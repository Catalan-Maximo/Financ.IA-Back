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
@Table(name = "tasas_mercado")
@Data
public class TasaMercado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Entidad fuente de los datos (ej. "BCRA"). */
    private String entidad;

    /** Tipo de activo / indicador. */
    private String tipoActivo;

    /** Valor numérico (unidades según tipoActivo, ver Javadoc de la clase). */
    private double valor;

    /** Fuente específica (ej. "BCRA BADLAR", "BCRA tipo de cambio minorista"). */
    private String fuente;

    /** Fecha de publicación del dato. */
    private LocalDate fecha;
}
