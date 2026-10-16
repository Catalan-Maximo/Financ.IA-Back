package com.FinancIA.api.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.FinancIA.api.domain.CryptoState;
import com.FinancIA.api.dto.ChatRequestDTO;
import com.FinancIA.api.dto.ChatResponseDTO;
import com.FinancIA.api.dto.ComparisonRequest;
import com.FinancIA.api.dto.ComparisonResponse;
import com.FinancIA.api.dto.DollarQuote;
import com.FinancIA.api.dto.ExplainRequestDTO;
import com.FinancIA.api.dto.IARequestDTO;
import com.FinancIA.api.dto.IAResponseDTO;
import com.FinancIA.api.dto.PackQuote;
import com.FinancIA.api.dto.PerformanceDTO;
import com.FinancIA.api.repository.CryptoStateRepository;
import com.FinancIA.api.repository.MarketRateRepository;

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
public class IAService {

    private static final Logger log = LoggerFactory.getLogger(IAService.class);

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

    private final AssetService assetService;
    private final GroqClient groqClient;
    private final CryptoStateRepository cryptoRepository;
    private final MarketService marketService;
    private final MarketRateRepository rateRepository;

    public IAService(AssetService assetService, GroqClient groqClient,
                     CryptoStateRepository cryptoRepository,
                     MarketService marketService,
                     MarketRateRepository rateRepository) {
        this.assetService = assetService;
        this.groqClient = groqClient;
        this.cryptoRepository = cryptoRepository;
        this.marketService = marketService;
        this.rateRepository = rateRepository;
    }

    /**
     * Explica un bloque de datos financieros en palabras simples,
     * para usuarios que no saben de economía. Fallback: devuelve el texto tal cual.
     */
    public ChatResponseDTO explain(ExplainRequestDTO request) {
        String prompt = """
                Explicá estos datos financieros en palabras SIMPLES, para una persona
                que no sabe de economía. Qué significa cada número, si es bueno o malo,
                y qué debería hacer el usuario con esa información. 3-5 oraciones,
                español rioplatense, sin jerga. NO inventes números.

                Datos:
                %s
                """.formatted(request.getData());

        if (!groqClient.isConfigured()) {
            return new ChatResponseDTO("No tengo IA disponible ahora. Probá más tarde.");
        }
        try {
            String answer = groqClient.generateRecommendation(
                    "Sos un traductor de finanzas a lenguaje cotidiano.", prompt);
            return new ChatResponseDTO(answer.trim());
        } catch (Exception e) {
            log.warn("Explicación no disponible: {}", e.getMessage());
            return new ChatResponseDTO("No pude explicarlo ahora. Intentá de nuevo en unos segundos.");
        }
    }

    /**
     * Chat de dudas: responde con los datos vivos de la app como contexto.
     * La conversación completa viaja en el request y Groq la continúa.
     */
    public ChatResponseDTO answerChat(ChatRequestDTO request) {
        if (!groqClient.isConfigured()) {
            return new ChatResponseDTO(
                    "El chat no está disponible ahora (falta la clave de Groq). Probá más tarde.");
        }

        try {
            List<Map<String, String>> messages = new ArrayList<>();
            for (ChatRequestDTO.Message m : request.getMessages()) {
                messages.add(Map.of("role", m.rol(), "content", m.contenido()));
            }

            String answer = groqClient.chat(CHAT_SYSTEM_PROMPT + "\n\n" + buildChatContext(), messages);
            return new ChatResponseDTO(answer.trim());
        } catch (Exception e) {
            log.warn("Chat no disponible: {}", e.getMessage());
            return new ChatResponseDTO("No pude responder ahora mismo. Intentá de nuevo en unos segundos.");
        }
    }

    /** Snapshot de los datos de la app para que el chat responda con números reales. */
    private String buildChatContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("Datos actuales de la app:\n");

