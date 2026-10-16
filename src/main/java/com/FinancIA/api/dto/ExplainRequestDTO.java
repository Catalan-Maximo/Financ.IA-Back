package com.FinancIA.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Pedido de explicación simple de un bloque de datos financieros. */
@Data
public class ExplainRequestDTO {

    /** Constructor explícito: evita el constructor vacío generado por Lombok (corrupto en javac incremental). */
    public ExplainRequestDTO() {}

    /** Texto con los datos a explicar (ej. "P/E 33.1, margen 5.3%..."). */
    @Size(max = 4000, message = "El texto es demasiado largo")
    private String data;
}
