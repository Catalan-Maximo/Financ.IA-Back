package com.FinancIA.api.service;

import com.FinancIA.api.domain.TasaMercado;
import com.FinancIA.api.dto.ComparacionRequest;
import com.FinancIA.api.dto.ComparacionResponse;
import com.FinancIA.api.dto.RendimientoDTO;
import com.FinancIA.api.dto.VersusRequestDTO;
import com.FinancIA.api.dto.VersusResponseDTO;
import com.FinancIA.api.repository.TasaMercadoRepository;
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
public class ActivoService {

    /** Tipo de activo para la tasa BADLAR (plazos fijos de bancos privados). */
    public static final String PLAZO_FIJO = "Plazo Fijo Tradicional";

    /** Tipo de activo para el tipo de cambio minorista del BCRA. */
    public static final String DOLAR = "Dólar Oficial";

    /** Tipo de activo para las tasas de billeteras virtuales (scrapeadas). */
    public static final String BILLETERA = "Billetera Virtual";

    private final TasaMercadoRepository tasaRepository;
    private final MercadoService mercadoService;

    public ActivoService(TasaMercadoRepository tasaRepository, MercadoService mercadoService) {
        this.tasaRepository = tasaRepository;
        this.mercadoService = mercadoService;
    }

    /**
     * Devuelve los activos del mercado con datos reales persistidos en BD.
     */
    public List<Map<String, Object>> getActivos() {
        return construirActivosDesdeBD();
    }

