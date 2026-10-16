package com.FinancIA.api.service;

import com.FinancIA.api.dto.StockQuote;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cliente de la API pública de Yahoo Finance (sin autenticación).
 *
 * GET /v8/finance/chart/{ticker}?range=2d&interval=1d
 * Devuelve el precio actual y el cierre de la sesión anterior;
 * con eso se calcula la variación de 24h.
 *
 * Nota: Yahoo requiere un header User-Agent realista, si no responde 429.
 */
@Service
public class YahooFinanceClient {

    private static final String UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";

    private final RestClient restClient;

    public YahooFinanceClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder()
                .baseUrl("https://query1.finance.yahoo.com")
                .defaultHeader("User-Agent", UA)
                .requestFactory(factory)
                .build();
    }

    /**
     * Cierres diarios de los últimos 3 meses (para el modelo Monte Carlo).
     * Los valores null (días sin cotización) se filtran.
     */
    @SuppressWarnings("unchecked")
    public List<Double> candles3m(String ticker) {
        Map<String, Object> body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v8/finance/chart/{ticker}")
                        .queryParam("range", "3mo")
                        .queryParam("interval", "1d")
                        .build(ticker))
                .retrieve()
                .body(Map.class);

        Map<String, Object> chart = (Map<String, Object>) body.get("chart");
        Map<String, Object> result = (Map<String, Object>) ((java.util.List<Object>) chart.get("result"))
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Sin datos para " + ticker));

        Map<String, Object> indicators = (Map<String, Object>) result.get("indicators");
        Map<String, Object> quote = (Map<String, Object>) ((java.util.List<Object>) indicators.get("quote"))
                .stream().findFirst().orElse(Map.of());
        List<Object> closes = (List<Object>) quote.get("close");

        return closes.stream()
                .filter(java.util.Objects::nonNull)
                .map(c -> ((Number) c).doubleValue())
                .toList();
    }

    /**
     * Cotización actual de un ticker con su variación de 24h en %.
     * Si Yahoo no trae el cierre anterior, la variación queda en 0.
     */
    @SuppressWarnings("unchecked")
    public StockQuote quote(String ticker) {
        Map<String, Object> body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v8/finance/chart/{ticker}")
                        .queryParam("range", "2d")
                        .queryParam("interval", "1d")
                        .build(ticker))
                .retrieve()
                .body(Map.class);

        Map<String, Object> chart = (Map<String, Object>) body.get("chart");
        Map<String, Object> meta = (Map<String, Object>) ((java.util.List<Object>) chart.get("result"))
                .stream().findFirst()
                .map(r -> (Map<String, Object>) ((Map<String, Object>) r).get("meta"))
                .orElseThrow(() -> new IllegalStateException("Sin datos para " + ticker));

        double price = ((Number) meta.get("regularMarketPrice")).doubleValue();
        Object previousClose = meta.get("chartPreviousClose");
        if (previousClose == null) previousClose = meta.get("previousClose");

        double variation = 0;
        if (previousClose != null && ((Number) previousClose).doubleValue() > 0) {
            variation = (price / ((Number) previousClose).doubleValue() - 1) * 100;
        }
        return new StockQuote(ticker, round2(price), round2(variation), "n/d");
    }

    // ─── Fundamantales (crumb flow) ─────────────────────────────

    /** Cookie de sesión para el flujo anti-bot de Yahoo (quoteSummary). */
    private String cookie;
    private String crumb;

    /** Obtiene (y cachea) el crumb: cookie de fc.yahoo.com → getcrumb. */
    private synchronized void ensureCrumb() {
        if (crumb != null) return;

        // 1. Cookie de sesión (fc.yahoo.com la setea)
        String setCookie = restClient.get()
                .uri("https://fc.yahoo.com")
                .exchange((request, response) ->
                        (String) response.getHeaders().getFirst(
                                org.springframework.http.HttpHeaders.SET_COOKIE));

        cookie = setCookie != null && setCookie.contains(";")
                ? setCookie.substring(0, setCookie.indexOf(';'))
                : setCookie;

        // 2. Crumb con la cookie
        crumb = restClient.get()
                .uri("https://query1.finance.yahoo.com/v1/test/getcrumb")
                .header("Cookie", cookie == null ? "" : cookie)
                .retrieve()
                .body(String.class);
    }

    /**
     * Datos fundamentales de un ticker (P/E, ingresos, margen, deuda).
     * Usa el endpoint quoteSummary con crumb; reintenta una vez si Yahoo
     * invalida la sesión.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> rawFundamentals(String ticker) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                ensureCrumb();
                Map<String, Object> body = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/v10/finance/quoteSummary/{ticker}")
                                .queryParam("modules", "financialData,defaultKeyStatistics,incomeStatementHistory")
                                .queryParam("crumb", crumb == null ? "" : crumb)
                                .build(ticker))
                        .header("Cookie", cookie == null ? "" : cookie)
                        .retrieve()
                        .body(Map.class);

                Object finance = body.get("finance");
                if (finance instanceof Map<?, ?> f && f.get("error") != null) {
                    throw new IllegalStateException("Yahoo rechazó la sesión: " + f.get("error"));
                }
                Map<String, Object> result = (Map<String, Object>)
                        ((List<Object>) ((Map<String, Object>) body.get("quoteSummary")).get("result"))
                                .stream().findFirst()
                                .orElseThrow(() -> new IllegalStateException("Sin datos para " + ticker));
                return result;
            } catch (Exception e) {
                crumb = null; // sesión invalidada: regenerar y reintentar
                if (attempt == 1) throw e;
            }
        }
        throw new IllegalStateException("No se pudo consultar Yahoo para " + ticker);
    }

    /**
     * Noticias financieras del buscador de Yahoo (títulos + medio + link).
     * Consulta varias palabras clave y deduplica por título.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> news(int quantity) {
        List<Map<String, Object>> all = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        for (String query : List.of("markets", "inflation", "stocks")) {
            try {
                Map<String, Object> body = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/v1/finance/search")
                                .queryParam("q", query)
                                .queryParam("newsCount", Math.max(quantity, 5))
                                .build())
                        .retrieve()
                        .body(Map.class);

                List<Map<String, Object>> news = (List<Map<String, Object>>) body.get("news");
                if (news == null) continue;

                for (Map<String, Object> n : news) {
                    String title = (String) n.get("title");
                    if (title == null || !seen.add(title)) continue;
                    String link = (String) n.get("link");
                    all.add(Map.of(
                            "title", title,
                            "outlet", n.get("publisher") != null ? n.get("publisher") : "",
                            "link", link != null ? link : ""
                    ));
                }
            } catch (Exception e) {
                // Una query fallida no rompe el resto
            }
        }
        return all.stream().limit(quantity).toList();
    }

    /** Cierres diarios con fecha (para gráficos de acciones). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> candlesWithDate(String ticker, int days) {
        Map<String, Object> body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v8/finance/chart/{ticker}")
                        .queryParam("range", "3mo")
                        .queryParam("interval", "1d")
                        .build(ticker))
                .retrieve()
                .body(Map.class);

        Map<String, Object> chart = (Map<String, Object>) body.get("chart");
        Map<String, Object> result = (Map<String, Object>) ((List<Object>) chart.get("result"))
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Sin datos para " + ticker));

        List<Object> timestamps = (List<Object>) result.get("timestamp");
        Map<String, Object> quote = (Map<String, Object>) ((List<Object>)
                ((Map<String, Object>) result.get("indicators")).get("quote"))
                .stream().findFirst().orElse(Map.of());
        List<Object> closes = (List<Object>) quote.get("close");

        List<Map<String, Object>> points = new java.util.ArrayList<>();
        for (int i = 0; i < timestamps.size() && i < closes.size(); i++) {
            if (closes.get(i) == null) continue;
            long epoch = ((Number) timestamps.get(i)).longValue();
            String date = java.time.Instant.ofEpochSecond(epoch)
                    .atZone(java.time.ZoneId.of("UTC")).toLocalDate().toString();
            points.add(Map.of("date", date,
                    "value", ((Number) closes.get(i)).doubleValue()));
        }
        return points;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
