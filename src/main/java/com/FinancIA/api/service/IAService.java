package com.FinancIA.api.service;

import com.FinancIA.api.domain.CriptoEstado;
import com.FinancIA.api.dto.*;
import com.FinancIA.api.repository.CriptoEstadoRepository;
import com.FinancIA.api.repository.TasaMercadoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio de "IA" que genera recomendaciones personalizadas de inversión
 * según el perfil de riesgo del usuario.
 *
 * La redacción del consejo se delega al LLM de Groq cuando hay API key
 * configurada (ver {@link GroqClient}); los cálculos financieros SIEMPRE
 * se hacen en Java y se le pasan al modelo como contexto. Si Groq no está
 * disponible, se usa la lógica rule-based como fallback.
 *
 * Perfiles soportados:
 * ─────────────────────────────────────────────────────────
 *  Conservador → Prioriza capital garantizado. 80% Plazo Fijo, 20% Dólar.
 *  Moderado    → Balancea rendimiento y cobertura. 60% Plazo Fijo, 40% Dólar.
 *  Agresivo    → Maximiza rendimiento asumiendo riesgo cambiario. 40% Plazo Fijo, 60% Dólar.
 * ─────────────────────────────────────────────────────────
 */
@Service
@Slf4j
public class IAService {

    /** Prompt del chatbot de dudas: responde con los datos vivos de la app. */
    private static final String CHAT_SYSTEM_PROMPT = """
            Sos el asistente de FinancIA, una app argentina de finanzas personales.
            Respondé dudas de usuarios que NO saben de economía: explicá con ejemplos
            simples y números, sin jerga innecesaria. Respuestas cortas (2-4 oraciones).
            Usá los datos actuales de la app que te pasamos abajo cuando la pregunta
            lo requiera; si no están en el contexto, decí que no tenés ese dato.
            Temas frecuentes (respondé en este espíritu):
            - TNA: tasa nominal anual, el interés que paga un instrumento antes de inflación.
            - Rendimiento real: ganancia descontando inflación; positivo = ganás poder de compra.
            - RSI: indicador 0-100; >70 sobrecomprado, <30 sobrevendido.
            - Soporte/resistencia: mínimo/máximo de los últimos 20 días.
            - BUY/WATCH/AVOID: veredicto de nuestro análisis técnico diario, no es garantía.
            - Las cripto son muy volátiles: pueden bajar 20% o más en días.
            """;

    private static final String SYSTEM_PROMPT = """
            Sos el asesor virtual de FinancIA, una app argentina de finanzas personales.
            Redactá recomendaciones de inversión claras y concretas en español rioplatense,
            dirigidas a un usuario no experto. Usá 2 párrafos cortos y una lista breve
            para la distribución sugerida. NO inventes datos: usá únicamente los números
            que te pasamos. No uses markdown complejo ni emojis excesivos.
            Reglas para cripto según el perfil de riesgo del usuario:
            - Conservador: NO recomendar cripto; mencionarla solo como advertencia de volatilidad.
            - Moderado: cripto solo como cobertura con porcentaje chico, y solo si el veredicto no es AVOID.
            - Agresivo: entrada permitida si el veredicto es BUY o WATCH.
            """;

    private final ActivoService activoService;
    private final GroqClient groqClient;
    private final CriptoEstadoRepository criptoRepository;
    private final MercadoService mercadoService;
    private final TasaMercadoRepository tasaRepository;

    public IAService(ActivoService activoService, GroqClient groqClient,
                     CriptoEstadoRepository criptoRepository,
                     MercadoService mercadoService,
                     TasaMercadoRepository tasaRepository) {
        this.activoService = activoService;
        this.groqClient = groqClient;
        this.criptoRepository = criptoRepository;
        this.mercadoService = mercadoService;
        this.tasaRepository = tasaRepository;
    }

    /**
     * Chat de dudas: responde con los datos vivos de la app como contexto.
     * La conversación completa viaja en el request y Groq la continúa.
     */
    public ChatResponseDTO responderChat(ChatRequestDTO request) {
        if (!groqClient.estaConfigurado()) {
            return new ChatResponseDTO(
                    "El chat no está disponible ahora (falta la clave de Groq). Probá más tarde.");
        }

        try {
            List<Map<String, String>> mensajes = new ArrayList<>();
            for (ChatRequestDTO.Mensaje m : request.getMensajes()) {
                mensajes.add(Map.of("rol", m.rol(), "contenido", m.contenido()));
            }

            String respuesta = groqClient.chat(CHAT_SYSTEM_PROMPT + "\n\n" + construirContextoChat(), mensajes);
            return new ChatResponseDTO(respuesta.trim());
        } catch (Exception e) {
            log.warn("Chat no disponible: {}", e.getMessage());
            return new ChatResponseDTO("No pude responder ahora mismo. Intentá de nuevo en unos segundos.");
        }
    }

