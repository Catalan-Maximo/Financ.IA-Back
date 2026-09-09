package com.FinancIA.api.dto;

import lombok.Data;

/** Credenciales de login. */
@Data
public class AuthRequestDTO {

    private String email;
    private String password;
}
