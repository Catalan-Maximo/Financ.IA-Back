package com.FinancIA.api.dto;

import lombok.Data;

import java.util.List;

/**
 * Conversación del chatbot: la historia completa de mensajes
 * (el frontend la mantiene y la reenvía en cada turno).
 */
@Data
public class ChatRequestDTO {

    private List<Mensaje> mensajes;

    /** Un mensaje de la conversación. */
    public record Mensaje(String rol, String contenido) {}
}
