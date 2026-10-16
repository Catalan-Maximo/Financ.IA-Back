package com.FinancIA.api.service;

import com.FinancIA.api.dto.StockQuote;
import com.FinancIA.api.dto.DollarQuote;
import com.FinancIA.api.dto.FundamentalsDTO;
import com.FinancIA.api.dto.PackQuote;
import com.FinancIA.api.domain.PackComposition;
import com.FinancIA.api.repository.PackCompositionRepository;
import com.FinancIA.api.repository.InvestmentPackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lógica de mercados: acciones con cotización real de Yahoo Finance,
 * cotizaciones del dólar (DolarAPI) y packs con variación ponderada
 * según su composición en la BD.
 */
@Service
@Slf4j
public class MarketService {

    /** Tickers que mostramos en la sección Acciones. */
    public static final List<String> TICKERS = List.of("AAPL", "MSFT", "SPY", "YPF", "GGAL", "MELI");

    private final YahooFinanceClient yahooClient;
    private final DollarApiClient dollarApiClient;
    private final BinanceClient binanceClient;
    private final MonteCarloService monteCarloService;
    private final InvestmentPackRepository packRepository;
    private final PackCompositionRepository compositionRepository;

    public MarketService(YahooFinanceClient yahooClient,
                          DollarApiClient dollarApiClient,
                          BinanceClient binanceClient,
                          MonteCarloService monteCarloService,
                          InvestmentPackRepository packRepository,
                          PackCompositionRepository compositionRepository) {
        this.yahooClient = yahooClient;
        this.dollarApiClient = dollarApiClient;
        this.binanceClient = binanceClient;
        this.monteCarloService = monteCarloService;
        this.packRepository = packRepository;
        this.compositionRepository = compositionRepository;
    }

    /** Cache en memoria de valoraciones por P/E (TTL 6h, cambian poco). */
    private final java.util.concurrent.ConcurrentHashMap<String, ValuationCache> cacheValuation =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record ValuationCache(java.time.Instant savedAt, FundamentalsDTO fundamentals) {}

    /** Cotizaciones de las acciones/ETFs con variación de 24h y valoración por P/E. */
    public List<StockQuote> stocks() {
        return TICKERS.stream()
                .map(ticker -> {
                    StockQuote base = safeQuote(ticker);
                    return new StockQuote(
                            base.ticker(), base.price(), base.variation24h(),
                            cachedValuation(ticker));
                })
                .toList();
    }

    /** Valoración por P/E con cache: evita pegarle a Yahoo por cada request de la lista. */
    private String cachedValuation(String ticker) {
        try {
            ValuationCache cache = cacheValuation.get(ticker);
            if (cache != null && cache.savedAt().isAfter(java.time.Instant.now().minus(6, java.time.temporal.ChronoUnit.HOURS))) {
                return cache.fundamentals().priceValuation();
            }
            FundamentalsDTO f = fundamentals(ticker);
            cacheValuation.put(ticker, new ValuationCache(java.time.Instant.now(), f));
            return f.priceValuation();
        } catch (Exception e) {
            log.warn("Sin valoración para {}: {}", ticker, e.getMessage());
            return "n/d";
        }
    }

    /** Cotizaciones del dólar (DolarAPI). */
    public List<DollarQuote> dollar() {
        return dollarApiClient.quotes();
    }

    /**
     * Activos proyectados para el simulador con Monte Carlo: acciones/ETFs
     * (Yahoo) y cripto (Binance) usando su volatilidad histórica real.
     * Devuelve el mismo shape que ActivoService más dos claves extra:
     * "peor" y "mejor" = percentiles 5/95 del retorno total del horizonte.
     */
    public List<Map<String, Object>> projectedAssets(int months) {
        List<Map<String, Object>> assets = new java.util.ArrayList<>();

        for (String ticker : TICKERS) {
            try {
                assets.add(project(ticker, "Acción / ETF", yahooCloses(ticker), months));
            } catch (Exception e) {
                log.warn("No se pudo proyectar {}: {}", ticker, e.getMessage());
            }
        }

        for (String par : BinanceClient.PAIRS) {
            try {
                List<Double> closes = binanceClient.dailyCandles(par, 90).stream()
                        .map(BinanceClient.Candle::close)
                        .toList();
                String name = par.equals("BTCUSDT") ? "Bitcoin" : "Ethereum";
                assets.add(project(name, "Cripto", closes, months));
            } catch (Exception e) {
                log.warn("No se pudo proyectar {}: {}", par, e.getMessage());
            }
        }

        return assets;
    }

