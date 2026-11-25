package com.dataquality.backend.ai;

import java.util.List;

/**
 * Provider abstraction so the AI assistant works identically regardless of
 * which LLM actually answers the question. Ollama is the default (free, local,
 * no API key) - switching to Anthropic or OpenAI is a matter of setting
 * AI_PROVIDER (+ that provider's API key) via environment variables, no code
 * or redeploy of application logic required.
 */
public interface LlmProvider {
    String complete(String systemPrompt, List<ChatMessage> conversation);
}
