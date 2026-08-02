package com.FinancIA.api.dto;

import lombok.Data;

/**
 * DTO de entrada para la simulación con IA.
 * Incluye el perfil del usuario, el monto disponible,
 * el plazo deseado y la inflación estimada para que
 * la IA genere una recomendación personalizada.
 */
@Data
public class IARequestDTO {

    /** Perfil de riesgo del inversor: "Conservador", "Moderado" o "Agresivo". */
    private String perfilInversor;

    /** Monto disponible para invertir en pesos. */
    private double monto;

    /** Plazo de inversión deseado en meses. */
    private int plazoMeses;

    /** Inflación mensual estimada (%, ej. 4.0 = 4%). */
    private double inflacionMensual;

    /** Activo A a comparar (ej. "Plazo Fijo"). Opcional. */
    private String activoA;

    /** Activo B a comparar (ej. "Dólar"). Opcional. */
    private String activoB;
}
