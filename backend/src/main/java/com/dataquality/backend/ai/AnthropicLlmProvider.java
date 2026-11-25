package com.dataquality.backend.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Uses the official Anthropic Java SDK. Only instantiated when
 * app.ai.provider=anthropic (ANTHROPIC_API_KEY must also be set) - see
 * AiProviderConfig.
 */
public class AnthropicLlmProvider implements LlmProvider {

    private final AnthropicClient client;
    private final String model;

    public AnthropicLlmProvider(String apiKey, String model) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.model = model;
    }

    @Override
    public String complete(String systemPrompt, List<ChatMessage> conversation) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L)
                .system(systemPrompt);

        for (ChatMessage message : conversation) {
            MessageParam.Role role = "assistant".equals(message.role())
                    ? MessageParam.Role.ASSISTANT
                    : MessageParam.Role.USER;
            params.addMessage(MessageParam.builder().role(role).content(message.content()).build());
        }

        try {
            Message response = client.messages().create(params.build());
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(textBlock -> textBlock.text())
                    .collect(Collectors.joining());
        } catch (Exception e) {
            throw new AiProviderException("Anthropic API request failed: " + e.getMessage(), e);
        }
    }
}
