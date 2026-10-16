package com.FinancIA.api.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Meta de ahorro del usuario: objetivo en pesos con fecha límite
 * y monto acumulado (se aporta manualmente).
 */
@Entity
@Table(name = "savings_goals")
@Data
public class SavingsGoal {

    /** Constructor explícito: evita que Lombok genere uno (corrupto en javac incremental). */
    public SavingsGoal() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    /** Nombre visible (ej. "Viaje a Bariloche"). */
    private String name;

    private double targetAmount;

    /** Cuánto lleva ahorrado hasta ahora. */
    private double currentAmount;

    private LocalDate deadline;
}
