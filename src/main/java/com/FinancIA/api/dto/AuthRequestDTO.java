package com.FinancIA.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Credenciales de login. */
@Data
public class AuthRequestDTO {

    @NotBlank
    private String email;
    @NotBlank
    private String password;
}
