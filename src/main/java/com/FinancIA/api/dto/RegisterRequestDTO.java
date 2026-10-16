package com.FinancIA.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Datos de registro de un usuario nuevo. */
@Data
public class RegisterRequestDTO {

    /** Constructor explícito: evita el constructor vacío generado por Lombok (corrupto en javac incremental). */
    public RegisterRequestDTO() {}

    @NotBlank
    private String name;
    @NotBlank
    private String email;
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;
}
