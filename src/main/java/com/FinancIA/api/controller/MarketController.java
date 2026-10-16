package com.FinancIA.api.controller;

import jakarta.validation.Valid;
import com.FinancIA.api.domain.CryptoState;
import com.FinancIA.api.dto.StockQuote;
import com.FinancIA.api.dto.DollarQuote;
import com.FinancIA.api.dto.PackQuote;
import com.FinancIA.api.dto.VersusRequestDTO;
import com.FinancIA.api.dto.VersusResponseDTO;
import com.FinancIA.api.repository.CryptoStateRepository;
import com.FinancIA.api.service.AssetService;
import com.FinancIA.api.service.BinanceClient;
import com.FinancIA.api.service.BcraClient;
import com.FinancIA.api.service.MarketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Endpoints de la sección Mercados (protegidos por JWT):
 *
 * GET /api/v1/mercados/acciones — cotizaciones de Yahoo Finance
 * GET /api/v1/mercados/dolar    — cotizaciones de DolarAPI
 * GET /api/v1/mercados/packs    — packs con variación ponderada
 * GET /api/v1/mercados/crypto   — estado cripto (issue #18)
 */
@RestController
@RequestMapping("/api/v1/markets")
public class MarketController {

    private final MarketService marketService;
    private final CryptoStateRepository cryptoRepository;
    private final AssetService assetService;
    private final BcraClient bcraClient;
    private final BinanceClient binanceClient;

    public MarketController(MarketService marketService,
                             CryptoStateRepository cryptoRepository,
                             AssetService assetService,
                             BcraClient bcraClient,
                             BinanceClient binanceClient) {
        this.marketService = marketService;
        this.cryptoRepository = cryptoRepository;
        this.assetService = assetService;
        this.bcraClient = bcraClient;
        this.binanceClient = binanceClient;
    }

    @GetMapping("/stocks")
    public List<StockQuote> stocks() {
        return marketService.stocks();
    }

    @GetMapping("/dollar")
    public List<DollarQuote> dollar() {
        return marketService.dollar();
    }

    @GetMapping("/packs")
    public List<PackQuote> packs() {
        return marketService.packs();
    }

    @GetMapping("/crypto")
    public List<CryptoState> crypto() {
        return BinanceClient.PAIRS.stream()
                .map(par -> cryptoRepository.findTop1BySymbolOrderByDateDesc(par))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    /** POST /mercados/versus — comparación cara a cara con probabilidad Monte Carlo. */
    @PostMapping("/versus")
    public VersusResponseDTO versus(@RequestBody @Valid VersusRequestDTO request) {
        return assetService.compareVersus(request);
    }

    /** GET /mercados/dolar-historial?dias=30 — evolución del dólar oficial (BCRA). */
    @GetMapping("/dollar-history")
    public List<Map<String, Object>> dollarHistory(@RequestParam(defaultValue = "30") int days) {
        return bcraClient.history(BcraClient.SERIES_RETAIL_DOLLAR, days);
    }

    /** Nombres amigables de las criptos extra. */
    private static final java.util.Map<String, String> CRYPTO_NAMES = java.util.Map.of(
            "SOLUSDT", "Solana", "ADAUSDT", "Cardano", "XRPUSDT", "XRP",
            "DOGEUSDT", "Dogecoin", "BNBUSDT", "BNB", "LTCUSDT", "Litecoin");

    /**
     * GET /mercados/cripto-info — precio y variación 24h de las criptos
     * extra (solo informativo: el análisis y las recomendaciones siguen
     * siendo exclusivos de BTC y ETH).
     */
    @GetMapping("/crypto-info")
    public List<Map<String, Object>> cryptoInfo() {
        List<Map<String, Object>> info = new java.util.ArrayList<>();
        for (String symbol : BinanceClient.OTHER_CRYPTOS) {
            try {
                List<Double> closes = binanceClient.dailyCandles(symbol, 2).stream()
                        .map(BinanceClient.Candle::close)
                        .toList();
                if (closes.size() < 2) continue;
                double price = closes.get(closes.size() - 1);
                double variation = (price / closes.get(0) - 1) * 100;
                info.add(Map.of(
                        "symbol", symbol,
                        "name", CRYPTO_NAMES.getOrDefault(symbol, symbol),
                        "price", Math.round(price * 100.0) / 100.0,
                        "variation24h", Math.round(variation * 100.0) / 100.0
                ));
            } catch (Exception e) {
                logCrypto(symbol, e);
            }
        }
        return info;
    }

    private void logCrypto(String symbol, Exception e) {
        // Binance no respondió para esta cripto: la omitimos de la lista
        org.slf4j.LoggerFactory.getLogger(MarketController.class)
                .warn("Sin info para {}: {}", symbol, e.getMessage());
    }

    /** GET /mercados/fundamentales/{ticker} — P/E, ingresos, margen y deuda (Yahoo). */
    @GetMapping("/fundamentals/{ticker}")
    public com.FinancIA.api.dto.FundamentalsDTO fundamentals(@PathVariable String ticker) {
        return marketService.fundamentals(ticker);
    }

    /** GET /mercados/acciones/{ticker}/velas — cierres diarios para el gráfico. */
    @GetMapping("/stocks/{ticker}/candles")
    public List<Map<String, Object>> stockCandles(@PathVariable String ticker) {
        return marketService.stockCandles(ticker);
    }

    /** GET /mercados/noticias — noticias financieras destacadas (Yahoo). */
    @GetMapping("/news")
    public List<Map<String, Object>> news() {
        return marketService.news();
    }
}
