package com.dataquality.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls OpenAI's Chat Completions API directly over HTTP. Only instantiated
 * when app.ai.provider=openai (OPENAI_API_KEY must also be set) - see
 * AiProviderConfig.
 */
public class OpenAiLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmProvider.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiLlmProvider(String apiKey, String model) {
        this.apiKey = apiKey;
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
                "messages", messages
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri(CHAT_COMPLETIONS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
                throw new AiProviderException("OpenAI returned an unexpected response shape", null);
            }
            Object first = choices.get(0);
            if (first instanceof Map<?, ?> choice && choice.get("message") instanceof Map<?, ?> messageMap) {
                Object content = messageMap.get("content");
                return content != null ? content.toString() : "";
            }
            throw new AiProviderException("OpenAI returned an unexpected response shape", null);
        } catch (RestClientException e) {
            log.error("OpenAI request failed", e);
            throw new AiProviderException("OpenAI API request failed: " + e.getMessage(), e);
        }
    }
}