    /**
     * VERSUS: compara dos activos cara a cara. Si ambos son proyectables
     * (acciones/ETF/cripto), la probabilidad sale del Monte Carlo con
     * escenarios emparejados; si no, el ganador es el de mayor tasa real.
     */
    public VersusResponseDTO compararVersus(VersusRequestDTO request) {
        ComparacionRequest comp = new ComparacionRequest();
        comp.setMonto(request.getMonto());
        comp.setPlazoMeses(request.getPlazoMeses());
        comp.setInflacionMensual(request.getInflacionMensual());

        List<RendimientoDTO> rendimientos = compararActivos(comp).getRendimientos();

        RendimientoDTO a = rendimientos.stream()
                .filter(r -> r.getTipo().equalsIgnoreCase(request.getEntidadA())
                        || r.getEntidad().equalsIgnoreCase(request.getEntidadA()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Activo no encontrado: " + request.getEntidadA()));
        RendimientoDTO b = rendimientos.stream()
                .filter(r -> r.getTipo().equalsIgnoreCase(request.getEntidadB())
                        || r.getEntidad().equalsIgnoreCase(request.getEntidadB()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Activo no encontrado: " + request.getEntidadB()));

        Double probabilidadA = mercadoService.probabilidadSupera(
                request.getEntidadA(), request.getEntidadB(), request.getPlazoMeses());

        if (probabilidadA != null) {
            boolean ganaA = probabilidadA >= 0.5;
            String ganador = nombreMostrable(ganaA ? a : b);
            double pct = ganaA ? probabilidadA * 100 : (1 - probabilidadA) * 100;
            String mensaje = String.format(
                    "%s gana en el %.0f%% de los 10.000 escenarios simulados. "
                    + "Ojo: en el peor 5%% puede rendir %+.1f%%.%n",
                    ganador, pct, ganaA ? a.getPeorEscenario() : b.getPeorEscenario());
            return new VersusResponseDTO(a, b, ganador, probabilidadA, mensaje);
        }

        // Al menos uno es renta fija: comparamos la tasa real mensual
        boolean ganaA = a.getTasaRealMensual() >= b.getTasaRealMensual();
        RendimientoDTO ganador = ganaA ? a : b;
        RendimientoDTO perdedor = ganaA ? b : a;
        return new VersusResponseDTO(a, b, nombreMostrable(ganador), null,
                String.format("%s le gana a %s: %+.2f%% real mensual contra %+.2f%%.",
                        nombreMostrable(ganador), nombreMostrable(perdedor),
                        ganador.getTasaRealMensual(), perdedor.getTasaRealMensual()));
    }

    /** Nombre único para mostrar: "BCRA" aparece dos veces (plazo fijo y dólar). */
    private String nombreMostrable(RendimientoDTO r) {
        return "BCRA".equals(r.getEntidad()) ? r.getTipo() : r.getEntidad();
    }

    /**
     * Calcula el rendimiento real de todos los activos del mercado
     * contra la inflación informada por el usuario.
     */
    public ComparacionResponse compararActivos(ComparacionRequest request) {
        double monto            = request.getMonto();
        int    plazoMeses       = request.getPlazoMeses();
        double inflacionMensual = request.getInflacionMensual();

        List<Map<String, Object>> activos = construirActivosDesdeBD();

        // Acciones/ETFs y cripto proyectados con Monte Carlo.
        // Si Yahoo/Binance no responden, la comparación sigue con renta fija.
        try {
            activos.addAll(mercadoService.activosProyectados(plazoMeses));
        } catch (Exception e) {
            log.warn("No se pudieron proyectar acciones/cripto: {}", e.getMessage());
        }

        List<RendimientoDTO> rendimientos = activos.stream()
                .map(activo -> calcularRendimiento(activo, monto, plazoMeses, inflacionMensual))
                .toList();

        // Determinamos cuál activo tiene la mejor tasa real mensual
        String mejorOpcion = rendimientos.stream()
                .max(Comparator.comparingDouble(RendimientoDTO::getTasaRealMensual))
                .map(RendimientoDTO::getTipo)
                .orElse("N/A");

        return ComparacionResponse.builder()
                .montoInvertido(monto)
                .plazoMeses(plazoMeses)
                .inflacionMensualUsada(inflacionMensual)
                .rendimientos(rendimientos)
                .mejorOpcion(mejorOpcion)
                .build();
    }

    // ─── Construcción de activos desde la BD ───────────────────

    private List<Map<String, Object>> construirActivosDesdeBD() {
        List<Map<String, Object>> activos = new ArrayList<>();

        // Plazo fijo: última tasa BADLAR publicada por el BCRA
        tasaRepository.findTop1ByTipoActivoOrderByFechaDesc(PLAZO_FIJO).ifPresent(tasa ->
                activos.add(Map.of("entidad", "BCRA", "tna", tasa.getValor(), "tipo", PLAZO_FIJO,
                        "riesgo", "Bajo")));

        // Dólar oficial: variación de los últimos 30 días anualizada.
        // Necesita al menos 2 registros (hoy + uno anterior) para poder
        // calcular una variación; los primeros días de datos el dólar
        // no aparece hasta que el job acumula historial.
        tasaRepository.findTop1ByTipoActivoOrderByFechaDesc(DOLAR).ifPresent(ultima -> {
            LocalDate desde = ultima.getFecha().minusDays(30);
            List<TasaMercado> historial =
                    tasaRepository.findByTipoActivoAndFechaGreaterThanEqualOrderByFechaAsc(DOLAR, desde);
            boolean hayHistorialReal = historial.size() > 1
                    && historial.get(0).getFecha().isBefore(ultima.getFecha())
                    && historial.get(0).getValor() > 0;
            if (hayHistorialReal) {
                double variacion30d = ultima.getValor() / historial.get(0).getValor() - 1;
                double tnaAnualizada = (Math.pow(1 + variacion30d, 12) - 1) * 100;
                activos.add(Map.of("entidad", "BCRA", "tna", redondear(tnaAnualizada), "tipo", DOLAR,
                        "riesgo", "Bajo"));
            }
        });

        // Billeteras virtuales: última tasa de cada entidad (scrape diario).
        // El stream viene ordenado por fecha desc — nos quedamos con la
        // primera aparición de cada entidad (la más reciente).
        Map<String, TasaMercado> ultimaPorEntidad = new LinkedHashMap<>();
        for (TasaMercado tasa : tasaRepository.findByTipoActivoOrderByFechaDesc(BILLETERA)) {
            if (BilleterasClient.EXCLUIDAS.contains(tasa.getEntidad())) continue;
            ultimaPorEntidad.putIfAbsent(tasa.getEntidad(), tasa);
        }
        for (TasaMercado tasa : ultimaPorEntidad.values()) {
            activos.add(Map.of("entidad", tasa.getEntidad(), "tna", tasa.getValor(), "tipo", BILLETERA,
                    "riesgo", "Bajo"));
        }

        // Ordenamos de mayor a menor tasa para que el dashboard muestre
        // primero las opciones más rendidoras.
        activos.sort((a, b) -> Double.compare(
                ((Number) b.get("tna")).doubleValue(),
                ((Number) a.get("tna")).doubleValue()));

        return activos;
    }

    // ─── Cálculo por activo ────────────────────────────────────

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

        // Rango Monte Carlo (solo para acciones/ETF y cripto; null en renta fija)
        Double peor = activo.containsKey("peor")
                ? ((Number) activo.get("peor")).doubleValue() : null;
        Double mejor = activo.containsKey("mejor")
                ? ((Number) activo.get("mejor")).doubleValue() : null;
        String riesgo = activo.containsKey("riesgo") ? (String) activo.get("riesgo") : "Bajo";

        return RendimientoDTO.builder()
                .entidad(entidad)
                .tipo(tipo)
                .tna(tna)
                .tasaEfectivaMensual(redondear(tem * 100))
                .tasaRealMensual(redondear(tasaReal * 100))
                .gananciaNominal(redondear(gananciaNominal))
                .gananciaReal(redondear(gananciaReal))
                .leGanaALaInflacion(tasaReal > 0)
                .peorEscenario(peor)
                .mejorEscenario(mejor)
                .riesgo(riesgo)
                .build();
    }

    /** Redondea a 2 decimales. */
    private double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
