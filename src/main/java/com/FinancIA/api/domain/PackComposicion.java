package com.FinancIA.api.domain;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Un activo dentro de un pack: ticker + peso porcentual.
 * (ej. YPF con 40% dentro de "Argentina Potenciada")
 */
@Entity
@Table(name = "pack_composicion")
@Data
public class PackComposicion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Pack al que pertenece. */
    private Long packId;

    /** Ticker de Yahoo Finance (ej. YPF, AAPL, SPY). */
    private String ticker;

    /** Peso del activo en el pack (0-100). */
    private int porcentajePeso;
}
