package com.FinancIA.api.controller;

import com.FinancIA.api.dto.IARequestDTO;
import com.FinancIA.api.dto.IAResponseDTO;
import com.FinancIA.api.service.IAService;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para los endpoints de IA / recomendaciones inteligentes.
 *
 * POST /api/v1/ia/simular
 *   Recibe perfil, monto, plazo e inflación y devuelve
 *   una recomendación personalizada con distribución de portafolio.
 *
 * Ejemplo de body:
 * {
 *   "perfilInversor": "Moderado",
 *   "monto": 100000,
 *   "plazoMeses": 6,
 *   "inflacionMensual": 4.0,
 *   "activoA": "Plazo Fijo",
 *   "activoB": "Dólar"
 * }
 */
@RestController
@RequestMapping("/api/v1/ia")
public class IAController {

    private final IAService iaService;

    public IAController(IAService iaService) {
        this.iaService = iaService;
    }

    @PostMapping("/simular")
    public IAResponseDTO simularConIA(@RequestBody IARequestDTO request) {
        return iaService.generarRecomendacion(request);
    }
}
