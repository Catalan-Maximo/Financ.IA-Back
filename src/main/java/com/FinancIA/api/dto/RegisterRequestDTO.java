package com.FinancIA.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Datos de registro de un usuario nuevo. */
@Data
public class RegisterRequestDTO {

    @NotBlank
    private String nombre;
    @NotBlank
    private String email;
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;
}
