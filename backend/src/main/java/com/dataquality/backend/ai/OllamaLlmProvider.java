package com.dataquality.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls a local Ollama server's /api/chat endpoint - the default provider so
 * the assistant works free, offline, and without any API key during
 * development. Uses SimpleClientHttpRequestFactory rather than the JDK
 * HttpClient default: the same "Expect: 100-continue" issue found earlier with
 * the data-processing service (see DatasetService) applies to any local
 * server that doesn't answer that handshake, so this avoids it up front
 * rather than waiting to hit it again.
 */
public class OllamaLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmProvider.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final String model;

    public OllamaLlmProvider(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.restClient = RestClient.builder()
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }

    @Override
    public String complete(String systemPrompt, List<ChatMessage> conversation) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (ChatMessage message : conversation) {
            messages.add(Map.of("role", message.role(), "content", message.content()));
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
                "stream", false
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri(baseUrl + "/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null || !(response.get("message") instanceof Map<?, ?> messageMap)) {
                throw new AiProviderException("Ollama returned an unexpected response shape", null);
            }
            Object content = messageMap.get("content");
            return content != null ? content.toString() : "";
        } catch (RestClientException e) {
            log.error("Ollama request failed at {}", baseUrl, e);
            throw new AiProviderException(
                    "The local AI model (Ollama) is not reachable at " + baseUrl
                            + ". Make sure Ollama is running and the model \"" + model + "\" is pulled.",
                    e
            );
        }
    }
}
