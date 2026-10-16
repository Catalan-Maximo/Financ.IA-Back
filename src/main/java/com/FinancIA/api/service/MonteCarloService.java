package com.FinancIA.api.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Simulación Monte Carlo para proyectar activos volátiles.
 *
 * Modelo:
 * 1. Retornos logarítmicos diarios de los cierres históricos.
 * 2. Drift (mu) = media de los retornos; volatilidad (sigma) = desvío estándar.
 * 3. 10.000 caminos simulando el horizonte (meses × 21 días hábiles):
 *    logRet += mu + sigma * Z, con Z ~ N(0,1).
 * 4. Resultado: mediana mensual (esperado) y percentiles 5/95 del
 *    retorno TOTAL del horizonte (rango con 90% de confianza).
 *
 * Semilla fija: misma entrada → misma salida (determinista y testeable).
 */
@Service
public class MonteCarloService {

    public static final int SIMULATIONS = 10_000;
    private static final int BUSINESS_DAYS_PER_MONTH = 21;
    private static final long SEED = 42L;

    /** Proyección de un activo: esperado mensual (%) y rango total del horizonte (%). */
    public record Projection(double medianaMensualPct, double p5TotalPct, double p95TotalPct) {}

    /**
     * Probabilidad (0-1) de que A supere a B en el horizonte simulado.
     * Una sola pasada con la MISMA secuencia aleatoria para ambos activos
     * (compara los escenarios de a pares, como corresponde).
     */
    public double probabilityOfOutperforming(List<Double> closesA, List<Double> closesB, int meses) {
        double muA = mean(logReturns(closesA));
        double sigmaA = standardDeviation(logReturns(closesA));
        double muB = mean(logReturns(closesB));
        double sigmaB = standardDeviation(logReturns(closesB));

        Random rng = new Random(SEED);
        int pasos = meses * BUSINESS_DAYS_PER_MONTH;
        int aWins = 0;

        for (int s = 0; s < SIMULATIONS; s++) {
            double logA = 0;
            double logB = 0;
            for (int t = 0; t < pasos; t++) {
                double z = rng.nextGaussian();
                logA += muA + sigmaA * z;
                logB += muB + sigmaB * z;
            }
            if (logA > logB) aWins++;
        }
        return (double) aWins / SIMULATIONS;
    }

    private double[] logReturns(List<Double> closes) {
        double[] returns = new double[closes.size() - 1];
        for (int i = 0; i < returns.length; i++) {
            returns[i] = Math.log(closes.get(i + 1) / closes.get(i));
        }
        return returns;
    }

    /**
     * Volatilidad mensual (%) de un activo: desvío de los retornos
     * logarítmicos diarios anualizado a un mes (× √21 días hábiles).
     */
    public double monthlyVolatilityPct(List<Double> closes) {
        double[] returns = new double[closes.size() - 1];
        for (int i = 0; i < returns.length; i++) {
            returns[i] = Math.log(closes.get(i + 1) / closes.get(i));
        }
        return standardDeviation(returns) * Math.sqrt(BUSINESS_DAYS_PER_MONTH) * 100;
    }

    public Projection simulate(List<Double> closes, int meses) {
        // 1. Retornos logarítmicos diarios
        double[] returns = new double[closes.size() - 1];
        for (int i = 0; i < returns.length; i++) {
            returns[i] = Math.log(closes.get(i + 1) / closes.get(i));
        }
        double mu = mean(returns);
        double sigma = standardDeviation(returns);

        // 2. Simulación
        Random rng = new Random(SEED);
        int pasos = meses * BUSINESS_DAYS_PER_MONTH;
        double[] totalReturn = new double[SIMULATIONS];
        double[] monthlyReturn = new double[SIMULATIONS];

        for (int s = 0; s < SIMULATIONS; s++) {
            double logRet = 0;
            for (int t = 0; t < pasos; t++) {
                logRet += mu + sigma * rng.nextGaussian();
            }
            totalReturn[s] = Math.exp(logRet) - 1;
            monthlyReturn[s] = (Math.pow(1 + totalReturn[s], 1.0 / meses) - 1) * 100;
        }

        // 3. Percentiles
        Arrays.sort(totalReturn);
        Arrays.sort(monthlyReturn);

        double p5 = totalReturn[(int) (SIMULATIONS * 0.05)] * 100;
        double p95 = totalReturn[(int) (SIMULATIONS * 0.95)] * 100;
        double medianaMensual = monthlyReturn[SIMULATIONS / 2];

        return new Projection(medianaMensual, p5, p95);
    }

    private double mean(double[] valores) {
        double sum = 0;
        for (double v : valores) sum += v;
        return sum / valores.length;
    }

    /** Desvío estándar muestral. */
    private double standardDeviation(double[] valores) {
        double m = mean(valores);
        double sum = 0;
        for (double v : valores) sum += (v - m) * (v - m);
        return Math.sqrt(sum / (valores.length - 1));
    }
}
