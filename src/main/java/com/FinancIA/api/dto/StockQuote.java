package com.FinancIA.api.dto;

/** Cotización de una acción/ETF con su variación de 24h en % y valoración por P/E. */
public record StockQuote(String ticker, double price, double variation24h, String priceValuation) {}