        try {
            sb.append("- Inflación mensual oficial: ");
            rateRepository.findTop1ByAssetTypeOrderByDateDesc(MarketDataJob.MONTHLY_INFLATION)
                    .ifPresentOrElse(t -> sb.append(String.format("%.1f%%%n", t.getValue())),
                            () -> sb.append("sin dato%n"));
        } catch (Exception e) {
            sb.append("sin dato%n");
        }

        try {
            sb.append("- Tasas de hoy (renta fija y billeteras): ");
            sb.append(assetService.getAssets().stream()
                    .map(a -> String.format("%s (%.1f%%)", a.get("entity"), a.get("tna")))
                    .collect(java.util.stream.Collectors.joining(", ")));
            sb.append("\n");
        } catch (Exception e) {
            sb.append("sin dato\n");
        }

        try {
            sb.append("- Cripto: ");
            List<CryptoState> crypto = BinanceClient.PAIRS.stream()
                    .map(par -> cryptoRepository.findTop1BySymbolOrderByDateDesc(par))
                    .flatMap(Optional::stream)
                    .toList();
            sb.append(crypto.stream()
                    .map(c -> String.format("%s $%.0f (%s)", c.getSymbol(), c.getPrice(), c.getVerdict()))
                    .collect(java.util.stream.Collectors.joining(", ")));
            sb.append("\n");
        } catch (Exception e) {
            sb.append("sin dato\n");
        }

        try {
            sb.append("- Dólar: ");
            sb.append(marketService.dollar().stream()
                    .map(d -> String.format("%s $%.0f", d.name(), d.sellPrice()))
                    .collect(java.util.stream.Collectors.joining(", ")));
            sb.append("\n");
        } catch (Exception e) {
            sb.append("sin dato\n");
        }

        try {
            sb.append("- Packs: ");
            sb.append(marketService.packs().stream()
                    .map(p -> String.format("%s [%s] %+.2f%%", p.name(), p.risk(), p.variation24h()))
                    .collect(java.util.stream.Collectors.joining(", ")));
            sb.append("\n");
        } catch (Exception e) {
            sb.append("sin dato\n");
        }

        try {
            sb.append("- Fundamentales de acciones (Yahoo): ");
            sb.append(String.join(" | ", marketService.fundamentalsSummary()));
        } catch (Exception e) {
            sb.append("sin dato");
        }

