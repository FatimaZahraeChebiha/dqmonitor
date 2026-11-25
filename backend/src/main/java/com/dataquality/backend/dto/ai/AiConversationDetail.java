package com.dataquality.backend.dto.ai;

import java.util.List;

public record AiConversationDetail(Long id, String title, List<AiChatMessageDto> messages) {}
