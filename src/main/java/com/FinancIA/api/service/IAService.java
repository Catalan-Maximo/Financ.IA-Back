package com.FinancIA.api.service;

import com.FinancIA.api.dto.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Servicio de "IA" que genera recomendaciones personalizadas de inversión
 * según el perfil de riesgo del usuario.
 *
 * Lógica basada en reglas (rule-based) — simula el comportamiento de una IA
 * combinando los cálculos financieros del ActivoService con estrategias
 * predefinidas por perfil de riesgo.
 *
 * Perfiles soportados:
 * ─────────────────────────────────────────────────────────
 *  Conservador → Prioriza capital garantizado. 70% Plazo Fijo, 20% Billetera, 10% Dólar.
 *  Moderado    → Balancea rendimiento y seguridad. 40% Plazo Fijo, 40% Billetera, 20% Dólar.
 *  Agresivo    → Maximiza rendimiento. 20% Plazo Fijo, 50% Billetera, 30% Dólar.
 * ─────────────────────────────────────────────────────────
 */
@Service
public class IAService {

    private final ActivoService activoService;

    public IAService(ActivoService activoService) {
        this.activoService = activoService;
    }

    /**
     * Genera una recomendación de inversión personalizada.
     */
    public IAResponseDTO generarRecomendacion(IARequestDTO request) {
        String perfil          = normalizarPerfil(request.getPerfilInversor());
        double monto           = request.getMonto();
        int    plazoMeses      = request.getPlazoMeses();
        double inflacionMensual = request.getInflacionMensual();

        // 1. Calcular rendimientos reales usando ActivoService
        ComparacionRequest compReq = new ComparacionRequest();
        compReq.setMonto(monto);
        compReq.setPlazoMeses(plazoMeses);
        compReq.setInflacionMensual(inflacionMensual);

        ComparacionResponse comparacion = activoService.compararActivos(compReq);
        List<RendimientoDTO> rendimientos = comparacion.getRendimientos();

        // 2. Generar distribución de portafolio según perfil
        List<IAResponseDTO.AsignacionDTO> distribucion = generarDistribucion(perfil);

        // 3. Calcular ganancia real estimada ponderada del portafolio
        double gananciaRealEstimada = calcularGananciaPonderada(rendimientos, distribucion, monto);

        // 4. Generar texto de recomendación
        String recomendacion = generarTextoRecomendacion(
                perfil, rendimientos, distribucion, monto, plazoMeses, inflacionMensual,
                request.getActivoA(), request.getActivoB()
        );

        // 5. Resumen de estrategia
        String resumen = generarResumen(perfil, comparacion.getMejorOpcion(), inflacionMensual);

        return IAResponseDTO.builder()
                .perfilInversor(perfil)
                .recomendacion(recomendacion)
                .nivelRiesgo(mapearNivelRiesgo(perfil))
                .distribucionSugerida(distribucion)
                .rendimientos(rendimientos)
                .gananciaRealEstimada(redondear(gananciaRealEstimada))
                .resumenEstrategia(resumen)
                .build();
    }

    // ─── Distribución por perfil ────────────────────────────────

    private List<IAResponseDTO.AsignacionDTO> generarDistribucion(String perfil) {
        return switch (perfil) {
            case "Conservador" -> List.of(
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Plazo Fijo Tradicional")
                    .porcentaje(70)
                    .motivo("Capital garantizado con tasa regulada por BCRA")
                    .build(),
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Billetera Virtual")
                    .porcentaje(20)
                    .motivo("Liquidez inmediata para imprevistos")
                    .build(),
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Dólar / Cobertura")
                    .porcentaje(10)
                    .motivo("Cobertura mínima ante devaluación")
                    .build()
            );
            case "Agresivo" -> List.of(
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Billetera Virtual")
                    .porcentaje(50)
                    .motivo("Máxima liquidez con rendimiento diario competitivo")
                    .build(),
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Dólar / Cobertura")
                    .porcentaje(30)
                    .motivo("Diversificación agresiva ante volatilidad cambiaria")
                    .build(),
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Plazo Fijo Tradicional")
                    .porcentaje(20)
                    .motivo("Base de rendimiento garantizado")
                    .build()
            );
            default -> List.of( // Moderado
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Plazo Fijo Tradicional")
                    .porcentaje(40)
                    .motivo("Rendimiento estable con capital garantizado")
                    .build(),
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Billetera Virtual")
                    .porcentaje(40)
                    .motivo("Liquidez diaria con rendimiento competitivo")
                    .build(),
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Dólar / Cobertura")
                    .porcentaje(20)
                    .motivo("Cobertura moderada ante escenarios de devaluación")
                    .build()
            );
        };
    }

    // ─── Ganancia ponderada ─────────────────────────────────────

