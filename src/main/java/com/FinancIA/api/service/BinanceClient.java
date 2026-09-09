package com.FinancIA.api.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Cliente de la API pública de Binance (sin autenticación).
 *
 * GET /api/v3/klines?symbol=BTCUSDT&interval=1d&limit=210
 *
 * Formato de cada kline (array de strings):
 * [openTime, open, high, low, close, volume, closeTime, ...]
 */
@Service
public class BinanceClient {

    public static final List<String> PARES = List.of("BTCUSDT", "ETHUSDT");

    private final RestClient restClient;

    public BinanceClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder()
                .baseUrl("https://api.binance.com")
                .requestFactory(factory)
                .build();
    }

    /** Una vela diaria. */
    public record Candle(long openTime, double open, double high, double low, double close, double volume) {}

    /**
     * Trae las últimas velas diarias de un par.
     * @param limit cantidad de velas (210 alcanza para SMA200 + RSI14)
     */
    public List<Candle> velasDiarias(String simbolo, int limit) {
        List<List<Object>> raw = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/klines")
                        .queryParam("symbol", simbolo)
                        .queryParam("interval", "1d")
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<List<Object>>>() {});

        // Ojo: openTime/closeTime vienen como números sin comillas, el resto como strings.
        // String.valueOf maneja ambos.
        return raw.stream().map(k -> new Candle(
                Long.parseLong(String.valueOf(k.get(0))),
                Double.parseDouble(String.valueOf(k.get(1))),
                Double.parseDouble(String.valueOf(k.get(2))),
                Double.parseDouble(String.valueOf(k.get(3))),
                Double.parseDouble(String.valueOf(k.get(4))),
                Double.parseDouble(String.valueOf(k.get(5)))
        )).toList();
    }
}
