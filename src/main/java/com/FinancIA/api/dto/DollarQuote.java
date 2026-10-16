package com.FinancIA.api.dto;

/** Cotización de un tipo de dólar (Oficial, Blue, MEP, CCL...). */
public record DollarQuote(String name, double buyPrice, double sellPrice, String date) {}
