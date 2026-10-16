package com.FinancIA.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO de respuesta de la simulación con IA.
 * Contiene la recomendación personalizada, la distribución sugerida
 * del portafolio y los rendimientos calculados por activo.
 */
@Data
@Builder
public class IAResponseDTO {

    /** Constructor explícito: evita el constructor vacío generado por Lombok (corrupto en javac incremental). */
    public IAResponseDTO() {}

    /** Constructor con todos los campos (reemplaza al @AllArgsConstructor). */
    public IAResponseDTO(String investorProfile, String recommendation, String riskLevel, java.util.List<AllocationDTO> suggestedAllocation, java.util.List<PerformanceDTO> performances, double estimatedRealGain, String strategySummary) {
        this.investorProfile = investorProfile;
        this.recommendation = recommendation;
        this.riskLevel = riskLevel;
        this.suggestedAllocation = suggestedAllocation;
        this.performances = performances;
        this.estimatedRealGain = estimatedRealGain;
        this.strategySummary = strategySummary;
    }


    /** Perfil de riesgo utilizado para la recomendación. */
    private String investorProfile;

    /** Texto de recomendación generado por la IA. */
    private String recommendation;

    /** Nivel de riesgo general de la estrategia: "Bajo", "Medio", "Alto". */
    private String riskLevel;

    /** Distribución sugerida del portafolio. */
    private List<AllocationDTO> suggestedAllocation;

    /** Rendimientos proyectados para cada activo del mercado. */
    private List<PerformanceDTO> performances;

    /** Ganancia real total estimada del portafolio en $ al final del plazo. */
    private double estimatedRealGain;

    /** Resumen breve de la estrategia. */
    private String strategySummary;

    /**
     * Representa un porcentaje de asignación a un tipo de activo.
     */
    @Data
    @Builder
            public static class AllocationDTO {

        /** Constructor explícito (ver nota en IAResponseDTO). */
        public AllocationDTO() {}

        /** Constructor con todos los campos (reemplaza al @AllArgsConstructor). */
        public AllocationDTO(String assetType, int percentage, String reason) {
            this.assetType = assetType;
            this.percentage = percentage;
            this.reason = reason;
        }


        /** Nombre del tipo de activo (ej. "Billetera Virtual", "Plazo Fijo", "Dólar"). */
        private String assetType;
        /** Porcentaje sugerido del portafolio (0-100). */
        private int percentage;
        /** Justificación breve de la asignación. */
        private String reason;
    }
}
