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

    public static final int SIMULACIONES = 10_000;
    private static final int DIAS_HABILES_POR_MES = 21;
    private static final long SEMILLA = 42L;

    /** Proyección de un activo: esperado mensual (%) y rango total del horizonte (%). */
    public record Proyeccion(double medianaMensualPct, double p5TotalPct, double p95TotalPct) {}

    /**
     * Probabilidad (0-1) de que A supere a B en el horizonte simulado.
     * Una sola pasada con la MISMA secuencia aleatoria para ambos activos
     * (compara los escenarios de a pares, como corresponde).
     */
    public double probabilidadSupera(List<Double> closesA, List<Double> closesB, int meses) {
        double muA = media(logRetornos(closesA));
        double sigmaA = desvioEstandar(logRetornos(closesA));
        double muB = media(logRetornos(closesB));
        double sigmaB = desvioEstandar(logRetornos(closesB));

        Random rng = new Random(SEMILLA);
        int pasos = meses * DIAS_HABILES_POR_MES;
        int ganaA = 0;

        for (int s = 0; s < SIMULACIONES; s++) {
            double logA = 0;
            double logB = 0;
            for (int t = 0; t < pasos; t++) {
                double z = rng.nextGaussian();
                logA += muA + sigmaA * z;
                logB += muB + sigmaB * z;
            }
            if (logA > logB) ganaA++;
        }
        return (double) ganaA / SIMULACIONES;
    }

    private double[] logRetornos(List<Double> closes) {
        double[] retornos = new double[closes.size() - 1];
        for (int i = 0; i < retornos.length; i++) {
            retornos[i] = Math.log(closes.get(i + 1) / closes.get(i));
        }
        return retornos;
    }

    /**
     * Volatilidad mensual (%) de un activo: desvío de los retornos
     * logarítmicos diarios anualizado a un mes (× √21 días hábiles).
     */
    public double volatilidadMensualPct(List<Double> closes) {
        double[] retornos = new double[closes.size() - 1];
        for (int i = 0; i < retornos.length; i++) {
            retornos[i] = Math.log(closes.get(i + 1) / closes.get(i));
        }
        return desvioEstandar(retornos) * Math.sqrt(DIAS_HABILES_POR_MES) * 100;
    }

    public Proyeccion simular(List<Double> closes, int meses) {
        // 1. Retornos logarítmicos diarios
        double[] retornos = new double[closes.size() - 1];
        for (int i = 0; i < retornos.length; i++) {
            retornos[i] = Math.log(closes.get(i + 1) / closes.get(i));
        }
        double mu = media(retornos);
        double sigma = desvioEstandar(retornos);

        // 2. Simulación
        Random rng = new Random(SEMILLA);
        int pasos = meses * DIAS_HABILES_POR_MES;
        double[] retornoTotal = new double[SIMULACIONES];
        double[] retornoMensual = new double[SIMULACIONES];

        for (int s = 0; s < SIMULACIONES; s++) {
            double logRet = 0;
            for (int t = 0; t < pasos; t++) {
                logRet += mu + sigma * rng.nextGaussian();
            }
            retornoTotal[s] = Math.exp(logRet) - 1;
            retornoMensual[s] = (Math.pow(1 + retornoTotal[s], 1.0 / meses) - 1) * 100;
        }

        // 3. Percentiles
        Arrays.sort(retornoTotal);
        Arrays.sort(retornoMensual);

        double p5 = retornoTotal[(int) (SIMULACIONES * 0.05)] * 100;
        double p95 = retornoTotal[(int) (SIMULACIONES * 0.95)] * 100;
        double medianaMensual = retornoMensual[SIMULACIONES / 2];

        return new Proyeccion(medianaMensual, p5, p95);
    }

    private double media(double[] valores) {
        double suma = 0;
        for (double v : valores) suma += v;
        return suma / valores.length;
    }

    /** Desvío estándar muestral. */
    private double desvioEstandar(double[] valores) {
        double m = media(valores);
        double suma = 0;
        for (double v : valores) suma += (v - m) * (v - m);
        return Math.sqrt(suma / (valores.length - 1));
    }
}
