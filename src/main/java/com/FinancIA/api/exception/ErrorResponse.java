package com.FinancIA.api.exception;

import java.time.LocalDateTime;

/**
 * Cuerpo JSON estructurado para todas las respuestas de error.
 * Reemplaza los stack traces genéricos de Spring por un formato
 * consistente que el frontend puede interpretar fácilmente.
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {}
