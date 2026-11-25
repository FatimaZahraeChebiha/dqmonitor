package com.dataquality.backend.service;

import com.dataquality.backend.ai.ChatMessage;
import com.dataquality.backend.ai.LlmProvider;
import com.dataquality.backend.dto.ai.AiChatMessageDto;
import com.dataquality.backend.dto.ai.AiChatRequest;
import com.dataquality.backend.dto.ai.AiChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTest {

    @Mock private AiContextService aiContextService;
    @Mock private LlmProvider llmProvider;

    @InjectMocks
    private AiAssistantService aiAssistantService;

    @BeforeEach
    void setUp() {
        when(aiContextService.buildContext(anyString())).thenReturn("Platform overview: average quality score 87.5%...");
    }

    @Test
    void includesRetrievedContextInTheSystemPromptAndReturnsTheProviderReply() {
        when(llmProvider.complete(anyString(), eq(List.of(new ChatMessage("user", "How is quality overall?")))))
                .thenReturn("Quality looks good overall.");

        AiChatResponse response = aiAssistantService.chat(new AiChatRequest("How is quality overall?", null));

        assertThat(response.reply()).isEqualTo("Quality looks good overall.");

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmProvider).complete(systemPromptCaptor.capture(), eq(List.of(new ChatMessage("user", "How is quality overall?"))));
        assertThat(systemPromptCaptor.getValue()).contains("Platform overview: average quality score 87.5%");
    }

    @Test
    void appendsPriorHistoryBeforeTheNewUserMessage() {
        List<AiChatMessageDto> history = List.of(
                new AiChatMessageDto("user", "What is DQMonitor?"),
                new AiChatMessageDto("assistant", "It's a data quality platform.")
        );
        when(llmProvider.complete(anyString(), eq(List.of(
                new ChatMessage("user", "What is DQMonitor?"),
                new ChatMessage("assistant", "It's a data quality platform."),
                new ChatMessage("user", "Anything else I should know?")
        )))).thenReturn("Not much else for now.");

        AiChatResponse response = aiAssistantService.chat(new AiChatRequest("Anything else I should know?", history));

        assertThat(response.reply()).isEqualTo("Not much else for now.");
    }
}
