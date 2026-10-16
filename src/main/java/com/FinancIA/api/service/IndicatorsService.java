package com.FinancIA.api.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Análisis técnico portado del proyecto crypto-checklist (TypeScript):
 * SMA, RSI de Wilder, soporte/resistencia de 20 días y variación de 3 días.
 */
@Service
public class IndicatorsService {

    /** Media móvil simple de los últimos N cierres, o null si faltan velas. */
    public Double sma(List<Double> closes, int periodo) {
        if (closes.size() < periodo) return null;
        double sum = 0;
        for (int i = closes.size() - periodo; i < closes.size(); i++) {
            sum += closes.get(i);
        }
        return sum / periodo;
    }

    /** RSI de Wilder (suavizado): el estándar de las plataformas de trading. */
    public Double rsi(List<Double> closes, int periodo) {
        if (closes.size() < periodo + 1) return null;

        // Promedio simple de ganancias/pérdidas de los primeros `periodo` cambios
        double avgGain = 0;
        double avgLoss = 0;
        for (int i = 1; i <= periodo; i++) {
            double change = closes.get(i) - closes.get(i - 1);
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        avgGain /= periodo;
        avgLoss /= periodo;

        // Suavizado de Wilder para el resto de la serie
        for (int i = periodo + 1; i < closes.size(); i++) {
            double change = closes.get(i) - closes.get(i - 1);
            double gain = change > 0 ? change : 0;
            double loss = change < 0 ? Math.abs(change) : 0;
            avgGain = (avgGain * (periodo - 1) + gain) / periodo;
            avgLoss = (avgLoss * (periodo - 1) + loss) / periodo;
        }

        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100 - 100 / (1 + rs);
    }

    /** Mínimo de los últimos N cierres (soporte). */
    public double support(List<Double> closes, int window) {
        return closes.subList(closes.size() - window, closes.size()).stream()
                .mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
    }

    /** Máximo de los últimos N cierres (resistencia). */
    public double resistance(List<Double> closes, int window) {
        return closes.subList(closes.size() - window, closes.size()).stream()
                .mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
    }

    /** Variación % entre el cierre de hace 3 días y el actual, o null si faltan velas. */
    public Double change3d(List<Double> closes) {
        if (closes.size() < 4) return null;
        double actual = closes.get(closes.size() - 1);
        double threeDaysAgo = closes.get(closes.size() - 4);
        return (actual - threeDaysAgo) / threeDaysAgo * 100;
    }
}
