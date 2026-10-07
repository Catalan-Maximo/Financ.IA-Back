package com.FinancIA.api.service;

import com.FinancIA.api.dto.AccionCotizacion;
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

    private final RestClient restClient;

    public YahooFinanceClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder()
                .baseUrl("https://query1.finance.yahoo.com")
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                .requestFactory(factory)
                .build();
    }

    /**
     * Cierres diarios de los últimos 3 meses (para el modelo Monte Carlo).
     * Los valores null (días sin cotización) se filtran.
     */
    @SuppressWarnings("unchecked")
    public List<Double> velas3m(String ticker) {
        Map<String, Object> body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v8/finance/chart/{ticker}")
                        .queryParam("range", "3mo")
                        .queryParam("interval", "1d")
                        .build(ticker))
                .retrieve()
                .body(Map.class);

        Map<String, Object> chart = (Map<String, Object>) body.get("chart");
        Map<String, Object> resultado = (Map<String, Object>) ((java.util.List<Object>) chart.get("result"))
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Sin datos para " + ticker));

        Map<String, Object> indicators = (Map<String, Object>) resultado.get("indicators");
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
    public AccionCotizacion cotizacion(String ticker) {
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

        double precio = ((Number) meta.get("regularMarketPrice")).doubleValue();
        Object cierreAnterior = meta.get("chartPreviousClose");
        if (cierreAnterior == null) cierreAnterior = meta.get("previousClose");

        double variacion = 0;
        if (cierreAnterior != null && ((Number) cierreAnterior).doubleValue() > 0) {
            variacion = (precio / ((Number) cierreAnterior).doubleValue() - 1) * 100;
        }
        return new AccionCotizacion(ticker, redondear(precio), redondear(variacion));
    }

    private double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
