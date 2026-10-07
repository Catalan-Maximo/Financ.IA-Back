package com.FinancIA.api.dto;

/** Cotización de una acción/ETF con su variación de 24h en %. */
public record AccionCotizacion(String ticker, double precio, double variacion24h) {}
