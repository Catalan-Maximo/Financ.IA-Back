package com.FinancIA.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * DTO de entrada para solicitar la comparación de activos.
 * El frontend envía el monto a invertir, el plazo en meses
 * y la inflación mensual estimada para el cálculo.
 */
@Data
public class ComparacionRequest {

    /** Monto en pesos que el usuario quiere invertir. */
    @Positive
    private double monto;

    /** Plazo de la inversión en meses. */
    @Min(1)
    private int plazoMeses;

    /** Inflación mensual estimada (porcentaje, ej. 4.0 = 4%). */
    @PositiveOrZero
    private double inflacionMensual;
}
