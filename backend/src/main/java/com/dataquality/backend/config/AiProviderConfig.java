package com.dataquality.backend.config;

import com.dataquality.backend.ai.AnthropicLlmProvider;
import com.dataquality.backend.ai.LlmProvider;
import com.dataquality.backend.ai.OllamaLlmProvider;
import com.dataquality.backend.ai.OpenAiLlmProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the active LlmProvider from a single env var (AI_PROVIDER) so the
 * app can move from free local development (Ollama, the default) to a hosted
 * provider in production by changing environment variables only - no code
 * change, no redeploy of application logic.
 */
@Configuration
public class AiProviderConfig {

    @Bean
    public LlmProvider llmProvider(
            @Value("${app.ai.provider:ollama}") String provider,
            @Value("${app.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${app.ai.ollama.model:llama3.2}") String ollamaModel,
            @Value("${app.ai.anthropic.api-key:}") String anthropicApiKey,
            @Value("${app.ai.anthropic.model:claude-opus-4-8}") String anthropicModel,
            @Value("${app.ai.openai.api-key:}") String openAiApiKey,
            @Value("${app.ai.openai.model:gpt-4o-mini}") String openAiModel
    ) {
        return switch (provider.toLowerCase()) {
            case "anthropic" -> new AnthropicLlmProvider(anthropicApiKey, anthropicModel);
            case "openai" -> new OpenAiLlmProvider(openAiApiKey, openAiModel);
            default -> new OllamaLlmProvider(ollamaBaseUrl, ollamaModel);
        };
    }
}