        return sb.toString();
    }

    /**
     * Genera una recomendación de inversión personalizada.
     */
    public IAResponseDTO generateRecommendation(IARequestDTO request) {
        String profile           = normalizeProfile(request.getInvestorProfile());
        double amount            = request.getAmount();
        int    months       = request.getMonths();
        double monthlyInflation = request.getMonthlyInflation();

        // 1. Calcular rendimientos reales usando ActivoService (datos del BCRA)
        ComparisonRequest comparisonRequest = new ComparisonRequest();
        comparisonRequest.setAmount(amount);
        comparisonRequest.setMonths(months);
        comparisonRequest.setMonthlyInflation(monthlyInflation);

        ComparisonResponse comparison = assetService.compareAssets(comparisonRequest);
        List<PerformanceDTO> performances = comparison.getPerformances();

        // 2. Generar distribución de portafolio según perfil
        List<IAResponseDTO.AllocationDTO> allocation = generateAllocation(profile);

        // 3. Calcular ganancia real estimada ponderada del portafolio
        double estimatedRealGain = calculateWeightedGain(performances, allocation);

        // 3b. Estado cripto (últimos snapshots del job de Binance)
        List<CryptoState> crypto = BinanceClient.PAIRS.stream()
                .map(par -> cryptoRepository.findTop1BySymbolOrderByDateDesc(par))
                .flatMap(Optional::stream)
                .toList();

        // 3c. Mercados reales (packs + dólar) — si las APIs fallan, seguimos sin ese contexto
        List<PackQuote> packs = List.of();
        List<DollarQuote> dollars = List.of();
        try {
            packs = marketService.packs();
            dollars = marketService.dollar();
        } catch (Exception e) {
            log.warn("No se pudieron cargar los mercados para la recomendación: {}", e.getMessage());
        }

        // 4. Texto de recomendación: Groq si está disponible, si no rule-based
        String recommendation = generateRecommendationWithFallback(
                profile, performances, allocation, crypto, packs, dollars,
                amount, months, monthlyInflation,
                request.getAssetA(), request.getAssetB()
        );

        // 5. Resumen de estrategia (rule-based, corto y estable)
        String summary = generateSummary(profile, comparison.getBestOption(), monthlyInflation);

        return IAResponseDTO.builder()
                .investorProfile(profile)
                .recommendation(recommendation)
                .riskLevel(mapRiskLevel(profile))
                .suggestedAllocation(allocation)
                .performances(performances)
                .estimatedRealGain(round2(estimatedRealGain))
                .strategySummary(summary)
                .build();
    }

    // ─── Recomendación: Groq con fallback ──────────────────────

    private String generateRecommendationWithFallback(
            String profile,
            List<PerformanceDTO> performances,
            List<IAResponseDTO.AllocationDTO> allocation,
            List<CryptoState> crypto,
            List<PackQuote> packs,
            List<DollarQuote> dollars,
            double amount,
            int months,
            double monthlyInflation,
            String assetA,
            String assetB
    ) {
        String fallback = generateRuleBasedRecommendation(
                profile, performances, allocation, crypto, packs,
                amount, months, monthlyInflation,
                assetA, assetB
        );

        if (!groqClient.isConfigured()) {
            log.info("GROQ_API_KEY no configurada — usando recomendación rule-based");
            return fallback;
        }

        try {
            String textoGroq = groqClient.generateRecommendation(
                    SYSTEM_PROMPT,
                    buildUserPrompt(profile, performances, allocation, crypto, packs, dollars,
                            amount, months, monthlyInflation)
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
    private String buildUserPrompt(
            String profile,
            List<PerformanceDTO> performances,
            List<IAResponseDTO.AllocationDTO> allocation,
            List<CryptoState> crypto,
            List<PackQuote> packs,
            List<DollarQuote> dollars,
            double amount,
            int months,
            double monthlyInflation
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Perfil de riesgo: %s.%n", profile));
        sb.append(String.format("Monto a invertir: $%,.0f.%n", amount));
        sb.append(String.format("Plazo: %d meses.%n", months));
        sb.append(String.format("Inflación mensual estimada: %.1f%%.%n", monthlyInflation));
        sb.append(String.format("%nRendimientos calculados con datos reales del BCRA:%n"));
        for (PerformanceDTO r : performances) {
            sb.append(String.format(
                    "- %s (%s): TNA %.2f%%, tasa real mensual %+.2f%%, ganancia real estimada $%,.0f%n",
                    r.getEntity(), r.getType(), r.getTna(), r.getRealMonthlyRate(), r.getRealGain()
            ));
        }
        sb.append(String.format("%nDistribución sugerida por reglas:%n"));
        for (IAResponseDTO.AllocationDTO a : allocation) {
            sb.append(String.format("- %d%% %s: %s%n", a.getPercentage(), a.getAssetType(), a.getReason()));
        }
        sb.append(String.format("%nEstado cripto (análisis técnico, Binance):%n"));
        if (crypto.isEmpty()) {
            sb.append("- Sin datos cripto disponibles hoy.%n");
        } else {
            for (CryptoState c : crypto) {
                String rsi = c.getRsi14() == null ? "n/d" : String.format("%.1f", c.getRsi14());
                sb.append(String.format(
                        "- %s: precio $%,.0f, RSI %s, veredicto %s (%d/4 checks)%n",
                        c.getSymbol(), c.getPrice(), rsi, c.getVerdict(), c.getChecksPassed()
                ));
            }
        }
        sb.append(String.format("%nMercados (cotizaciones reales de hoy):%n"));
        if (!dollars.isEmpty()) {
            List<String> mainDollars = dollars.stream().limit(4)
                    .map(d -> String.format("%s $%,.0f", d.name(), d.sellPrice()))
                    .toList();
            sb.append(String.format("- Dólar (venta): %s%n", String.join(", ", mainDollars)));
        }
        if (!packs.isEmpty()) {
            sb.append("- Packs de inversión (variación 24h): ");
            sb.append(packs.stream()
                    .map(p -> String.format("%s [%s] %+.2f%%", p.name(), p.risk(), p.variation24h()))
                    .collect(java.util.stream.Collectors.joining(", ")));
            sb.append(String.format("%n"));
        }
        sb.append(String.format("%nEscribí la recomendación para este usuario.%n"));
        return sb.toString();
    }

    // ─── Distribución por perfil ────────────────────────────────

    private List<IAResponseDTO.AllocationDTO> generateAllocation(String profile) {
        return switch (profile) {
            case "Conservador" -> List.of(
                IAResponseDTO.AllocationDTO.builder()
                    .assetType("Plazo Fijo Tradicional")
                    .percentage(80)
                    .reason("Capital garantizado con tasa regulada por BCRA")
                    .build(),
                IAResponseDTO.AllocationDTO.builder()
                    .assetType("Dólar / Cobertura")
                    .percentage(20)
                    .reason("Cobertura mínima ante devaluación")
                    .build()
            );
            case "Agresivo" -> List.of(
                IAResponseDTO.AllocationDTO.builder()
                    .assetType("Dólar / Cobertura")
                    .percentage(60)
                    .reason("Mayor exposición cambiaria buscando ganancia por devaluación")
                    .build(),
                IAResponseDTO.AllocationDTO.builder()
                    .assetType("Plazo Fijo Tradicional")
                    .percentage(40)
                    .reason("Piso de rendimiento garantizado")
                    .build()
            );
            default -> List.of( // Moderado
                IAResponseDTO.AllocationDTO.builder()
                    .assetType("Plazo Fijo Tradicional")
                    .percentage(60)
                    .reason("Base de rendimiento estable con capital garantizado")
                    .build(),
                IAResponseDTO.AllocationDTO.builder()
                    .assetType("Dólar / Cobertura")
                    .percentage(40)
                    .reason("Cobertura moderada ante escenarios cambiarios")
                    .build()
            );
        };
    }

    // ─── Ganancia ponderada ─────────────────────────────────────

    private double calculateWeightedGain(
            List<PerformanceDTO> performances,
            List<IAResponseDTO.AllocationDTO> allocation
    ) {
        double totalGain = 0;

        for (IAResponseDTO.AllocationDTO alloc : allocation) {
            double weightFraction = alloc.getPercentage() / 100.0;

            // Buscar el rendimiento que mejor matchea el tipo de activo
            PerformanceDTO performance = performances.stream()
                    .filter(r -> r.getType().toLowerCase().contains(
                            extractKeyword(alloc.getAssetType())))
                    .findFirst()
                    .orElse(performances.stream()
                            .max(Comparator.comparingDouble(PerformanceDTO::getRealMonthlyRate))
                            .orElse(null));

            if (performance != null) {
                totalGain += performance.getRealGain() * weightFraction;
            }
        }

        return totalGain;
    }

    // ─── Texto de recomendación rule-based (fallback) ───────────

    private String generateRuleBasedRecommendation(
            String profile,
            List<PerformanceDTO> performances,
            List<IAResponseDTO.AllocationDTO> allocation,
            List<CryptoState> crypto,
            List<PackQuote> packs,
            double amount,
            int months,
            double monthlyInflation,
            String assetA,
            String assetB
    ) {
        StringBuilder sb = new StringBuilder();

        // Intro personalizada por perfil
        sb.append(switch (profile) {
            case "Conservador" -> "🛡️ Como inversor conservador, tu prioridad es proteger el capital. ";
            case "Agresivo" -> "🚀 Como inversor agresivo, buscás maximizar el rendimiento aceptando mayor volatilidad. ";
            default -> "⚖️ Como inversor moderado, buscás un balance entre seguridad y rendimiento. ";
        });

        // Contexto de inflación
        sb.append(String.format("Con una inflación mensual del %.1f%%, ", monthlyInflation));

        // Análisis de activos
        long beatingAssets = performances.stream().filter(PerformanceDTO::isBeatsInflation).count();
        if (performances.isEmpty()) {
            sb.append("todavía no hay datos de mercado cargados. ");
        } else if (beatingAssets == performances.size()) {
            sb.append("todos los activos analizados le ganan a la inflación, lo cual es positivo. ");
        } else if (beatingAssets > 0) {
            sb.append(String.format("solo %d de %d activos le ganan a la inflación. ", beatingAssets, performances.size()));
        } else {
            sb.append("ningún activo analizado supera la inflación — es momento de priorizar cobertura cambiaria. ");
        }

        // Comparación A vs B si se proporcionaron
        if (assetA != null && assetB != null && !assetA.isBlank() && !assetB.isBlank()) {
            sb.append(String.format("Comparando %s contra %s: ", assetA, assetB));
            sb.append("según tu perfil, te recomendamos diversificar entre ambos siguiendo la distribución sugerida. ");
        }

        // Recomendación de distribución
        sb.append("Mi sugerencia de distribución: ");
        for (IAResponseDTO.AllocationDTO alloc : allocation) {
            sb.append(String.format("%d%% en %s, ", alloc.getPercentage(), alloc.getAssetType()));
        }

        // Cripto según perfil
        crypto.stream()
                .filter(c -> c.getSymbol().equals("BTCUSDT"))
                .findFirst()
                .ifPresent(btc -> sb.append(String.format(" Cripto: BTC en %s (%d/4 checks) — %s",
                        btc.getVerdict(), btc.getChecksPassed(),
                        switch (profile) {
                            case "Conservador" -> "demasiado volátil para tu perfil, evitá. ";
                            case "Agresivo" -> "podés considerarla si el veredicto es BUY o WATCH. ";
                            default -> "solo como cobertura chica si el veredicto no es AVOID. ";
                        })));

        // Mercados: mencionar el pack que más rinde hoy (si hay datos)
        packs.stream()
                .max(Comparator.comparingDouble(PackQuote::variation24h))
                .ifPresent(best -> sb.append(String.format(
                        " En mercados, hoy el pack %s es el que más rinde (%+.2f%%, riesgo %s). ",
                        best.name(), best.variation24h(), best.risk())));

        // Cierre
        sb.append(String.format("para un plazo de %d meses con $%,.0f.", months, amount));

        return sb.toString();
    }

    // ─── Resumen de estrategia ──────────────────────────────────

    private String generateSummary(String profile, String bestOption, double inflation) {
        return switch (profile) {
            case "Conservador" -> String.format(
                "Estrategia defensiva. Priorizar %s para capital garantizado. " +
                "Inflación mensual: %.1f%%. Foco en preservar poder adquisitivo.", bestOption, inflation);
            case "Agresivo" -> String.format(
                "Estrategia ofensiva. %s lidera en rendimiento real. " +
                "Inflación mensual: %.1f%%. Foco en maximizar ganancia real con diversificación.", bestOption, inflation);
            default -> String.format(
                "Estrategia balanceada. %s ofrece el mejor rendimiento real. " +
                "Inflación mensual: %.1f%%. Foco en equilibrar riesgo y retorno.", bestOption, inflation);
        };
    }

    // ─── Helpers ────────────────────────────────────────────────

    private String normalizeProfile(String profile) {
        if (profile == null) return "Moderado";
        return switch (profile.toLowerCase().trim()) {
            case "conservador" -> "Conservador";
            case "agresivo" -> "Agresivo";
            default -> "Moderado";
        };
    }

    private String mapRiskLevel(String profile) {
        return switch (profile) {
            case "Conservador" -> "Bajo";
            case "Agresivo" -> "Alto";
            default -> "Medio";
        };
    }

    /** Palabra clave del tipo de activo para matchear contra los rendimientos. */
    private String extractKeyword(String assetType) {
        String lower = assetType.toLowerCase();
        if (lower.contains("plazo")) return "plazo";
        if (lower.contains("billetera")) return "billetera";
        if (lower.contains("dólar") || lower.contains("dolar")) return "dólar";
        return "plazo"; // fallback conservador
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
