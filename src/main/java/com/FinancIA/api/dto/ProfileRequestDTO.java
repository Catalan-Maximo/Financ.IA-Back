package com.FinancIA.api.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * DTO de entrada para el registro del perfil inversor.
 * El frontend envía la suma total de puntos del cuestionario
 * (de 8 a 49) y el backend clasifica el perfil.
 */
@Data
public class ProfileRequestDTO {

    /** Constructor explícito: evita el constructor vacío generado por Lombok (corrupto en javac incremental). */
    public ProfileRequestDTO() {}

    /** Nombre del usuario. */
    private String name;

    /** Email del usuario. */
    private String email;

    /** Suma total de puntos del test de 10 preguntas (8 a 49). */
    @Min(0)
    private int score;
}
