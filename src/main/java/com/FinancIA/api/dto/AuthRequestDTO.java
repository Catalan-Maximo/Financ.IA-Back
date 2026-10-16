package com.FinancIA.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Credenciales de login. */
@Data
public class AuthRequestDTO {

    /** Constructor explícito: evita el constructor vacío generado por Lombok (corrupto en javac incremental). */
    public AuthRequestDTO() {}

    @NotBlank
    private String email;
    @NotBlank
    private String password;
}
