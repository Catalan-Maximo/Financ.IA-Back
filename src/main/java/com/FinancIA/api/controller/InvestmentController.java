package com.FinancIA.api.controller;

import jakarta.validation.Valid;
import com.FinancIA.api.dto.ComparisonRequest;
import com.FinancIA.api.dto.ComparisonResponse;
import com.FinancIA.api.repository.MarketRateRepository;
import com.FinancIA.api.service.AssetService;
import com.FinancIA.api.service.MarketDataJob;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/assets")
public class InvestmentController {

    private final AssetService assetService;
    private final MarketRateRepository rateRepository;

    public InvestmentController(AssetService assetService, MarketRateRepository rateRepository) {
        this.assetService = assetService;
        this.rateRepository = rateRepository;
    }

    @GetMapping("/rates")
    public List<Map<String, Object>> getWalletRates() {
        return assetService.getAssets();
    }

    /**
     * GET /api/v1/activos/inflacion — último IPC oficial guardado por el job
     * del BCRA, para precargar el simulador con la inflación real.
     */
    @GetMapping("/inflation")
    public Map<String, Object> officialInflation() {
        return rateRepository.findTop1ByAssetTypeOrderByDateDesc(MarketDataJob.MONTHLY_INFLATION)
                .map(t -> Map.<String, Object>of("value", t.getValue(), "date", t.getDate().toString()))
                .orElse(Map.of("value", 0.0, "date", ""));
    }

    /**
     * GET /api/v1/activos/historial/{entidad} — últimos 30 días de la tasa
     * de una entidad (ej. "Mercado Pago"), para el gráfico de tendencia.
     */
    @GetMapping("/history/{entity}")
    public List<Map<String, Object>> entityHistory(@PathVariable String entity) {
        return rateRepository.findTop30ByEntityOrderByDateAsc(entity).stream()
                .map(t -> Map.<String, Object>of("date", t.getDate().toString(), "value", t.getValue()))
                .toList();
    }

    /**
     * POST /api/v1/activos/comparar
     *
     * Recibe monto, plazo e inflación mensual estimada y devuelve
     * el rendimiento real calculado para cada activo del mercado.
     *
     * Ejemplo de body:
     * {
     *   "monto": 100000,
     *   "plazoMeses": 6,
     *   "inflacionMensual": 4.0
     * }
     */
    @PostMapping("/compare")
    public ComparisonResponse compareAssets(@RequestBody @Valid ComparisonRequest request) {
        return assetService.compareAssets(request);
    }
}
