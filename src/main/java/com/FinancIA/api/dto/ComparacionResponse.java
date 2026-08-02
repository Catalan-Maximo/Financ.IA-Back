package com.FinancIA.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO de respuesta que envuelve la lista de rendimientos calculados
 * junto con los parámetros de entrada utilizados y un resumen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparacionResponse {

    /** Monto original ingresado. */
    private double montoInvertido;

    /** Plazo en meses utilizado para el cálculo. */
    private int plazoMeses;

    /** Inflación mensual utilizada (%). */
    private double inflacionMensualUsada;

    /** Lista de rendimientos calculados por cada activo. */
    private List<RendimientoDTO> rendimientos;

    /** Entidad con mejor tasa real (la que más le gana a la inflación). */
    private String mejorOpcion;
}
