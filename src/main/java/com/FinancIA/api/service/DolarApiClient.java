package com.FinancIA.api.service;

import com.FinancIA.api.dto.DolarCotizacion;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cliente de DolarAPI (https://dolarapi.com) — cotizaciones del dólar
 * en Argentina: Oficial, Blue, MEP (Bolsa), CCL, Cripto, etc.
 * API pública y gratuita, sin autenticación.
 */
@Service
public class DolarApiClient {

    private final RestClient restClient;

    public DolarApiClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder()
                .baseUrl("https://dolarapi.com")
                .requestFactory(factory)
                .build();
    }

    /** Todas las cotizaciones disponibles (Oficial, Blue, MEP, CCL...). */
    public List<DolarCotizacion> cotizaciones() {
        List<Map<String, Object>> raw = restClient.get()
                .uri("/v1/dolares")
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        return raw.stream().map(c -> new DolarCotizacion(
                (String) c.get("nombre"),
                ((Number) c.get("compra")).doubleValue(),
                ((Number) c.get("venta")).doubleValue(),
                String.valueOf(c.get("fechaActualizacion")).substring(0, 10)
        )).toList();
    }
}
