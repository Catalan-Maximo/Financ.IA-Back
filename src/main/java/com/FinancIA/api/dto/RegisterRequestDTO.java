package com.FinancIA.api.dto;

import lombok.Data;

/** Datos de registro de un usuario nuevo. */
@Data
public class RegisterRequestDTO {

    private String nombre;
    private String email;
    private String password;
}
