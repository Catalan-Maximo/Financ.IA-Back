package com.FinancIA.api.exception;

/**
 * Se lanza cuando un recurso pedido no existe (ej. un usuario
 * con un id inexistente). El {@link GlobalExceptionHandler}
 * lo traduce a HTTP 404 NOT FOUND.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
