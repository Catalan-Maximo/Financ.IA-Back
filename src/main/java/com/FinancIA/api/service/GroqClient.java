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
    public boolean isConfigured() {
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
    public String generateRecommendation(String systemPrompt, String userPrompt) {
        return send(systemPrompt, List.of(Map.of("role", "user", "content", userPrompt)));
    }

    /**
     * Chat libre: recibe la historia completa de mensajes
     * ([{rol: user|assistant, contenido}]) y devuelve la respuesta.
     */
    public String chat(String systemPrompt, List<Map<String, String>> messages) {
        return send(systemPrompt, messages);
    }

    @SuppressWarnings("unchecked")
    private String send(String systemPrompt, List<Map<String, String>> messages) {
        // La API de Groq espera las claves en inglés ("role" / "content");
        // internamente usamos "rol" / "contenido" y convertimos acá.
        List<Map<String, String>> history = new java.util.ArrayList<>();
        history.add(Map.of("role", "system", "content", systemPrompt));
        for (Map<String, String> m : messages) {
            history.add(Map.of("role", m.get("role"), "content", m.get("content")));
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", history,
                "temperature", 0.7,
                "max_tokens", 600
        );

        Map<String, Object> answer = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        List<Map<String, Object>> choices = (List<Map<String, Object>>) answer.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return cleanMarkdown((String) message.get("content"));
    }

    /**
     * Groq a veces devuelve markdown (**negrita**, *cursiva*, `código`, #).
     * La app lo muestra como texto plano, así que lo limpiamos acá
     * para que nunca se vean los asteriscos.
     */
    private String cleanMarkdown(String text) {
        if (text == null) return "";
        return text
                .replaceAll("\\*\\*", "")
                .replaceAll("\\*", "")
                .replaceAll("`", "")
                .replaceAll("^#{1,6}\\s*", "")
                .replaceAll("\\n#{1,6}\\s*", "\n")
                .trim();
    }
}