    /** Cierres de 3 meses de Yahoo para un ticker. */
    private List<Double> yahooCloses(String ticker) {
        return yahooClient.candles3m(ticker);
    }

    /** Cierres diarios con fecha (para el gráfico de una acción). */
    public List<Map<String, Object>> stockCandles(String ticker) {
        return yahooClient.candlesWithDate(ticker, 90);
    }

    /** Noticias financieras destacadas (Yahoo). */
    public List<Map<String, Object>> news() {
        return yahooClient.news(10);
    }

    /**
     * Probabilidad de que el activo A supere a B (Monte Carlo, escenarios
     * emparejados). Null si alguno de los dos no es proyectable (renta fija).
     */
    public Double probabilityOfOutperforming(String assetA, String assetB, int meses) {
        List<Double> closesA = closesOf(assetA);
        List<Double> closesB = closesOf(assetB);
        if (closesA.size() < 2 || closesB.size() < 2) return null;
        return monteCarloService.probabilityOfOutperforming(closesA, closesB, meses);
    }

    /**
     * Fundamentales de una acción (P/E, ingresos, margen, deuda) con la
     * valoración simple: P/E < 20 barata, 20-30 razonable, > 30 cara.
     */
    @SuppressWarnings("unchecked")
    public FundamentalsDTO fundamentals(String ticker) {
        Map<String, Object> raw = yahooClient.rawFundamentals(ticker);

        Map<String, Object> fd = (Map<String, Object>) raw.get("financialData");
        Map<String, Object> ks = (Map<String, Object>) raw.get("defaultKeyStatistics");

        Double pe = rawDouble(ks != null ? ks.get("forwardPE") : null);
        if (pe == null && fd != null) pe = rawDouble(fd.get("trailingPE"));

        String valuation = pe == null ? "n/d"
                : pe < 20 ? "Barata"
                : pe <= 30 ? "Razonable"
                : "Cara";

        Double debt = rawDouble(fd != null ? fd.get("totalDebt") : null);
        Double cash = rawDouble(fd != null ? fd.get("totalCash") : null);

        List<FundamentalsDTO.AnnualIncome> annual = new java.util.ArrayList<>();
        Map<String, Object> hist = (Map<String, Object>) raw.get("incomeStatementHistory");
        if (hist != null) {
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) hist.get("incomeStatementHistory");
            if (items != null) {
                // La API devuelve del año más nuevo al más viejo: tomamos los últimos 4 y los damos en orden
                int since = Math.max(0, items.size() - 4);
                for (int i = since; i < items.size(); i++) {
                    Map<String, Object> item = items.get(i);
                    String year = String.valueOf(
                            ((Map<String, Object>) item.get("endDate")).get("fmt"));
                    annual.add(new FundamentalsDTO.AnnualIncome(
                            year.substring(0, 4),
                            rawDouble(item.get("totalRevenue")) == null ? 0 : rawDouble(item.get("totalRevenue")),
                            rawDouble(item.get("netIncome")) == null ? 0 : rawDouble(item.get("netIncome"))
                    ));
                }
            }
        }

        Double growth = rawDouble(fd != null ? fd.get("revenueGrowth") : null);
        Double margin = rawDouble(fd != null ? fd.get("profitMargins") : null);

