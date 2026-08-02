package com.FinancIA.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detalle de rendimiento calculado para un activo financiero concreto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RendimientoDTO {

    /** Nombre de la entidad (ej. "Mercado Pago"). */
    private String entidad;

    /** Tipo de instrumento (ej. "Billetera Virtual"). */
    private String tipo;

    /** Tasa Nominal Anual informada por la entidad. */
    private double tna;

    /** Tasa Efectiva Mensual = (1 + TNA/100)^(30/365) - 1, expresada como %. */
    private double tasaEfectivaMensual;

    /** Tasa Real Mensual = ((1 + TEM) / (1 + inflación)) - 1, expresada como %. */
    private double tasaRealMensual;

    /** Ganancia nominal proyectada en $ al final del plazo. */
    private double gananciaNominal;

    /** Ganancia real (ajustada por inflación) proyectada en $ al final del plazo. */
    private double gananciaReal;

    /** true si la tasa real mensual supera la inflación. */
    private boolean leGanaALaInflacion;
}
