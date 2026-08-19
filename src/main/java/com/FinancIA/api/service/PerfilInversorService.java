package com.FinancIA.api.service;

import org.springframework.stereotype.Service;

/**
 * Clasifica al inversor según la puntuación del test de perfil.
 *
 * El cuestionario tiene 10 preguntas y la suma total se mueve
 * en un rango de 8 a 49 puntos:
 *
 * ─────────────────────────────────────────────────────────
 *  8 - 18  → Conservador  🛡️ Evita el riesgo, busca liquidez
 * 19 - 35  → Moderado     ⚖️ Tolera fluctuaciones a mediano plazo
 * 36 - 49  → Agresivo     📈 Maximiza rentabilidad a largo plazo
 * ─────────────────────────────────────────────────────────
 *
 * Los valores fuera de rango se clasifican al extremo más cercano
 * (menos de 8 → Conservador, más de 49 → Agresivo).
 */
@Service
public class PerfilInversorService {

    public static final String CONSERVADOR = "Conservador";
    public static final String MODERADO = "Moderado";
    public static final String AGRESIVO = "Agresivo";

    /**
     * Devuelve el perfil de riesgo según la puntuación del test.
     */
    public String clasificar(int puntaje) {
        if (puntaje <= 18) return CONSERVADOR;
        if (puntaje <= 35) return MODERADO;
        return AGRESIVO;
    }
}
