package com.FinancIA.api.dto;

import lombok.Data;

/** Comparación cara a cara entre dos activos. */
@Data
public class VersusRequestDTO {

    /** Entidad del activo A (ej. "Plazo Fijo Tradicional" o entidad del comparador, "Bitcoin", "YPF"). */
    private String entidadA;

    /** Entidad del activo B. */
    private String entidadB;

    private double monto;
    private int plazoMeses;
    private double inflacionMensual;
}
