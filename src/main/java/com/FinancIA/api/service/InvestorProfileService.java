package com.FinancIA.api.service;

import org.springframework.stereotype.Service;

/**
 * Clasifica al inversor según la puntuación del test de perfil.
 *
 * El cuestionario breve tiene 6 preguntas y la suma total se mueve
 * en un rango de 6 a 29 puntos:
 *
 * ─────────────────────────────────────────────────────────
 *  6 - 13  → Conservador  Evita el riesgo, busca liquidez
 * 14 - 23  → Moderado     Tolera fluctuaciones a mediano plazo
 * 24 - 29  → Agresivo     Maximiza rentabilidad a largo plazo
 * ─────────────────────────────────────────────────────────
 *
 * Los valores fuera de rango se clasifican al extremo más cercano.
 */
@Service
public class InvestorProfileService {

    public static final String CONSERVATIVE = "Conservador";
    public static final String MODERATE = "Moderado";
    public static final String AGGRESSIVE = "Agresivo";

    /**
     * Devuelve el perfil de riesgo según la puntuación del test.
     */
    public String classify(int score) {
        if (score <= 13) return CONSERVATIVE;
        if (score <= 23) return MODERATE;
        return AGGRESSIVE;
    }
}