    /** Snapshot de los datos de la app para que el chat responda con números reales. */
    private String construirContextoChat() {
        StringBuilder sb = new StringBuilder();
        sb.append("Datos actuales de la app:\n");

        try {
            sb.append("- Inflación mensual oficial: ");
            tasaRepository.findTop1ByTipoActivoOrderByFechaDesc(MercadoDataJob.INFLACION_MENSUAL)
                    .ifPresentOrElse(t -> sb.append(String.format("%.1f%%%n", t.getValor())),
                            () -> sb.append("sin dato%n"));
        } catch (Exception e) {
            sb.append("sin dato%n");
        }

        try {
            sb.append("- Tasas de hoy (renta fija y billeteras): ");
            sb.append(activoService.getActivos().stream()
                    .map(a -> String.format("%s (%.1f%%)", a.get("entidad"), a.get("tna")))
                    .collect(java.util.stream.Collectors.joining(", ")));
            sb.append("\n");
        } catch (Exception e) {
            sb.append("sin dato\n");
        }

        try {
            sb.append("- Cripto: ");
            List<CriptoEstado> cripto = BinanceClient.PARES.stream()
                    .map(par -> criptoRepository.findTop1BySimboloOrderByFechaDesc(par))
                    .flatMap(Optional::stream)
                    .toList();
            sb.append(cripto.stream()
                    .map(c -> String.format("%s $%.0f (%s)", c.getSimbolo(), c.getPrecio(), c.getVeredicto()))
                    .collect(java.util.stream.Collectors.joining(", ")));
            sb.append("\n");
        } catch (Exception e) {
            sb.append("sin dato\n");
        }

        try {
            sb.append("- Dólar: ");
            sb.append(mercadoService.dolar().stream()
                    .map(d -> String.format("%s $%.0f", d.nombre(), d.venta()))
                    .collect(java.util.stream.Collectors.joining(", ")));
            sb.append("\n");
        } catch (Exception e) {
            sb.append("sin dato\n");
        }

        try {
            sb.append("- Packs: ");
            sb.append(mercadoService.packs().stream()
                    .map(p -> String.format("%s [%s] %+.2f%%", p.nombre(), p.riesgo(), p.variacion24h()))
                    .collect(java.util.stream.Collectors.joining(", ")));
        } catch (Exception e) {
            sb.append("sin dato");
        }

        return sb.toString();
    }

