package com.dataquality.backend.dto.ai;

import com.dataquality.backend.model.AiConversation;

import java.time.LocalDateTime;

public record AiConversationSummary(Long id, String title, LocalDateTime updatedAt) {
    public static AiConversationSummary from(AiConversation conversation) {
        return new AiConversationSummary(conversation.getId(), conversation.getTitle(), conversation.getUpdatedAt());
    }
}
