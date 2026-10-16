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
public class CryptoChecklistService {

    public static final String BUY = "BUY";
    public static final String WATCH = "WATCH";
    public static final String AVOID = "AVOID";

    private final IndicatorsService indicators;

    public CryptoChecklistService(IndicatorsService indicators) {
        this.indicators = indicators;
    }

    /** Resultado del análisis de un par. */
    public record CryptoAnalysis(
            double price,
            Double sma50,
            Double sma200,
            Double rsi14,
            double support20,
            double resistance20,
            Double change3d,
            int checksPassed,
            String verdict
    ) {}

    public CryptoAnalysis analyze(List<Double> closes) {
        double price = closes.get(closes.size() - 1);
        Double sma50 = indicators.sma(closes, 50);
        Double sma200 = indicators.sma(closes, 200);
        Double rsi14 = indicators.rsi(closes, 14);
        double support20 = indicators.support(closes, 20);
        double resistance20 = indicators.resistance(closes, 20);
        Double change3d = indicators.change3d(closes);

        List<Boolean> checks = new ArrayList<>();

        // 1. Contexto de fondo no bajista
        checks.add(sma200 != null && price >= sma200 * 0.97);

        // 2. Cerca del soporte de 20 días
        double supportDistance = (price - support20) / support20 * 100;
        checks.add(supportDistance <= 4);

        // 3. RSI sin sobrecompra
        checks.add(rsi14 != null && rsi14 < 70);

        // 4. No persiguiendo una suba fuerte
        checks.add(change3d != null && change3d <= 12);

        int passed = (int) checks.stream().filter(Boolean::booleanValue).count();
        String verdict = passed >= 3 ? BUY : (passed == 2 ? WATCH : AVOID);

        return new CryptoAnalysis(price, sma50, sma200, rsi14,
                support20, resistance20, change3d, passed, verdict);
    }
}
