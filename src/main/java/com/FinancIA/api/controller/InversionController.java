package com.FinancIA.api.controller;

import com.FinancIA.api.dto.ComparacionRequest;
import com.FinancIA.api.dto.ComparacionResponse;
import com.FinancIA.api.repository.TasaMercadoRepository;
import com.FinancIA.api.service.ActivoService;
import com.FinancIA.api.service.MercadoDataJob;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class InversionController {

    private final ActivoService activoService;
    private final TasaMercadoRepository tasaRepository;

    public InversionController(ActivoService activoService, TasaMercadoRepository tasaRepository) {
        this.activoService = activoService;
        this.tasaRepository = tasaRepository;
    }

    @GetMapping("/activos/tasas")
    public List<Map<String, Object>> obtenerTasasBilleteras() {
        return activoService.getActivos();
    }

    /**
     * GET /api/v1/activos/inflacion — último IPC oficial guardado por el job
     * del BCRA, para precargar el simulador con la inflación real.
     */
    @GetMapping("/activos/inflacion")
    public Map<String, Object> inflacionOficial() {
        return tasaRepository.findTop1ByTipoActivoOrderByFechaDesc(MercadoDataJob.INFLACION_MENSUAL)
                .map(t -> Map.<String, Object>of("valor", t.getValor(), "fecha", t.getFecha().toString()))
                .orElse(Map.of("valor", 0.0, "fecha", ""));
    }

    /**
     * GET /api/v1/activos/historial/{entidad} — últimos 30 días de la tasa
     * de una entidad (ej. "Mercado Pago"), para el gráfico de tendencia.
     */
    @GetMapping("/activos/historial/{entidad}")
    public List<Map<String, Object>> historialEntidad(@PathVariable String entidad) {
        return tasaRepository.findTop30ByEntidadOrderByFechaAsc(entidad).stream()
                .map(t -> Map.<String, Object>of("fecha", t.getFecha().toString(), "valor", t.getValor()))
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
    @PostMapping("/activos/comparar")
    public ComparacionResponse compararActivos(@RequestBody ComparacionRequest request) {
        return activoService.compararActivos(request);
    }
}
