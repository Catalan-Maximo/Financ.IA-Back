package com.FinancIA.api.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Snapshot diario del análisis técnico de un par cripto.
 * No se guardan las 210 velas crudas — solo los indicadores
 * calculados (mismo patrón que TasaMercado).
 */
@Entity
@Table(name = "cripto_estado")
@Data
public class CriptoEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Par analizado (BTCUSDT / ETHUSDT). */
    private String simbolo;

    /** Fecha del snapshot. */
    private LocalDate fecha;

    /** Precio de cierre actual. */
    private double precio;

    private Double sma50;
    private Double sma200;
    private Double rsi14;

    private double soporte20;
    private double resistencia20;

    /** Variación % de los últimos 3 días. */
    private Double cambio3d;

    /** Cuántas de las 4 reglas pasaron. */
    private int checksPasados;

    /** BUY / WATCH / AVOID. */
    private String veredicto;

    /** Fuente de los datos ("Binance"). */
    private String fuente;
}