    private double calcularGananciaPonderada(
            List<RendimientoDTO> rendimientos,
            List<IAResponseDTO.AsignacionDTO> distribucion,
            double montoTotal
    ) {
        double gananciaTotal = 0;

        for (IAResponseDTO.AsignacionDTO asignacion : distribucion) {
            double pesoFraccion = asignacion.getPorcentaje() / 100.0;

            // Buscar el rendimiento que mejor matchea el tipo de activo
            RendimientoDTO rendimiento = rendimientos.stream()
                    .filter(r -> r.getTipo().toLowerCase().contains(
                            extraerPalabraClave(asignacion.getTipoActivo())))
                    .findFirst()
                    .orElse(rendimientos.stream()
                            .max(Comparator.comparingDouble(RendimientoDTO::getTasaRealMensual))
                            .orElse(null));

            if (rendimiento != null) {
                gananciaTotal += rendimiento.getGananciaReal() * pesoFraccion;
            }
        }

        return gananciaTotal;
    }

    // ─── Texto de recomendación ─────────────────────────────────

    private String generarTextoRecomendacion(
            String perfil,
            List<RendimientoDTO> rendimientos,
            List<IAResponseDTO.AsignacionDTO> distribucion,
            double monto,
            int plazoMeses,
            double inflacionMensual,
            String activoA,
            String activoB
    ) {
        StringBuilder sb = new StringBuilder();

        // Intro personalizada por perfil
        sb.append(switch (perfil) {
            case "Conservador" -> "🛡️ Como inversor conservador, tu prioridad es proteger el capital. ";
            case "Agresivo" -> "🚀 Como inversor agresivo, buscás maximizar el rendimiento aceptando mayor volatilidad. ";
            default -> "⚖️ Como inversor moderado, buscás un balance entre seguridad y rendimiento. ";
        });

        // Contexto de inflación
        sb.append(String.format("Con una inflación mensual del %.1f%%, ", inflacionMensual));

        // Análisis de activos
        long activosQueGanan = rendimientos.stream().filter(RendimientoDTO::isLeGanaALaInflacion).count();
        if (activosQueGanan == rendimientos.size()) {
            sb.append("todos los activos analizados le ganan a la inflación, lo cual es positivo. ");
        } else if (activosQueGanan > 0) {
            sb.append(String.format("solo %d de %d activos le ganan a la inflación. ", activosQueGanan, rendimientos.size()));
        } else {
            sb.append("ningún activo analizado supera la inflación — es momento de priorizar cobertura cambiaria. ");
        }

        // Comparación A vs B si se proporcionaron
        if (activoA != null && activoB != null && !activoA.isBlank() && !activoB.isBlank()) {
            sb.append(String.format("Comparando %s contra %s: ", activoA, activoB));
            sb.append("según tu perfil, te recomendamos diversificar entre ambos siguiendo la distribución sugerida. ");
        }

        // Recomendación de distribución
        sb.append("Mi sugerencia de distribución: ");
        for (IAResponseDTO.AsignacionDTO asig : distribucion) {
            sb.append(String.format("%d%% en %s, ", asig.getPorcentaje(), asig.getTipoActivo()));
        }

        // Cierre
        sb.append(String.format("para un plazo de %d meses con $%,.0f.", plazoMeses, monto));

        return sb.toString();
    }

    // ─── Resumen de estrategia ──────────────────────────────────

    private String generarResumen(String perfil, String mejorOpcion, double inflacion) {
        return switch (perfil) {
            case "Conservador" -> String.format(
                "Estrategia defensiva. Priorizar %s para capital garantizado. " +
                "Inflación mensual: %.1f%%. Foco en preservar poder adquisitivo.", mejorOpcion, inflacion);
            case "Agresivo" -> String.format(
                "Estrategia ofensiva. %s lidera en rendimiento real. " +
                "Inflación mensual: %.1f%%. Foco en maximizar ganancia real con diversificación.", mejorOpcion, inflacion);
            default -> String.format(
                "Estrategia balanceada. %s ofrece el mejor rendimiento real. " +
                "Inflación mensual: %.1f%%. Foco en equilibrar riesgo y retorno.", mejorOpcion, inflacion);
        };
    }

    // ─── Helpers ────────────────────────────────────────────────

    private String normalizarPerfil(String perfil) {
        if (perfil == null) return "Moderado";
        return switch (perfil.toLowerCase().trim()) {
            case "conservador" -> "Conservador";
            case "agresivo" -> "Agresivo";
            default -> "Moderado";
        };
    }

    private String mapearNivelRiesgo(String perfil) {
        return switch (perfil) {
            case "Conservador" -> "Bajo";
            case "Agresivo" -> "Alto";
            default -> "Medio";
        };
    }

    private String extraerPalabraClave(String tipoActivo) {
        if (tipoActivo.toLowerCase().contains("plazo")) return "plazo";
        if (tipoActivo.toLowerCase().contains("billetera")) return "billetera";
        return "billetera"; // fallback
    }

    private double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
