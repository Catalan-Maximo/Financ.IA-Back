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
public class PerfilInversorService {

    public static final String CONSERVADOR = "Conservador";
    public static final String MODERADO = "Moderado";
    public static final String AGRESIVO = "Agresivo";

    /**
     * Devuelve el perfil de riesgo según la puntuación del test.
     */
    public String clasificar(int puntaje) {
        if (puntaje <= 13) return CONSERVADOR;
        if (puntaje <= 23) return MODERADO;
        return AGRESIVO;
    }
}
