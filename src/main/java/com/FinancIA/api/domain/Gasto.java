package com.FinancIA.api.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Un gasto del mes del usuario, categorizado para el dashboard.
 */
@Entity
@Table(name = "gasto")
@Data
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    /** Categoría: Comida, Transporte, Vivienda, Servicios, Entretenimiento, Otros. */
    private String categoria;

    /** Descripción opcional (ej. "Supermercado"). */
    private String descripcion;

    private double monto;

    private LocalDate fecha;
}
