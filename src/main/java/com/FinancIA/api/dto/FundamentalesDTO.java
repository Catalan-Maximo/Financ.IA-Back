package com.FinancIA.api.dto;

import java.util.List;

/**
 * Fundamentales de una acción (Yahoo Finance): P/E con valoración
 * simple, crecimiento de ingresos, margen neto y situación de deuda.
 */
public record FundamentalesDTO(
        String ticker,
        Double pe,
        String valoracionPrecio,   // "Barata" / "Razonable" / "Cara" / "n/d"
        Double crecimientoIngresosPct,
        Double margenNetoPct,
        Double deuda,
        Double caja,
        boolean deudaMayorQueCaja,
        List<IngresoAnual> ingresosAnuales
) {
    /** Un año de resultados: ingresos y ganancia neta. */
    public record IngresoAnual(String anio, double ingresos, double neto) {}
}
