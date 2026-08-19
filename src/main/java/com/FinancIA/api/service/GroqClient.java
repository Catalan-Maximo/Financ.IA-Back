package com.FinancIA.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cliente de la API de Groq (https://api.groq.com, compatible con OpenAI).
 *
 * Usa el modelo gratuito llama-3.3-70b-versatile.
 * La API key se configura por variable de entorno GROQ_API_KEY —
 * si no está definida, {@link #estaConfigurado()} devuelve false y
 * el {@link IAService} usa su lógica rule-based como fallback.
 */
@Service
public class GroqClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GroqClient(
            @Value("${groq.api.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${groq.api.key:}") String apiKey,
            @Value("${groq.api.model:openai/gpt-oss-120b}") String model
    ) {
        this.apiKey = apiKey;
        this.model = model;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    /** true si hay API key configurada (si no, IAService usa fallback). */
    public boolean estaConfigurado() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Genera un texto de recomendación llamando al LLM de Groq.
     *
     * @param systemPrompt instrucciones de comportamiento del asistente
     * @param userPrompt   contexto con los datos calculados (rendimientos,
     *                     distribución, perfil, monto, plazo, inflación)
     * @return el texto generado por el modelo
     */
    @SuppressWarnings("unchecked")
    public String generarRecomendacion(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.7,
                "max_tokens", 600
        );

        Map<String, Object> respuesta = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        List<Map<String, Object>> choices = (List<Map<String, Object>>) respuesta.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }
}
