package com.FinancIA.api.service;

import com.FinancIA.api.dto.AccionCotizacion;
import com.FinancIA.api.dto.DolarCotizacion;
import com.FinancIA.api.dto.PackCotizacion;
import com.FinancIA.api.domain.PackComposicion;
import com.FinancIA.api.repository.PackComposicionRepository;
import com.FinancIA.api.repository.PackInversionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lógica de mercados: acciones con cotización real de Yahoo Finance,
 * cotizaciones del dólar (DolarAPI) y packs con variación ponderada
 * según su composición en la BD.
 */
@Service
@Slf4j
public class MercadoService {

    /** Tickers que mostramos en la sección Acciones. */
    public static final List<String> TICKERS = List.of("AAPL", "MSFT", "SPY", "YPF", "GGAL");

    private final YahooFinanceClient yahooClient;
    private final DolarApiClient dolarApiClient;
    private final BinanceClient binanceClient;
    private final MonteCarloService monteCarloService;
    private final PackInversionRepository packRepository;
    private final PackComposicionRepository composicionRepository;

    public MercadoService(YahooFinanceClient yahooClient,
                          DolarApiClient dolarApiClient,
                          BinanceClient binanceClient,
                          MonteCarloService monteCarloService,
                          PackInversionRepository packRepository,
                          PackComposicionRepository composicionRepository) {
        this.yahooClient = yahooClient;
        this.dolarApiClient = dolarApiClient;
        this.binanceClient = binanceClient;
        this.monteCarloService = monteCarloService;
        this.packRepository = packRepository;
        this.composicionRepository = composicionRepository;
    }

    /** Cotizaciones de las acciones/ETFs con variación de 24h. */
    public List<AccionCotizacion> acciones() {
        return TICKERS.stream()
                .map(this::cotizacionSegura)
                .toList();
    }

    /** Cotizaciones del dólar (DolarAPI). */
    public List<DolarCotizacion> dolar() {
        return dolarApiClient.cotizaciones();
    }

    /**
     * Activos proyectados para el simulador con Monte Carlo: acciones/ETFs
     * (Yahoo) y cripto (Binance) usando su volatilidad histórica real.
     * Devuelve el mismo shape que ActivoService más dos claves extra:
     * "peor" y "mejor" = percentiles 5/95 del retorno total del horizonte.
     */
    public List<Map<String, Object>> activosProyectados(int plazoMeses) {
        List<Map<String, Object>> activos = new java.util.ArrayList<>();

        for (String ticker : TICKERS) {
            try {
                activos.add(proyectar(ticker, "Acción / ETF", closesYahoo(ticker), plazoMeses));
            } catch (Exception e) {
                log.warn("No se pudo proyectar {}: {}", ticker, e.getMessage());
            }
        }

        for (String par : BinanceClient.PARES) {
            try {
                List<Double> closes = binanceClient.velasDiarias(par, 90).stream()
                        .map(BinanceClient.Candle::close)
                        .toList();
                String nombre = par.equals("BTCUSDT") ? "Bitcoin" : "Ethereum";
                activos.add(proyectar(nombre, "Cripto", closes, plazoMeses));
            } catch (Exception e) {
                log.warn("No se pudo proyectar {}: {}", par, e.getMessage());
            }
        }

        return activos;
    }

    /** Cierres de 3 meses de Yahoo para un ticker. */
    private List<Double> closesYahoo(String ticker) {
        return yahooClient.velas3m(ticker);
    }

    /**
     * Probabilidad de que el activo A supere a B (Monte Carlo, escenarios
     * emparejados). Null si alguno de los dos no es proyectable (renta fija).
     */
    public Double probabilidadSupera(String entidadA, String entidadB, int meses) {
        List<Double> closesA = closesDe(entidadA);
        List<Double> closesB = closesDe(entidadB);
        if (closesA.size() < 2 || closesB.size() < 2) return null;
        return monteCarloService.probabilidadSupera(closesA, closesB, meses);
    }

