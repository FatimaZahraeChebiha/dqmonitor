package com.dataquality.backend.service;

import com.dataquality.backend.ai.ChatMessage;
import com.dataquality.backend.ai.LlmProvider;
import com.dataquality.backend.dto.ai.AiChatMessageDto;
import com.dataquality.backend.dto.ai.AiChatRequest;
import com.dataquality.backend.dto.ai.AiChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private static final String SYSTEM_PROMPT_PREFIX = """
            You are the AI assistant embedded in DQMonitor, a data quality monitoring platform \
            for Enhanced Technologies. Answer questions about the platform's datasets, quality \
            reports, and validation rules using only the context provided below. Be concise and \
            factual. If the context doesn't contain the answer, say so rather than guessing.

            """;

    private final AiContextService aiContextService;
    private final LlmProvider llmProvider;

    public AiChatResponse chat(AiChatRequest request) {
        String context = aiContextService.buildContext(request.message());
        String systemPrompt = SYSTEM_PROMPT_PREFIX + context;

        List<AiChatMessageDto> history = request.history() == null ? List.of() : request.history();
        List<ChatMessage> conversation = new ArrayList<>(history.size() + 1);
        for (AiChatMessageDto item : history) {
            conversation.add(new ChatMessage(item.role(), item.content()));
        }
        conversation.add(new ChatMessage("user", request.message()));

        String reply = llmProvider.complete(systemPrompt, conversation);
        return new AiChatResponse(reply);
    }
}
