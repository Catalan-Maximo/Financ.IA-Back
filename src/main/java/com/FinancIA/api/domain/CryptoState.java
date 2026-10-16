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
@Table(name = "crypto_states")
@Data
public class CryptoState {

    /** Constructor explícito: evita que Lombok genere uno (corrupto en javac incremental). */
    public CryptoState() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Par analizado (BTCUSDT / ETHUSDT). */
    private String symbol;

    /** Fecha del snapshot. */
    private LocalDate date;

    /** Precio de cierre actual. */
    private double price;

    private Double sma50;
    private Double sma200;
    private Double rsi14;

    private double support20;
    private double resistance20;

    /** Variación % de los últimos 3 días. */
    private Double change3d;

    /** Cuántas de las 4 reglas pasaron. */
    private int checksPassed;

    /** BUY / WATCH / AVOID. */
    private String verdict;

    /** Fuente de los datos ("Binance"). */
    private String source;
}
