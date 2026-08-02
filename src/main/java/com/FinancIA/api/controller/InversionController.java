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
        return List.of(
            Map.of("entidad", "Mercado Pago", "tna", 35.0, "tipo", "Billetera Virtual"),
            Map.of("entidad", "Personal Pay", "tna", 37.5, "tipo", "Billetera Virtual"),
            Map.of("entidad", "Banco Nación", "tna", 39.0, "tipo", "Plazo Fijo Tradicional")
        );
    }

    @PostMapping("/ia/simular")
    public Map<String, String> simularInversion(@RequestBody Map<String, String> peticion) {
        String activoA = peticion.getOrDefault("activoA", "Plazo Fijo");
        String activoB = peticion.getOrDefault("activoB", "Dólar");
        
        String respuestaIA = "Analizando tu perfil... En base a la inflación proyectada, " +
                "comparar " + activoA + " contra " + activoB + " muestra que a corto plazo " +
                "te conviene mantener liquidez en pesos remunerados, pero diversificando un 30% a " + activoB + ".";
                
        return Map.of("consejoIA", respuestaIA);
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
