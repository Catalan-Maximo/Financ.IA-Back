package com.FinancIA.api.service;

import com.FinancIA.api.dto.ComparacionRequest;
import com.FinancIA.api.dto.ComparacionResponse;
import com.FinancIA.api.dto.RendimientoDTO;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Servicio de cálculo financiero para activos de inversión.
 *
 * Fórmulas utilizadas:
 * ─────────────────────────────────────────────────────────
 * Tasa Efectiva Mensual (TEM):
 *   TEM = (1 + TNA/100)^(30/365) - 1
 *
 * Tasa Real Mensual (Fisher):
 *   TasaReal = ((1 + TEM) / (1 + inflación/100)) - 1
 *
 * Ganancia Nominal:
 *   GN = monto × (1 + TEM)^plazo - monto
 *
 * Ganancia Real:
 *   GR = monto × (1 + TasaReal)^plazo - monto
 * ─────────────────────────────────────────────────────────
 */
@Service
public class ActivoService {

    /**
     * Datos simulados de tasas del mercado.
     * En una iteración futura se podrían traer de una API externa o BD.
     */
    private static final List<Map<String, Object>> ACTIVOS_MERCADO = List.of(
        Map.of("entidad", "Mercado Pago",  "tna", 35.0, "tipo", "Billetera Virtual"),
        Map.of("entidad", "Personal Pay",  "tna", 37.5, "tipo", "Billetera Virtual"),
        Map.of("entidad", "Banco Nación",  "tna", 39.0, "tipo", "Plazo Fijo Tradicional")
    );

    /**
     * Calcula el rendimiento real de todos los activos del mercado
     * contra la inflación informada por el usuario.
     */
    public ComparacionResponse compararActivos(ComparacionRequest request) {
        double monto           = request.getMonto();
        int    plazoMeses      = request.getPlazoMeses();
        double inflacionMensual = request.getInflacionMensual();

        List<RendimientoDTO> rendimientos = ACTIVOS_MERCADO.stream()
                .map(activo -> calcularRendimiento(activo, monto, plazoMeses, inflacionMensual))
                .toList();

        // Determinamos cuál activo tiene la mejor tasa real mensual
        String mejorOpcion = rendimientos.stream()
                .max(Comparator.comparingDouble(RendimientoDTO::getTasaRealMensual))
                .map(RendimientoDTO::getEntidad)
                .orElse("N/A");

        return ComparacionResponse.builder()
                .montoInvertido(monto)
                .plazoMeses(plazoMeses)
                .inflacionMensualUsada(inflacionMensual)
                .rendimientos(rendimientos)
                .mejorOpcion(mejorOpcion)
                .build();
    }

    /**
     * Calcula las métricas de rendimiento para un activo individual.
     */
    private RendimientoDTO calcularRendimiento(
            Map<String, Object> activo,
            double monto,
            int plazoMeses,
            double inflacionMensual
    ) {
        String entidad = (String) activo.get("entidad");
        String tipo    = (String) activo.get("tipo");
        double tna     = ((Number) activo.get("tna")).doubleValue();

        // Tasa Efectiva Mensual: TEM = (1 + TNA/100)^(30/365) - 1
        double tem = Math.pow(1 + tna / 100.0, 30.0 / 365.0) - 1;

        // Tasa Real Mensual (ecuación de Fisher):
        // TasaReal = ((1 + TEM) / (1 + inflación/100)) - 1
        double tasaReal = ((1 + tem) / (1 + inflacionMensual / 100.0)) - 1;

        // Ganancia Nominal: monto × (1 + TEM)^plazo - monto
        double gananciaNominal = monto * Math.pow(1 + tem, plazoMeses) - monto;

        // Ganancia Real: monto × (1 + TasaReal)^plazo - monto
        double gananciaReal = monto * Math.pow(1 + tasaReal, plazoMeses) - monto;

        return RendimientoDTO.builder()
                .entidad(entidad)
                .tipo(tipo)
                .tna(tna)
                .tasaEfectivaMensual(redondear(tem * 100))
                .tasaRealMensual(redondear(tasaReal * 100))
                .gananciaNominal(redondear(gananciaNominal))
                .gananciaReal(redondear(gananciaReal))
                .leGanaALaInflacion(tasaReal > 0)
                .build();
    }

    /** Redondea a 2 decimales. */
    private double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
