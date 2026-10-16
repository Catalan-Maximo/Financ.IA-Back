package com.FinancIA.api.domain;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Un pack de inversión pre-armado (composición de acciones/ETFs).
 * La composición vive en PackComposicion para que no esté hardcodeada.
 */
@Entity
@Table(name = "investment_packs")
@Data
public class InvestmentPack {

    /** Constructor explícito: evita que Lombok genere uno (corrupto en javac incremental). */
    public InvestmentPack() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre visible (ej. "Argentina Potenciada"). */
    private String name;

    /** Nivel de riesgo: Bajo / Moderado / Agresivo. */
    private String risk;
}
