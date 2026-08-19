package com.FinancIA.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/**
 * Manejo centralizado de excepciones.
 *
 * Traduce las excepciones del dominio a respuestas JSON estructuradas
 * con su HTTP status correcto, en vez de devolver stack traces genéricos:
 *
 * - ResourceNotFoundException  → 404 NOT FOUND
 * - IllegalArgumentException   → 400 BAD REQUEST
 * - Cualquier otra excepción   → 500 INTERNAL SERVER ERROR
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> manejarNoEncontrado(
            ResourceNotFoundException e, HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> manejarArgumentoInvalido(
            IllegalArgumentException e, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorGenerico(
            Exception e, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), e);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno del servidor", request);
    }

    private ResponseEntity<ErrorResponse> construir(
            HttpStatus status, String mensaje, HttpServletRequest request) {
        ErrorResponse cuerpo = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                mensaje,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(cuerpo);
    }
}
