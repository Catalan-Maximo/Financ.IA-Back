package com.FinancIA.api.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Un gasto del mes del usuario, categorizado para el dashboard.
 */
@Entity
@Table(name = "expenses")
@Data
public class Expense {

    /** Constructor explícito: evita que Lombok genere uno (corrupto en javac incremental). */
    public Expense() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    /** Categoría: Comida, Transporte, Vivienda, Servicios, Entretenimiento, Otros. */
    private String category;

    /** Descripción opcional (ej. "Supermercado"). */
    private String description;

    private double amount;

    private LocalDate date;
}
