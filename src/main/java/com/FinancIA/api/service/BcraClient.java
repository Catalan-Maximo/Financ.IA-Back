package com.FinancIA.api.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Cliente de la API pública del BCRA (https://api.bcra.gob.ar, sin autenticación).
 *
 * Usa la API de Estadísticas Monetarias v4.0:
 *   GET /estadisticas/v4.0/Monetarias/{idVariable}?Limit=N
 *
 * Series utilizadas:
 * - 7  → Tasa BADLAR (plazos fijos de bancos privados), diaria, %
 * - 4  → Tipo de cambio minorista (promedio vendedor), diario, $
 * - 27 → Inflación mensual (IPC), mensual, %
 */
@Service
public class BcraClient {

    public static final int SERIE_BADLAR = 7;
    public static final int SERIE_DOLAR_MINORISTA = 4;
    public static final int SERIE_INFLACION_MENSUAL = 27;

    private final RestClient restClient;

    public BcraClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder()
                .baseUrl("https://api.bcra.gob.ar")
                .requestFactory(factory)
                .build();
    }

    /** Un valor de una serie con su fecha de publicación. */
    public record SerieValor(LocalDate fecha, double valor) {}

    /**
     * Devuelve el último valor publicado de una serie del BCRA.
     *
     * Formato de respuesta de la API:
     * {"results":[{"idVariable":7,"detalle":[{"fecha":"2026-08-14","valor":23.31}]}]}
     */
    @SuppressWarnings("unchecked")
    public SerieValor ultimoValor(int idVariable) {
        Map<String, Object> body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/estadisticas/v4.0/Monetarias/{id}")
                        .queryParam("Limit", 1)
                        .build(idVariable))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");
        List<Map<String, Object>> detalle = (List<Map<String, Object>>) results.get(0).get("detalle");
        Map<String, Object> ultimo = detalle.get(0);

        return new SerieValor(
                LocalDate.parse((String) ultimo.get("fecha")),
                ((Number) ultimo.get("valor")).doubleValue()
        );
    }
}
