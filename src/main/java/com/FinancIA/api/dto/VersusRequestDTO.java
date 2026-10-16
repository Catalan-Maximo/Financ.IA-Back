package com.FinancIA.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** Comparación cara a cara entre dos activos. */
@Data
public class VersusRequestDTO {

    /** Constructor explícito: evita el constructor vacío generado por Lombok (corrupto en javac incremental). */
    public VersusRequestDTO() {}

    /** Entidad del activo A (ej. "Plazo Fijo Tradicional" o entidad del comparador, "Bitcoin", "YPF"). */
    @NotBlank
    private String assetA;

    /** Entidad del activo B. */
    @NotBlank
    private String assetB;

    @Positive
    private double amount;
    @Min(1)
    private int months;
    @PositiveOrZero
    private double monthlyInflation;
}
