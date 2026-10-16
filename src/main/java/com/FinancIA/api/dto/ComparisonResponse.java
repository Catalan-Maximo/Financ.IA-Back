package com.FinancIA.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO de respuesta que envuelve la lista de rendimientos calculados
 * junto con los parámetros de entrada utilizados y un resumen.
 */
@Data
@Builder
public class ComparisonResponse {

    /** Constructor explícito: evita el constructor vacío generado por Lombok (corrupto en javac incremental). */
    public ComparisonResponse() {}

    /** Constructor con todos los campos (reemplaza al @AllArgsConstructor). */
    public ComparisonResponse(double investedAmount, int months, double monthlyInflationUsed, java.util.List<PerformanceDTO> performances, String bestOption) {
        this.investedAmount = investedAmount;
        this.months = months;
        this.monthlyInflationUsed = monthlyInflationUsed;
        this.performances = performances;
        this.bestOption = bestOption;
    }


    /** Monto original ingresado. */
    private double investedAmount;

    /** Plazo en meses utilizado para el cálculo. */
    private int months;

    /** Inflación mensual utilizada (%). */
    private double monthlyInflationUsed;

    /** Lista de rendimientos calculados por cada activo. */
    private List<PerformanceDTO> performances;

    /** Entidad con mejor tasa real (la que más le gana a la inflación). */
    private String bestOption;
}
