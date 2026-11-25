package com.dataquality.backend.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** One turn of prior conversation history, as sent by the frontend. */
public record AiChatMessageDto(
        @NotBlank @Pattern(regexp = "user|assistant") String role,
        @NotBlank String content
) {}
