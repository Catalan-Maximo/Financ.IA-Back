package com.FinancIA.api.controller;

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

    public MercadoController(MercadoService mercadoService,
                             CriptoEstadoRepository criptoRepository,
                             ActivoService activoService,
                             BcraClient bcraClient) {
        this.mercadoService = mercadoService;
        this.criptoRepository = criptoRepository;
        this.activoService = activoService;
        this.bcraClient = bcraClient;
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
    public VersusResponseDTO versus(@RequestBody VersusRequestDTO request) {
        return activoService.compararVersus(request);
    }

    /** GET /mercados/dolar-historial?dias=30 — evolución del dólar oficial (BCRA). */
    @GetMapping("/dolar-historial")
    public List<Map<String, Object>> dolarHistorial(@RequestParam(defaultValue = "30") int dias) {
        return bcraClient.historial(BcraClient.SERIE_DOLAR_MINORISTA, dias);
    }
}
