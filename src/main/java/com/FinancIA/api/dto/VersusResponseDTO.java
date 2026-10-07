package com.FinancIA.api.dto;

/**
 * Resultado del VERSUS: los dos rendimientos calculados, el ganador,
 * la probabilidad de Monte Carlo (si ambos son proyectables) y un
 * mensaje explicativo en criollo.
 */
public record VersusResponseDTO(
        RendimientoDTO activoA,
        RendimientoDTO activoB,
        String ganador,
        Double probabilidadA,
        String mensaje
) {}
