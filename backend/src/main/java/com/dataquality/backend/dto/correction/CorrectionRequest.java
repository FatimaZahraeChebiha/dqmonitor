package com.dataquality.backend.dto.correction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CorrectionRequest(
        @NotNull Integer rowIndex,
        @NotBlank String columnName,
        String newValue
) {}
