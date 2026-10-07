package com.FinancIA.api.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Meta de ahorro del usuario: objetivo en pesos con fecha límite
 * y monto acumulado (se aporta manualmente).
 */
@Entity
@Table(name = "meta_ahorro")
@Data
public class MetaAhorro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    /** Nombre visible (ej. "Viaje a Bariloche"). */
    private String nombre;

    private double montoObjetivo;

    /** Cuánto lleva ahorrado hasta ahora. */
    private double montoActual;

    private LocalDate fechaLimite;
}
