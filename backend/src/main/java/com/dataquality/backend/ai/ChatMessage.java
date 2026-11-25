package com.dataquality.backend.ai;

/** role is "user" or "assistant" - matches every provider's own wire format. */
public record ChatMessage(String role, String content) {}
