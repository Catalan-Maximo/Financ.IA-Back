package com.FinancIA.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** Comparación cara a cara entre dos activos. */
@Data
public class VersusRequestDTO {

    /** Entidad del activo A (ej. "Plazo Fijo Tradicional" o entidad del comparador, "Bitcoin", "YPF"). */
    @NotBlank
    private String entidadA;

    /** Entidad del activo B. */
    @NotBlank
    private String entidadB;

    @Positive
    private double monto;
    @Min(1)
    private int plazoMeses;
    @PositiveOrZero
    private double inflacionMensual;
}
