package com.dataquality.backend.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiSendMessageRequest(@NotBlank @Size(max = 2000) String message) {}
