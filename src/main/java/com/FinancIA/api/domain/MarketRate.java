package com.FinancIA.api.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Registro de una tasa / valor de mercado persistido por el job programado.
 *
 * Unidades del campo valor según tipoActivo:
 * - "Plazo Fijo Tradicional" → tasa nominal anual en % (ej. 23.31)
 * - "Dólar Oficial"           → precio en pesos (ej. 1515.02)
 * - "Inflación Mensual"       → variación mensual en % (ej. 2.1)
 */
@Entity
@Table(name = "market_rates")
@Data
public class MarketRate {

    /** Constructor explícito: evita que Lombok genere uno (corrupto en javac incremental). */
    public MarketRate() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Entidad fuente de los datos (ej. "BCRA"). */
    private String entity;

    /** Tipo de activo / indicador. */
    private String assetType;

    /** Valor numérico (unidades según tipoActivo, ver Javadoc de la clase). */
    private double value;

    /** Fuente específica (ej. "BCRA BADLAR", "BCRA tipo de cambio minorista"). */
    private String source;

    /** Fecha de publicación del dato. */
    private LocalDate date;
}
