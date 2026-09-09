package com.FinancIA.api.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Checklist de compra portado del proyecto crypto-checklist.
 *
 * 4 reglas:
 * 1. Contexto no bajista: precio >= SMA200 * 0.97.
 * 2. Cerca del soporte de 20 días: distancia <= 4%.
 * 3. RSI(14) sin sobrecompra: rsi < 70.
 * 4. No persiguiendo una suba fuerte: variación 3 días <= 12%.
 *
 * Veredicto: 3+ aprobadas = BUY, exactamente 2 = WATCH, resto = AVOID.
 * Si un indicador es null (faltan velas), esa regla falla con detalle.
 *
 * El checklist de VENTA (PnL, stop loss) no se porta: depende de un
 * diario de posiciones que Financ.IA no tiene.
 */
@Service
public class CriptoChecklistService {

    public static final String BUY = "BUY";
    public static final String WATCH = "WATCH";
    public static final String AVOID = "AVOID";

    private final IndicadoresService indicadores;

    public CriptoChecklistService(IndicadoresService indicadores) {
        this.indicadores = indicadores;
    }

    /** Resultado del análisis de un par. */
    public record CriptoAnalisis(
            double precio,
            Double sma50,
            Double sma200,
            Double rsi14,
            double soporte20,
            double resistencia20,
            Double cambio3d,
            int checksPasados,
            String veredicto
    ) {}

    public CriptoAnalisis analizar(List<Double> closes) {
        double precio = closes.get(closes.size() - 1);
        Double sma50 = indicadores.sma(closes, 50);
        Double sma200 = indicadores.sma(closes, 200);
        Double rsi14 = indicadores.rsi(closes, 14);
        double soporte20 = indicadores.soporte(closes, 20);
        double resistencia20 = indicadores.resistencia(closes, 20);
        Double cambio3d = indicadores.cambio3d(closes);

        List<Boolean> checks = new ArrayList<>();

        // 1. Contexto de fondo no bajista
        checks.add(sma200 != null && precio >= sma200 * 0.97);

        // 2. Cerca del soporte de 20 días
        double distSoporte = (precio - soporte20) / soporte20 * 100;
        checks.add(distSoporte <= 4);

        // 3. RSI sin sobrecompra
        checks.add(rsi14 != null && rsi14 < 70);

        // 4. No persiguiendo una suba fuerte
        checks.add(cambio3d != null && cambio3d <= 12);

        int pasados = (int) checks.stream().filter(Boolean::booleanValue).count();
        String veredicto = pasados >= 3 ? BUY : (pasados == 2 ? WATCH : AVOID);

        return new CriptoAnalisis(precio, sma50, sma200, rsi14,
                soporte20, resistencia20, cambio3d, pasados, veredicto);
    }
}
