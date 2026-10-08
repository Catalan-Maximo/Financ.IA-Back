package com.FinancIA.api.dto;

import lombok.Data;

/** Pedido de explicación simple de un bloque de datos financieros. */
@Data
public class ExplicarRequestDTO {

    /** Texto con los datos a explicar (ej. "P/E 33.1, margen 5.3%..."). */
    private String datos;
}
