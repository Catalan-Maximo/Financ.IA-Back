package com.FinancIA.api.dto;

/**
 * Respuesta de registro/login: el token JWT, el email y el
 * perfil inversor del usuario (null si todavía no hizo el test).
 */
public record AuthResponseDTO(String token, String email, String perfilInversor) {}