        return new FundamentalsDTO(
                ticker,
                pe,
                valuation,
                growth == null ? null : growth * 100,
                margin == null ? null : margin * 100,
                debt,
                cash,
                debt != null && cash != null && debt > cash,
                annual
        );
    }

    /** Resumen de fundamentales por ticker para el contexto del chat. */
    public List<String> fundamentalsSummary() {
        List<String> lines = new java.util.ArrayList<>();
        for (String ticker : TICKERS) {
            try {
                FundamentalsDTO f = fundamentals(ticker);
                lines.add(String.format(
                        "%s: P/E %s (%s), ingresos %+.1f%% anual, margen neto %s",
                        ticker, f.pe() == null ? "n/d" : String.format("%.1f", f.pe()),
                        f.priceValuation(),
                        f.revenueGrowthPct() == null ? 0 : f.revenueGrowthPct(),
                        f.netMarginPct() == null ? "n/d" : String.format("%.1f%%", f.netMarginPct())));
            } catch (Exception e) {
                log.warn("Sin fundamentales para {}: {}", ticker, e.getMessage());
            }
        }
        return lines;
    }

    /** Lee el "raw" de un nodo de Yahoo ({"raw": 123, "fmt": "123"}). */
    private Double rawDouble(Object nodo) {
        if (!(nodo instanceof Map<?, ?> m)) return null;
        Object raw = m.get("raw");
        return raw instanceof Number n ? n.doubleValue() : null;
    }

    /** Precio actual de un activo (acciones/ETF y cripto); null para renta fija. */
    public Double currentPrice(String asset) {
        if (TICKERS.contains(asset)) {
            try {
                return yahooClient.quote(asset).price();
            } catch (Exception e) {
                log.warn("Sin precio para {}: {}", asset, e.getMessage());
                return null;
            }
        }
        String par = asset.equalsIgnoreCase("Bitcoin") ? "BTCUSDT"
                : asset.equalsIgnoreCase("Ethereum") ? "ETHUSDT" : null;
        if (par != null) {
            try {
                List<Double> closes = binanceClient.dailyCandles(par, 2).stream()
                        .map(BinanceClient.Candle::close)
                        .toList();
                return closes.isEmpty() ? null : closes.get(closes.size() - 1);
            } catch (Exception e) {
                log.warn("Sin precio para {}: {}", asset, e.getMessage());
                return null;
            }
        }
        return null;
    }

    /** Cierres de 3 meses para un ticker de Yahoo o un par cripto (por nombre). */
    public List<Double> closesOf(String entity) {
        if (TICKERS.contains(entity)) {
            try {
                return yahooClient.candles3m(entity);
            } catch (Exception e) {
                log.warn("Sin cierres para {}: {}", entity, e.getMessage());
                return List.of();
            }
        }
        String par = entity.equalsIgnoreCase("Bitcoin") ? "BTCUSDT"
                : entity.equalsIgnoreCase("Ethereum") ? "ETHUSDT" : null;
        if (par != null) {
            try {
                return binanceClient.dailyCandles(par, 90).stream()
                        .map(BinanceClient.Candle::close)
                        .toList();
            } catch (Exception e) {
                log.warn("Sin cierres para {}: {}", entity, e.getMessage());
                return List.of();
            }
        }
        return List.of(); // renta fija: no proyectable
    }

    /** Corre Monte Carlo y arma el mapa con el shape del simulador. */
    private Map<String, Object> project(String entity, String type,
                                          List<Double> closes, int months) {
        if (closes.size() < 2) return Map.of("entity", entity, "tna", 0.0, "type", type);

        MonteCarloService.Projection p = monteCarloService.simulate(closes, months);
        // TNA equivalente a la mediana mensual proyectada (para las fórmulas del simulador)
        double tna = (Math.pow(1 + p.medianaMensualPct() / 100.0, 365.0 / 30.0) - 1) * 100;

        return Map.of(
                "entity", entity,
                "tna", round2(tna),
                "type", type,
                "worst", round2(p.p5TotalPct()),
                "best", round2(p.p95TotalPct()),
                "risk", riskByVolatility(monteCarloService.monthlyVolatilityPct(closes))
        );
    }

    /** Buckets de riesgo según volatilidad mensual histórica (%). */
    public static String riskByVolatility(double volMensualPct) {
        if (volMensualPct < 3) return "Bajo";
        if (volMensualPct < 8) return "Medio";
        if (volMensualPct < 20) return "Alto";
        return "Extremo";
    }

    /**
     * Packs con variación ponderada: la variación de cada ticker
     * (una sola consulta por ticker, cacheada) multiplicada por su peso.
     */
    public List<PackQuote> packs() {
        Map<String, Double> variaciones = new HashMap<>();

        return packRepository.findAll().stream().map(pack -> {
            List<PackComposition> composition = compositionRepository.findByPackId(pack.getId());

            double variation = 0;
            for (PackComposition item : composition) {
                double tickerVariation = variaciones.computeIfAbsent(item.getTicker(), this::safeVariation);
                variation += tickerVariation * item.getWeightPercentage() / 100.0;
            }

            return new PackQuote(
                    pack.getId(),
                    pack.getName(),
                    pack.getRisk(),
                    round2(variation),
                    composition.stream()
                            .map(c -> new PackQuote.CompositionItem(c.getTicker(), c.getWeightPercentage()))
                            .toList()
            );
        }).toList();
    }

    /** Consulta Yahoo sin romper la lista si un ticker falla. */
    private StockQuote safeQuote(String ticker) {
        try {
            return yahooClient.quote(ticker);
        } catch (Exception e) {
            log.warn("No se pudo cotizar {}: {}", ticker, e.getMessage());
            return new StockQuote(ticker, 0, 0, "n/d");
        }
    }

    private double safeVariation(String ticker) {
        return safeQuote(ticker).variation24h();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
