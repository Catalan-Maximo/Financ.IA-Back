package com.FinancIA.api.dto;

import lombok.Data;

/**
 * DTO de entrada para el registro del perfil inversor.
 * El frontend envía la suma total de puntos del cuestionario
 * (de 8 a 49) y el backend clasifica el perfil.
 */
@Data
public class PerfilRequestDTO {

    /** Nombre del usuario. */
    private String nombre;

    /** Email del usuario. */
    private String email;

    /** Suma total de puntos del test de 10 preguntas (8 a 49). */
    private int puntaje;
}
