package com.dataquality.backend.dto.correction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ApplyCorrectionsRequest(
        @NotEmpty @Valid List<CorrectionRequest> corrections
) {}
