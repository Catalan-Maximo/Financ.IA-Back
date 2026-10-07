package com.FinancIA.api.domain;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Un pack de inversión pre-armado (composición de acciones/ETFs).
 * La composición vive en PackComposicion para que no esté hardcodeada.
 */
@Entity
@Table(name = "pack_inversion")
@Data
public class PackInversion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre visible (ej. "Argentina Potenciada"). */
    private String nombre;

    /** Nivel de riesgo: Bajo / Moderado / Agresivo. */
    private String riesgo;
}
