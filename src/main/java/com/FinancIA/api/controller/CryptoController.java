package com.FinancIA.api.controller;

import com.FinancIA.api.domain.CryptoState;
import com.FinancIA.api.repository.CryptoStateRepository;
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
@RequestMapping("/api/v1/crypto")
public class CryptoController {

    private final CryptoStateRepository repository;
    private final BinanceClient binanceClient;

    public CryptoController(CryptoStateRepository repository, BinanceClient binanceClient) {
        this.repository = repository;
        this.binanceClient = binanceClient;
    }

    @GetMapping("/state")
    public List<CryptoState> getState() {
        return BinanceClient.PAIRS.stream()
                .map(par -> repository.findTop1BySymbolOrderByDateDesc(par))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    /**
     * GET /cripto/velas/{simbolo}?dias=30 — cierres diarios para el gráfico.
     * Se consultan on demand a Binance (no se guardan en BD).
     */
    @GetMapping("/candles/{symbol}")
    public List<Map<String, Object>> getCandles(@PathVariable String symbol,
                                                  @RequestParam(defaultValue = "30") int days) {
        return binanceClient.dailyCandles(symbol, days).stream()
                .map(v -> Map.<String, Object>of(
                        "date", Instant.ofEpochMilli(v.openTime())
                                .atZone(ZoneId.of("UTC")).toLocalDate().toString(),
                        "close", v.close()
                ))
                .toList();
    }

    /**
     * Compara el veredicto de hoy contra el del día anterior y emite
     * una señal cuando cambió (ej. de WATCH a BUY → compra; a AVOID → vender/cuidado).
     */
    @GetMapping("/alerts")
    public List<Map<String, String>> getAlerts() {
        List<Map<String, String>> alerts = new ArrayList<>();
        for (String par : BinanceClient.PAIRS) {
            List<CryptoState> latest = repository.findTop2BySymbolOrderByDateDesc(par);
            if (latest.size() < 2) continue;

            String today = latest.get(0).getVerdict();
            String yesterday = latest.get(1).getVerdict();
            if (!today.equals(yesterday)) {
                alerts.add(Map.of(
                        "symbol", par,
                        "currentVerdict", today,
                        "previousVerdict", yesterday,
                        "message", par.replace("USDT", "") + " cambió de " + yesterday + " a " + today
                                + (today.equals("BUY") ? " — señal de compra."
                                  : today.equals("AVOID") ? " — señal de venta/cuidado."
                                  : " — señal de precaución.")
                ));
            }
        }
        return alerts;
    }
}
