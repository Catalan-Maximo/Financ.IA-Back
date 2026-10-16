package com.FinancIA.api.dto;

import java.util.List;

/** Un pack de inversión con su variación ponderada calculada con cotizaciones reales. */
public record PackQuote(
        Long id,
        String name,
        String risk,
        double variation24h,
        List<CompositionItem> composition
) {
    /** Un activo dentro del pack con su peso. */
    public record CompositionItem(String ticker, int percentage) {}
}
