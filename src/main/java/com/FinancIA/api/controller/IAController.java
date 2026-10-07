package com.FinancIA.api.controller;

import com.FinancIA.api.dto.ChatRequestDTO;
import com.FinancIA.api.dto.ChatResponseDTO;
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
 * POST /api/v1/ia/chat
 *   Chat de dudas: recibe la historia de mensajes y responde
 *   con los datos reales de la app como contexto.
 *
 * Ejemplo de body del chat:
 * { "mensajes": [ { "rol": "user", "contenido": "¿Qué es el dólar MEP?" } ] }
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

    @PostMapping("/chat")
    public ChatResponseDTO chat(@RequestBody ChatRequestDTO request) {
        return iaService.responderChat(request);
    }
}
