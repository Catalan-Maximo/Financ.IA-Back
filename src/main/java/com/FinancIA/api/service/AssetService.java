package com.FinancIA.api.service;

import com.FinancIA.api.domain.MarketRate;
import com.FinancIA.api.dto.ComparisonRequest;
import com.FinancIA.api.dto.ComparisonResponse;
import com.FinancIA.api.dto.PerformanceDTO;
import com.FinancIA.api.dto.VersusRequestDTO;
import com.FinancIA.api.dto.VersusResponseDTO;
import com.FinancIA.api.repository.MarketRateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de cálculo financiero para activos de inversión.
 *
 * Los datos de mercado se leen de la base de datos, poblada por el
 * {@link MercadoDataJob} con valores reales del BCRA.
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
@Slf4j
public class AssetService {

    /** Tipo de activo para la tasa BADLAR (plazos fijos de bancos privados). */
    public static final String FIXED_TERM_DEPOSIT = "Plazo Fijo Tradicional";

    /** Tipo de activo para el tipo de cambio minorista del BCRA. */
    public static final String DOLLAR = "Dólar Oficial";

    /** Tipo de activo para las tasas de billeteras virtuales (scrapeadas). */
    public static final String WALLET = "Billetera Virtual";

    private final MarketRateRepository rateRepository;
    private final MarketService marketService;

    public AssetService(MarketRateRepository rateRepository, MarketService marketService) {
        this.rateRepository = rateRepository;
        this.marketService = marketService;
    }

    /**
     * Devuelve los activos del mercado con datos reales persistidos en BD.
     */
    public List<Map<String, Object>> getAssets() {
        return buildAssetsFromDb();
    }