    /** Precio actual de un activo (acciones/ETF y cripto); null para renta fija. */
    public Double precioActual(String activo) {
        if (TICKERS.contains(activo)) {
            try {
                return yahooClient.cotizacion(activo).precio();
            } catch (Exception e) {
                log.warn("Sin precio para {}: {}", activo, e.getMessage());
                return null;
            }
        }
        String par = activo.equalsIgnoreCase("Bitcoin") ? "BTCUSDT"
                : activo.equalsIgnoreCase("Ethereum") ? "ETHUSDT" : null;
        if (par != null) {
            try {
                List<Double> closes = binanceClient.velasDiarias(par, 2).stream()
                        .map(BinanceClient.Candle::close)
                        .toList();
                return closes.isEmpty() ? null : closes.get(closes.size() - 1);
            } catch (Exception e) {
                log.warn("Sin precio para {}: {}", activo, e.getMessage());
                return null;
            }
        }
        return null;
    }

    /** Cierres de 3 meses para un ticker de Yahoo o un par cripto (por nombre). */
    public List<Double> closesDe(String entidad) {
        if (TICKERS.contains(entidad)) {
            try {
                return yahooClient.velas3m(entidad);
            } catch (Exception e) {
                log.warn("Sin cierres para {}: {}", entidad, e.getMessage());
                return List.of();
            }
        }
        String par = entidad.equalsIgnoreCase("Bitcoin") ? "BTCUSDT"
                : entidad.equalsIgnoreCase("Ethereum") ? "ETHUSDT" : null;
        if (par != null) {
            try {
                return binanceClient.velasDiarias(par, 90).stream()
                        .map(BinanceClient.Candle::close)
                        .toList();
            } catch (Exception e) {
                log.warn("Sin cierres para {}: {}", entidad, e.getMessage());
                return List.of();
            }
        }
        return List.of(); // renta fija: no proyectable
    }

    /** Corre Monte Carlo y arma el mapa con el shape del simulador. */
    private Map<String, Object> proyectar(String entidad, String tipo,
                                          List<Double> closes, int plazoMeses) {
        if (closes.size() < 2) return Map.of("entidad", entidad, "tna", 0.0, "tipo", tipo);

        MonteCarloService.Proyeccion p = monteCarloService.simular(closes, plazoMeses);
        // TNA equivalente a la mediana mensual proyectada (para las fórmulas del simulador)
        double tna = (Math.pow(1 + p.medianaMensualPct() / 100.0, 365.0 / 30.0) - 1) * 100;

        return Map.of(
                "entidad", entidad,
                "tna", redondear(tna),
                "tipo", tipo,
                "peor", redondear(p.p5TotalPct()),
                "mejor", redondear(p.p95TotalPct()),
                "riesgo", riesgoPorVolatilidad(monteCarloService.volatilidadMensualPct(closes))
        );
    }

    /** Buckets de riesgo según volatilidad mensual histórica (%). */
    public static String riesgoPorVolatilidad(double volMensualPct) {
        if (volMensualPct < 3) return "Bajo";
        if (volMensualPct < 8) return "Medio";
        if (volMensualPct < 20) return "Alto";
        return "Extremo";
    }

    /**
     * Packs con variación ponderada: la variación de cada ticker
     * (una sola consulta por ticker, cacheada) multiplicada por su peso.
     */
    public List<PackCotizacion> packs() {
        Map<String, Double> variaciones = new HashMap<>();

        return packRepository.findAll().stream().map(pack -> {
            List<PackComposicion> composicion = composicionRepository.findByPackId(pack.getId());

            double variacion = 0;
            for (PackComposicion item : composicion) {
                double variacionTicker = variaciones.computeIfAbsent(item.getTicker(), this::variacionSegura);
                variacion += variacionTicker * item.getPorcentajePeso() / 100.0;
            }

            return new PackCotizacion(
                    pack.getId(),
                    pack.getNombre(),
                    pack.getRiesgo(),
                    redondear(variacion),
                    composicion.stream()
                            .map(c -> new PackCotizacion.ComposicionItem(c.getTicker(), c.getPorcentajePeso()))
                            .toList()
            );
        }).toList();
    }

    /** Consulta Yahoo sin romper la lista si un ticker falla. */
    private AccionCotizacion cotizacionSegura(String ticker) {
        try {
            return yahooClient.cotizacion(ticker);
        } catch (Exception e) {
            log.warn("No se pudo cotizar {}: {}", ticker, e.getMessage());
            return new AccionCotizacion(ticker, 0, 0);
        }
    }

    private double variacionSegura(String ticker) {
        return cotizacionSegura(ticker).variacion24h();
    }

    private double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
