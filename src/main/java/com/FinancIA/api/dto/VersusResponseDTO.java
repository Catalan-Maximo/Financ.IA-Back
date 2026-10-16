package com.FinancIA.api.dto;

/**
 * Resultado del VERSUS: los dos rendimientos calculados, el ganador,
 * la probabilidad de Monte Carlo (si ambos son proyectables) y un
 * mensaje explicativo en criollo.
 */
public record VersusResponseDTO(
        PerformanceDTO assetA,
        PerformanceDTO assetB,
        String winner,
        Double probabilityA,
        String message
) {}
