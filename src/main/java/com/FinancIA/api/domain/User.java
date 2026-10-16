package com.FinancIA.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {

    /** Constructor explícito: evita que Lombok genere uno (corrupto en javac incremental). */
    public User() {}
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;

    /** Contraseña hasheada con BCrypt (null en usuarios creados antes del auth). */
    @JsonIgnore // nunca se serializa en las respuestas JSON
    private String password;

    /** Token de push de Expo (null si el usuario no habilitó notificaciones). */
    private String pushToken;

    /** Ingreso mensual declarado (para el dashboard de gastos). */
    private Double monthlyIncome;

    /** Suma total de puntos del test (8 a 49). */
    private Integer score;

    private String investorProfile; // Conservador, Moderado, Agresivo
}
