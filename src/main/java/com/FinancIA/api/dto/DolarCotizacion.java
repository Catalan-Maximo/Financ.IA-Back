package com.FinancIA.api.dto;

/** Cotización de un tipo de dólar (Oficial, Blue, MEP, CCL...). */
public record DolarCotizacion(String nombre, double compra, double venta, String fecha) {}
