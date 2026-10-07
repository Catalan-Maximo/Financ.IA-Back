package com.FinancIA.api.dto;

import java.util.List;

/** Un pack de inversión con su variación ponderada calculada con cotizaciones reales. */
public record PackCotizacion(
        Long id,
        String nombre,
        String riesgo,
        double variacion24h,
        List<ComposicionItem> composicion
) {
    /** Un activo dentro del pack con su peso. */
    public record ComposicionItem(String ticker, int porcentaje) {}
}
