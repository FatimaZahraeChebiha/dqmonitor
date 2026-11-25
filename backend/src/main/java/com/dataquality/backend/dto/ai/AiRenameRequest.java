package com.dataquality.backend.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiRenameRequest(@NotBlank @Size(max = 120) String title) {}
