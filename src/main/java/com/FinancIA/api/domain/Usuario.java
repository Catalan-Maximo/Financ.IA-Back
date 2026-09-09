package com.FinancIA.api.domain;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "usuarios")
@Data
public class Usuario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String email;

    /** Contraseña hasheada con BCrypt (null en usuarios creados antes del auth). */
    private String password;

    /** Token de push de Expo (null si el usuario no habilitó notificaciones). */
    private String pushToken;

    /** Suma total de puntos del test (8 a 49). */
    private Integer puntaje;

    private String perfilInversor; // Conservador, Moderado, Agresivo
}
