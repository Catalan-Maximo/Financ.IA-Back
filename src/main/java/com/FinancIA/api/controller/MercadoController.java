package com.FinancIA.api.controller;

import jakarta.validation.Valid;
import com.FinancIA.api.domain.CriptoEstado;
import com.FinancIA.api.dto.AccionCotizacion;
import com.FinancIA.api.dto.DolarCotizacion;
import com.FinancIA.api.dto.PackCotizacion;
import com.FinancIA.api.dto.VersusRequestDTO;
import com.FinancIA.api.dto.VersusResponseDTO;
import com.FinancIA.api.repository.CriptoEstadoRepository;
import com.FinancIA.api.service.ActivoService;
import com.FinancIA.api.service.BinanceClient;
import com.FinancIA.api.service.BcraClient;
import com.FinancIA.api.service.MercadoService;
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
@RequestMapping("/api/v1/mercados")
public class MercadoController {

    private final MercadoService mercadoService;
    private final CriptoEstadoRepository criptoRepository;
    private final ActivoService activoService;
    private final BcraClient bcraClient;
    private final BinanceClient binanceClient;

    public MercadoController(MercadoService mercadoService,
                             CriptoEstadoRepository criptoRepository,
                             ActivoService activoService,
                             BcraClient bcraClient,
                             BinanceClient binanceClient) {
        this.mercadoService = mercadoService;
        this.criptoRepository = criptoRepository;
        this.activoService = activoService;
        this.bcraClient = bcraClient;
        this.binanceClient = binanceClient;
    }

    @GetMapping("/acciones")
    public List<AccionCotizacion> acciones() {
        return mercadoService.acciones();
    }

    @GetMapping("/dolar")
    public List<DolarCotizacion> dolar() {
        return mercadoService.dolar();
    }

    @GetMapping("/packs")
    public List<PackCotizacion> packs() {
        return mercadoService.packs();
    }

    @GetMapping("/crypto")
    public List<CriptoEstado> crypto() {
        return BinanceClient.PARES.stream()
                .map(par -> criptoRepository.findTop1BySimboloOrderByFechaDesc(par))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    /** POST /mercados/versus — comparación cara a cara con probabilidad Monte Carlo. */
    @PostMapping("/versus")
    public VersusResponseDTO versus(@RequestBody @Valid VersusRequestDTO request) {
        return activoService.compararVersus(request);
    }

    /** GET /mercados/dolar-historial?dias=30 — evolución del dólar oficial (BCRA). */
    @GetMapping("/dolar-historial")
    public List<Map<String, Object>> dolarHistorial(@RequestParam(defaultValue = "30") int dias) {
        return bcraClient.historial(BcraClient.SERIE_DOLAR_MINORISTA, dias);
    }

    /** Nombres amigables de las criptos extra. */
    private static final java.util.Map<String, String> NOMBRES_CRIPTO = java.util.Map.of(
            "SOLUSDT", "Solana", "ADAUSDT", "Cardano", "XRPUSDT", "XRP",
            "DOGEUSDT", "Dogecoin", "BNBUSDT", "BNB", "LTCUSDT", "Litecoin");

    /**
     * GET /mercados/cripto-info — precio y variación 24h de las criptos
     * extra (solo informativo: el análisis y las recomendaciones siguen
     * siendo exclusivos de BTC y ETH).
     */
    @GetMapping("/cripto-info")
    public List<Map<String, Object>> criptoInfo() {
        List<Map<String, Object>> info = new java.util.ArrayList<>();
        for (String simbolo : BinanceClient.OTRAS_CRIPTOS) {
            try {
                List<Double> closes = binanceClient.velasDiarias(simbolo, 2).stream()
                        .map(BinanceClient.Candle::close)
                        .toList();
                if (closes.size() < 2) continue;
                double precio = closes.get(closes.size() - 1);
                double variacion = (precio / closes.get(0) - 1) * 100;
                info.add(Map.of(
                        "simbolo", simbolo,
                        "nombre", NOMBRES_CRIPTO.getOrDefault(simbolo, simbolo),
                        "precio", Math.round(precio * 100.0) / 100.0,
                        "variacion24h", Math.round(variacion * 100.0) / 100.0
                ));
            } catch (Exception e) {
                logCripto(simbolo, e);
            }
        }
        return info;
    }

    private void logCripto(String simbolo, Exception e) {
        // Binance no respondió para esta cripto: la omitimos de la lista
        org.slf4j.LoggerFactory.getLogger(MercadoController.class)
                .warn("Sin info para {}: {}", simbolo, e.getMessage());
    }

    /** GET /mercados/fundamentales/{ticker} — P/E, ingresos, margen y deuda (Yahoo). */
    @GetMapping("/fundamentales/{ticker}")
    public com.FinancIA.api.dto.FundamentalesDTO fundamentales(@PathVariable String ticker) {
        return mercadoService.fundamentales(ticker);
    }

    /** GET /mercados/acciones/{ticker}/velas — cierres diarios para el gráfico. */
    @GetMapping("/acciones/{ticker}/velas")
    public List<Map<String, Object>> velasAccion(@PathVariable String ticker) {
        return mercadoService.velasAccion(ticker);
    }

    /** GET /mercados/noticias — noticias financieras destacadas (Yahoo). */
    @GetMapping("/noticias")
    public List<Map<String, Object>> noticias() {
        return mercadoService.noticias();
    }
}
