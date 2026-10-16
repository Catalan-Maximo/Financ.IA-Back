package com.FinancIA.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * DTO de entrada para la simulación con IA.
 * Incluye el perfil del usuario, el monto disponible,
 * el plazo deseado y la inflación estimada para que
 * la IA genere una recomendación personalizada.
 */
@Data
public class IARequestDTO {

    /** Constructor explícito: evita el constructor vacío generado por Lombok (corrupto en javac incremental). */
    public IARequestDTO() {}

    /** Perfil de riesgo del inversor: "Conservador", "Moderado" o "Agresivo". */
    @NotBlank
    private String investorProfile;

    /** Monto disponible para invertir en pesos. */
    @Positive
    private double amount;

    /** Plazo de inversión deseado en meses. */
    @Min(1)
    private int months;

    /** Inflación mensual estimada (%, ej. 4.0 = 4%). */
    @PositiveOrZero
    private double monthlyInflation;

    /** Activo A a comparar (ej. "Plazo Fijo"). Opcional. */
    private String assetA;

    /** Activo B a comparar (ej. "Dólar"). Opcional. */
    private String assetB;
}
