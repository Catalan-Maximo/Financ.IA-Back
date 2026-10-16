package com.FinancIA.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Detalle de rendimiento calculado para un activo financiero concreto.
 */
@Data
@Builder
public class PerformanceDTO {

    /** Constructor explícito: evita el constructor vacío generado por Lombok (corrupto en javac incremental). */
    public PerformanceDTO() {}

    /** Constructor con todos los campos (reemplaza al @AllArgsConstructor). */
    public PerformanceDTO(String entity, String type, double tna, double effectiveMonthlyRate, double realMonthlyRate, double nominalGain, double realGain, boolean beatsInflation, Double worstCase, Double bestCase, String risk) {
        this.entity = entity;
        this.type = type;
        this.tna = tna;
        this.effectiveMonthlyRate = effectiveMonthlyRate;
        this.realMonthlyRate = realMonthlyRate;
        this.nominalGain = nominalGain;
        this.realGain = realGain;
        this.beatsInflation = beatsInflation;
        this.worstCase = worstCase;
        this.bestCase = bestCase;
        this.risk = risk;
    }


    /** Nombre de la entidad (ej. "Mercado Pago"). */
    private String entity;

    /** Tipo de instrumento (ej. "Billetera Virtual"). */
    private String type;

    /** Tasa Nominal Anual informada por la entidad. */
    private double tna;

    /** Tasa Efectiva Mensual = (1 + TNA/100)^(30/365) - 1, expresada como %. */
    private double effectiveMonthlyRate;

    /** Tasa Real Mensual = ((1 + TEM) / (1 + inflación)) - 1, expresada como %. */
    private double realMonthlyRate;

    /** Ganancia nominal proyectada en $ al final del plazo. */
    private double nominalGain;

    /** Ganancia real (ajustada por inflación) proyectada en $ al final del plazo. */
    private double realGain;

    /** true si la tasa real mensual supera la inflación. */
    private boolean beatsInflation;

    /** Percentil 5 del retorno total del horizonte (Monte Carlo), null en renta fija. */
    private Double worstCase;

    /** Percentil 95 del retorno total del horizonte (Monte Carlo), null en renta fija. */
    private Double bestCase;

    /** Nivel de riesgo: "Bajo" / "Medio" / "Alto" / "Extremo" (según volatilidad histórica). */
    private String risk;
}
