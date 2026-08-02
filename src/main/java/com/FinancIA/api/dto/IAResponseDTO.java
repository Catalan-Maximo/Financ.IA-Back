package com.FinancIA.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO de respuesta de la simulación con IA.
 * Contiene la recomendación personalizada, la distribución sugerida
 * del portafolio y los rendimientos calculados por activo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IAResponseDTO {

    /** Perfil de riesgo utilizado para la recomendación. */
    private String perfilInversor;

    /** Texto de recomendación generado por la IA. */
    private String recomendacion;

    /** Nivel de riesgo general de la estrategia: "Bajo", "Medio", "Alto". */
    private String nivelRiesgo;

    /** Distribución sugerida del portafolio. */
    private List<AsignacionDTO> distribucionSugerida;

    /** Rendimientos proyectados para cada activo del mercado. */
    private List<RendimientoDTO> rendimientos;

    /** Ganancia real total estimada del portafolio en $ al final del plazo. */
    private double gananciaRealEstimada;

    /** Resumen breve de la estrategia. */
    private String resumenEstrategia;

    /**
     * Representa un porcentaje de asignación a un tipo de activo.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AsignacionDTO {
        /** Nombre del tipo de activo (ej. "Billetera Virtual", "Plazo Fijo", "Dólar"). */
        private String tipoActivo;
        /** Porcentaje sugerido del portafolio (0-100). */
        private int porcentaje;
        /** Justificación breve de la asignación. */
        private String motivo;
    }
}
