package com.FinancIA.api.controller;

import com.FinancIA.api.dto.ComparacionRequest;
import com.FinancIA.api.dto.ComparacionResponse;
import com.FinancIA.api.service.ActivoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class InversionController {

    private final ActivoService activoService;

    public InversionController(ActivoService activoService) {
        this.activoService = activoService;
    }

    @GetMapping("/activos/tasas")
    public List<Map<String, Object>> obtenerTasasBilleteras() {
        return activoService.getActivos();
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
