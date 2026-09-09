package com.FinancIA.api.controller;

import com.FinancIA.api.domain.CriptoEstado;
import com.FinancIA.api.repository.CriptoEstadoRepository;
import com.FinancIA.api.service.BinanceClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/cripto/estado — último snapshot del análisis técnico de BTC y ETH.
 * GET /api/v1/cripto/alertas — señales: cambios de veredicto respecto del día anterior.
 * Protegidos por JWT como el resto de los endpoints.
 */
@RestController
@RequestMapping("/api/v1/cripto")
public class CriptoController {

    private final CriptoEstadoRepository repository;
    private final BinanceClient binanceClient;

    public CriptoController(CriptoEstadoRepository repository, BinanceClient binanceClient) {
        this.repository = repository;
        this.binanceClient = binanceClient;
    }

    @GetMapping("/estado")
    public List<CriptoEstado> obtenerEstado() {
        return BinanceClient.PARES.stream()
                .map(par -> repository.findTop1BySimboloOrderByFechaDesc(par))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    /**
     * GET /cripto/velas/{simbolo}?dias=30 — cierres diarios para el gráfico.
     * Se consultan on demand a Binance (no se guardan en BD).
     */
    @GetMapping("/velas/{simbolo}")
    public List<Map<String, Object>> obtenerVelas(@PathVariable String simbolo,
                                                  @RequestParam(defaultValue = "30") int dias) {
        return binanceClient.velasDiarias(simbolo, dias).stream()
                .map(v -> Map.<String, Object>of(
                        "fecha", Instant.ofEpochMilli(v.openTime())
                                .atZone(ZoneId.of("UTC")).toLocalDate().toString(),
                        "cierre", v.close()
                ))
                .toList();
    }

    /**
     * Compara el veredicto de hoy contra el del día anterior y emite
     * una señal cuando cambió (ej. de WATCH a BUY → compra; a AVOID → vender/cuidado).
     */
    @GetMapping("/alertas")
    public List<Map<String, String>> obtenerAlertas() {
        List<Map<String, String>> alertas = new ArrayList<>();
        for (String par : BinanceClient.PARES) {
            List<CriptoEstado> ultimos = repository.findTop2BySimboloOrderByFechaDesc(par);
            if (ultimos.size() < 2) continue;

            String hoy = ultimos.get(0).getVeredicto();
            String ayer = ultimos.get(1).getVeredicto();
            if (!hoy.equals(ayer)) {
                alertas.add(Map.of(
                        "simbolo", par,
                        "veredictoActual", hoy,
                        "veredictoAnterior", ayer,
                        "mensaje", par.replace("USDT", "") + " cambió de " + ayer + " a " + hoy
                                + (hoy.equals("BUY") ? " — señal de compra."
                                  : hoy.equals("AVOID") ? " — señal de venta/cuidado."
                                  : " — señal de precaución.")
                ));
            }
        }
        return alertas;
    }
}