    /**
     * Genera una recomendación de inversión personalizada.
     */
    public IAResponseDTO generarRecomendacion(IARequestDTO request) {
        String perfil           = normalizarPerfil(request.getPerfilInversor());
        double monto            = request.getMonto();
        int    plazoMeses       = request.getPlazoMeses();
        double inflacionMensual = request.getInflacionMensual();

        // 1. Calcular rendimientos reales usando ActivoService (datos del BCRA)
        ComparacionRequest compReq = new ComparacionRequest();
        compReq.setMonto(monto);
        compReq.setPlazoMeses(plazoMeses);
        compReq.setInflacionMensual(inflacionMensual);

        ComparacionResponse comparacion = activoService.compararActivos(compReq);
        List<RendimientoDTO> rendimientos = comparacion.getRendimientos();

        // 2. Generar distribución de portafolio según perfil
        List<IAResponseDTO.AsignacionDTO> distribucion = generarDistribucion(perfil);

        // 3. Calcular ganancia real estimada ponderada del portafolio
        double gananciaRealEstimada = calcularGananciaPonderada(rendimientos, distribucion);

        // 3b. Estado cripto (últimos snapshots del job de Binance)
        List<CriptoEstado> cripto = BinanceClient.PARES.stream()
                .map(par -> criptoRepository.findTop1BySimboloOrderByFechaDesc(par))
                .flatMap(Optional::stream)
                .toList();

        // 3c. Mercados reales (packs + dólar) — si las APIs fallan, seguimos sin ese contexto
        List<PackCotizacion> packs = List.of();
        List<DolarCotizacion> dolares = List.of();
        try {
            packs = mercadoService.packs();
            dolares = mercadoService.dolar();
        } catch (Exception e) {
            log.warn("No se pudieron cargar los mercados para la recomendación: {}", e.getMessage());
        }

        // 4. Texto de recomendación: Groq si está disponible, si no rule-based
        String recomendacion = generarRecomendacionConFallback(
                perfil, rendimientos, distribucion, cripto, packs, dolares,
                monto, plazoMeses, inflacionMensual,
                request.getActivoA(), request.getActivoB()
        );

        // 5. Resumen de estrategia (rule-based, corto y estable)
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

    // ─── Recomendación: Groq con fallback ──────────────────────

    private String generarRecomendacionConFallback(
            String perfil,
            List<RendimientoDTO> rendimientos,
            List<IAResponseDTO.AsignacionDTO> distribucion,
            List<CriptoEstado> cripto,
            List<PackCotizacion> packs,
            List<DolarCotizacion> dolares,
            double monto,
            int plazoMeses,
            double inflacionMensual,
            String activoA,
            String activoB
    ) {
        String fallback = generarTextoRecomendacion(
                perfil, rendimientos, distribucion, cripto, packs, dolares,
                monto, plazoMeses, inflacionMensual,
                activoA, activoB
        );

        if (!groqClient.estaConfigurado()) {
            log.info("GROQ_API_KEY no configurada — usando recomendación rule-based");
            return fallback;
        }

        try {
            String textoGroq = groqClient.generarRecomendacion(
                    SYSTEM_PROMPT,
                    construirPromptUsuario(perfil, rendimientos, distribucion, cripto, packs, dolares,
                            monto, plazoMeses, inflacionMensual)
            );
            if (textoGroq != null && !textoGroq.isBlank()) {
                return textoGroq.trim();
            }
        } catch (Exception e) {
            // La app no debe romperse si Groq no responde
            log.warn("Groq no respondió, usando recomendación rule-based: {}", e.getMessage());
        }
        return fallback;
    }

    /** Arma el prompt con los datos calculados para que el LLM solo redacte. */
    private String construirPromptUsuario(
            String perfil,
            List<RendimientoDTO> rendimientos,
            List<IAResponseDTO.AsignacionDTO> distribucion,
            List<CriptoEstado> cripto,
            List<PackCotizacion> packs,
            List<DolarCotizacion> dolares,
            double monto,
            int plazoMeses,
            double inflacionMensual
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Perfil de riesgo: %s.%n", perfil));
        sb.append(String.format("Monto a invertir: $%,.0f.%n", monto));
        sb.append(String.format("Plazo: %d meses.%n", plazoMeses));
        sb.append(String.format("Inflación mensual estimada: %.1f%%.%n", inflacionMensual));
        sb.append(String.format("%nRendimientos calculados con datos reales del BCRA:%n"));
        for (RendimientoDTO r : rendimientos) {
            sb.append(String.format(
                    "- %s (%s): TNA %.2f%%, tasa real mensual %+.2f%%, ganancia real estimada $%,.0f%n",
                    r.getEntidad(), r.getTipo(), r.getTna(), r.getTasaRealMensual(), r.getGananciaReal()
            ));
        }
        sb.append(String.format("%nDistribución sugerida por reglas:%n"));
        for (IAResponseDTO.AsignacionDTO a : distribucion) {
            sb.append(String.format("- %d%% %s: %s%n", a.getPorcentaje(), a.getTipoActivo(), a.getMotivo()));
        }
        sb.append(String.format("%nEstado cripto (análisis técnico, Binance):%n"));
        if (cripto.isEmpty()) {
            sb.append("- Sin datos cripto disponibles hoy.%n");
        } else {
            for (CriptoEstado c : cripto) {
                String rsi = c.getRsi14() == null ? "n/d" : String.format("%.1f", c.getRsi14());
                sb.append(String.format(
                        "- %s: precio $%,.0f, RSI %s, veredicto %s (%d/4 checks)%n",
                        c.getSimbolo(), c.getPrecio(), rsi, c.getVeredicto(), c.getChecksPasados()
                ));
            }
        }
        sb.append(String.format("%nMercados (cotizaciones reales de hoy):%n"));
        if (!dolares.isEmpty()) {
            List<String> dolaresPrincipales = dolares.stream().limit(4)
                    .map(d -> String.format("%s $%,.0f", d.nombre(), d.venta()))
                    .toList();
            sb.append(String.format("- Dólar (venta): %s%n", String.join(", ", dolaresPrincipales)));
        }
        if (!packs.isEmpty()) {
            sb.append("- Packs de inversión (variación 24h): ");
            sb.append(packs.stream()
                    .map(p -> String.format("%s [%s] %+.2f%%", p.nombre(), p.riesgo(), p.variacion24h()))
                    .collect(java.util.stream.Collectors.joining(", ")));
            sb.append(String.format("%n"));
        }
        sb.append(String.format("%nEscribí la recomendación para este usuario.%n"));
        return sb.toString();
    }

    // ─── Distribución por perfil ────────────────────────────────

    private List<IAResponseDTO.AsignacionDTO> generarDistribucion(String perfil) {
        return switch (perfil) {
            case "Conservador" -> List.of(
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Plazo Fijo Tradicional")
                    .porcentaje(80)
                    .motivo("Capital garantizado con tasa regulada por BCRA")
                    .build(),
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Dólar / Cobertura")
                    .porcentaje(20)
                    .motivo("Cobertura mínima ante devaluación")
                    .build()
            );
            case "Agresivo" -> List.of(
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Dólar / Cobertura")
                    .porcentaje(60)
                    .motivo("Mayor exposición cambiaria buscando ganancia por devaluación")
                    .build(),
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Plazo Fijo Tradicional")
                    .porcentaje(40)
                    .motivo("Piso de rendimiento garantizado")
                    .build()
            );
            default -> List.of( // Moderado
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Plazo Fijo Tradicional")
                    .porcentaje(60)
                    .motivo("Base de rendimiento estable con capital garantizado")
                    .build(),
                IAResponseDTO.AsignacionDTO.builder()
                    .tipoActivo("Dólar / Cobertura")
                    .porcentaje(40)
                    .motivo("Cobertura moderada ante escenarios cambiarios")
                    .build()
            );
        };
    }

    // ─── Ganancia ponderada ─────────────────────────────────────

    private double calcularGananciaPonderada(
            List<RendimientoDTO> rendimientos,
            List<IAResponseDTO.AsignacionDTO> distribucion
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

    // ─── Texto de recomendación rule-based (fallback) ───────────

    private String generarTextoRecomendacion(
            String perfil,
            List<RendimientoDTO> rendimientos,
            List<IAResponseDTO.AsignacionDTO> distribucion,
            List<CriptoEstado> cripto,
            List<PackCotizacion> packs,
            List<DolarCotizacion> dolares,
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
        if (rendimientos.isEmpty()) {
            sb.append("todavía no hay datos de mercado cargados. ");
        } else if (activosQueGanan == rendimientos.size()) {
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

        // Cripto según perfil
        cripto.stream()
                .filter(c -> c.getSimbolo().equals("BTCUSDT"))
                .findFirst()
                .ifPresent(btc -> sb.append(String.format(" Cripto: BTC en %s (%d/4 checks) — %s",
                        btc.getVeredicto(), btc.getChecksPasados(),
                        switch (perfil) {
                            case "Conservador" -> "demasiado volátil para tu perfil, evitá. ";
                            case "Agresivo" -> "podés considerarla si el veredicto es BUY o WATCH. ";
                            default -> "solo como cobertura chica si el veredicto no es AVOID. ";
                        })));

        // Mercados: mencionar el pack que más rinde hoy (si hay datos)
        packs.stream()
                .max(Comparator.comparingDouble(PackCotizacion::variacion24h))
                .ifPresent(mejor -> sb.append(String.format(
                        " En mercados, hoy el pack %s es el que más rinde (%+.2f%%, riesgo %s). ",
                        mejor.nombre(), mejor.variacion24h(), mejor.riesgo())));

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

    /** Palabra clave del tipo de activo para matchear contra los rendimientos. */
    private String extraerPalabraClave(String tipoActivo) {
        String lower = tipoActivo.toLowerCase();
        if (lower.contains("plazo")) return "plazo";
        if (lower.contains("billetera")) return "billetera";
        if (lower.contains("dólar") || lower.contains("dolar")) return "dólar";
        return "plazo"; // fallback conservador
    }

    private double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
