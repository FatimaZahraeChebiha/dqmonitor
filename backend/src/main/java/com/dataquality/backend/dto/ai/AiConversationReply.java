package com.dataquality.backend.dto.ai;

/** The assistant's answer plus the conversation's title, which the first message may have just set. */
public record AiConversationReply(String reply, String title) {}