    /**
     * VERSUS: compara dos activos cara a cara. Si ambos son proyectables
     * (acciones/ETF/cripto), la probabilidad sale del Monte Carlo con
     * escenarios emparejados; si no, el ganador es el de mayor tasa real.
     */
    public VersusResponseDTO compareVersus(VersusRequestDTO request) {
        ComparisonRequest comp = new ComparisonRequest();
        comp.setAmount(request.getAmount());
        comp.setMonths(request.getMonths());
        comp.setMonthlyInflation(request.getMonthlyInflation());

        List<PerformanceDTO> performances = compareAssets(comp).getPerformances();

        PerformanceDTO a = performances.stream()
                .filter(r -> r.getType().equalsIgnoreCase(request.getAssetA())
                        || r.getEntity().equalsIgnoreCase(request.getAssetA()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Activo no encontrado: " + request.getAssetA()));
        PerformanceDTO b = performances.stream()
                .filter(r -> r.getType().equalsIgnoreCase(request.getAssetB())
                        || r.getEntity().equalsIgnoreCase(request.getAssetB()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Activo no encontrado: " + request.getAssetB()));

        Double probabilityA = marketService.probabilityOfOutperforming(
                request.getAssetA(), request.getAssetB(), request.getMonths());

        if (probabilityA != null) {
            boolean aWins = probabilityA >= 0.5;
            String winner = displayName(aWins ? a : b);
            double pct = aWins ? probabilityA * 100 : (1 - probabilityA) * 100;
            String message = String.format(
                    "%s gana en el %.0f%% de los 10.000 escenarios simulados. "
                    + "Ojo: en el peor 5%% puede rendir %+.1f%%.%n",
                    winner, pct, aWins ? a.getWorstCase() : b.getWorstCase());
            return new VersusResponseDTO(a, b, winner, probabilityA, message);
        }

        // Al menos uno es renta fija: comparamos la tasa real mensual
        boolean aWins = a.getRealMonthlyRate() >= b.getRealMonthlyRate();
        PerformanceDTO winner = aWins ? a : b;
        PerformanceDTO loser = aWins ? b : a;
        return new VersusResponseDTO(a, b, displayName(winner), null,
                String.format("%s le gana a %s: %+.2f%% real mensual contra %+.2f%%.",
                        displayName(winner), displayName(loser),
                        winner.getRealMonthlyRate(), loser.getRealMonthlyRate()));
    }

    /** Nombre único para mostrar: "BCRA" aparece dos veces (plazo fijo y dólar). */
    private String displayName(PerformanceDTO r) {
        return "BCRA".equals(r.getEntity()) ? r.getType() : r.getEntity();
    }

    /**
     * Calcula el rendimiento real de todos los activos del mercado
     * contra la inflación informada por el usuario.
     */
    public ComparisonResponse compareAssets(ComparisonRequest request) {
        double amount            = request.getAmount();
        int    months       = request.getMonths();
        double monthlyInflation = request.getMonthlyInflation();

        List<Map<String, Object>> assets = buildAssetsFromDb();

        // Acciones/ETFs y cripto proyectados con Monte Carlo.
        // Si Yahoo/Binance no responden, la comparación sigue con renta fija.
        try {
            assets.addAll(marketService.projectedAssets(months));
        } catch (Exception e) {
            log.warn("No se pudieron proyectar acciones/cripto: {}", e.getMessage());
        }

        List<PerformanceDTO> performances = assets.stream()
                .map(asset -> calculatePerformance(asset, amount, months, monthlyInflation))
                .toList();

        // Determinamos cuál activo tiene la mejor tasa real mensual
        String bestOption = performances.stream()
                .max(Comparator.comparingDouble(PerformanceDTO::getRealMonthlyRate))
                .map(PerformanceDTO::getType)
                .orElse("N/A");

        return ComparisonResponse.builder()
                .investedAmount(amount)
                .months(months)
                .monthlyInflationUsed(monthlyInflation)
                .performances(performances)
                .bestOption(bestOption)
                .build();
    }

    // ─── Construcción de activos desde la BD ───────────────────

    private List<Map<String, Object>> buildAssetsFromDb() {
        List<Map<String, Object>> assets = new ArrayList<>();

        // Plazo fijo: última tasa BADLAR publicada por el BCRA
        rateRepository.findTop1ByAssetTypeOrderByDateDesc(FIXED_TERM_DEPOSIT).ifPresent(rate ->
                assets.add(Map.of("entity", "BCRA", "tna", rate.getValue(), "type", FIXED_TERM_DEPOSIT,
                        "risk", "Bajo")));

        // Dólar oficial: variación de los últimos 30 días anualizada.
        // Necesita al menos 2 registros (hoy + uno anterior) para poder
        // calcular una variación; los primeros días de datos el dólar
        // no aparece hasta que el job acumula historial.
        rateRepository.findTop1ByAssetTypeOrderByDateDesc(DOLLAR).ifPresent(latest -> {
            LocalDate since = latest.getDate().minusDays(30);
            List<MarketRate> history =
                    rateRepository.findByAssetTypeAndDateGreaterThanEqualOrderByDateAsc(DOLLAR, since);
            boolean hasRealHistory = history.size() > 1
                    && history.get(0).getDate().isBefore(latest.getDate())
                    && history.get(0).getValue() > 0;
            if (hasRealHistory) {
                double variation30d = latest.getValue() / history.get(0).getValue() - 1;
                double annualizedTna = (Math.pow(1 + variation30d, 12) - 1) * 100;
                assets.add(Map.of("entity", "BCRA", "tna", round2(annualizedTna), "type", DOLLAR,
                        "risk", "Bajo"));
            }
        });

        // Billeteras virtuales: última tasa de cada entidad (scrape diario).
        // El stream viene ordenado por fecha desc — nos quedamos con la
        // primera aparición de cada entidad (la más reciente).
        Map<String, MarketRate> latestByEntity = new LinkedHashMap<>();
        for (MarketRate rate : rateRepository.findByAssetTypeOrderByDateDesc(WALLET)) {
            if (WalletsClient.EXCLUDED.contains(rate.getEntity())) continue;
            latestByEntity.putIfAbsent(rate.getEntity(), rate);
        }
        for (MarketRate rate : latestByEntity.values()) {
            assets.add(Map.of("entity", rate.getEntity(), "tna", rate.getValue(), "type", WALLET,
                    "risk", "Bajo"));
        }

        // Ordenamos de mayor a menor tasa para que el dashboard muestre
        // primero las opciones más rendidoras.
        assets.sort((a, b) -> Double.compare(
                ((Number) b.get("tna")).doubleValue(),
                ((Number) a.get("tna")).doubleValue()));

        return assets;
    }

    // ─── Cálculo por activo ────────────────────────────────────

    /**
     * Calcula las métricas de rendimiento para un activo individual.
     */
    private PerformanceDTO calculatePerformance(
            Map<String, Object> asset,
            double amount,
            int months,
            double monthlyInflation
    ) {
        String entity = (String) asset.get("entity");
        String type    = (String) asset.get("type");
        double tna     = ((Number) asset.get("tna")).doubleValue();

        // Tasa Efectiva Mensual: TEM = (1 + TNA/100)^(30/365) - 1
        double monthlyRate = Math.pow(1 + tna / 100.0, 30.0 / 365.0) - 1;

        // Tasa Real Mensual (ecuación de Fisher):
        // TasaReal = ((1 + TEM) / (1 + inflación/100)) - 1
        double realRate = ((1 + monthlyRate) / (1 + monthlyInflation / 100.0)) - 1;

        // Ganancia Nominal: monto × (1 + TEM)^plazo - monto
        double nominalGain = amount * Math.pow(1 + monthlyRate, months) - amount;

        // Ganancia Real: monto × (1 + TasaReal)^plazo - monto
        double realGain = amount * Math.pow(1 + realRate, months) - amount;

        // Rango Monte Carlo (solo para acciones/ETF y cripto; null en renta fija)
        Double worst = asset.containsKey("worst")
                ? ((Number) asset.get("worst")).doubleValue() : null;
        Double best = asset.containsKey("best")
                ? ((Number) asset.get("best")).doubleValue() : null;
        String risk = asset.containsKey("risk") ? (String) asset.get("risk") : "Bajo";

        return PerformanceDTO.builder()
                .entity(entity)
                .type(type)
                .tna(tna)
                .effectiveMonthlyRate(round2(monthlyRate * 100))
                .realMonthlyRate(round2(realRate * 100))
                .nominalGain(round2(nominalGain))
                .realGain(round2(realGain))
                .beatsInflation(realRate > 0)
                .worstCase(worst)
                .bestCase(best)
                .risk(risk)
                .build();
    }

    /** Redondea a 2 decimales. */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
