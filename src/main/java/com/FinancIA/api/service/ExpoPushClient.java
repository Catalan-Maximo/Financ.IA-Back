package com.FinancIA.api.service;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Cliente de la API de push de Expo (https://exp.host/--/api/v2/push/send).
 * Envía notificaciones a los dispositivos que registraron su Expo push token.
 */
@Service
public class ExpoPushClient {

    private final RestClient restClient;

    public ExpoPushClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl("https://exp.host")
                .requestFactory(factory)
                .build();
    }

    /** Envía una notificación a un dispositivo. */
    public void enviar(String token, String titulo, String cuerpo) {
        Map<String, Object> body = Map.of(
                "to", token,
                "title", titulo,
                "body", cuerpo
        );
        restClient.post()
                .uri("/--/api/v2/push/send")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
