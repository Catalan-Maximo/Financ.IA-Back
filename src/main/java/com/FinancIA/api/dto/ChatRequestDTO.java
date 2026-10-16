package com.FinancIA.api.dto;

import lombok.Data;

import java.util.List;

/**
 * Conversación del chatbot: la historia completa de mensajes
 * (el frontend la mantiene y la reenvía en cada turno).
 */
@Data
public class ChatRequestDTO {

    /** Constructor explícito: evita el constructor vacío generado por Lombok (corrupto en javac incremental). */
    public ChatRequestDTO() {}

    private List<Message> messages;

    /** Un mensaje de la conversación. */
    public record Message(String rol, String contenido) {}
}
