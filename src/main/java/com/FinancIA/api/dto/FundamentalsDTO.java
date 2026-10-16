package com.FinancIA.api.dto;

import java.util.List;

/**
 * Fundamentales de una acción (Yahoo Finance): P/E con valoración
 * simple, crecimiento de ingresos, margen neto y situación de deuda.
 */
public record FundamentalsDTO(
        String ticker,
        Double pe,
        String priceValuation,   // "Barata" / "Razonable" / "Cara" / "n/d"
        Double revenueGrowthPct,
        Double netMarginPct,
        Double debt,
        Double cash,
        boolean debtExceedsCash,
        List<AnnualIncome> annualIncomes
) {
    /** Un año de resultados: ingresos y ganancia neta. */
    public record AnnualIncome(String year, double revenue, double netIncome) {}
}
