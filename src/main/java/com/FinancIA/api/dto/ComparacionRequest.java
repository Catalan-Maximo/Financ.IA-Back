package com.FinancIA.api.dto;

import lombok.Data;

/**
 * DTO de entrada para solicitar la comparación de activos.
 * El frontend envía el monto a invertir, el plazo en meses
 * y la inflación mensual estimada para el cálculo.
 */
@Data
public class ComparacionRequest {

    /** Monto en pesos que el usuario quiere invertir. */
    private double monto;

    /** Plazo de la inversión en meses. */
    private int plazoMeses;

    /** Inflación mensual estimada (porcentaje, ej. 4.0 = 4%). */
    private double inflacionMensual;
}
