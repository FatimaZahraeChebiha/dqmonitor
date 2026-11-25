package com.dataquality.backend.dto.internal;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Posted by the Python quality_check job to /internal/reports once analysis finishes.
 * Replaces the old design where the Python script wrote directly to MySQL with its
 * own hardcoded credentials, duplicating schema ownership with the JPA layer.
 */
public record InternalReportRequest(
        @NotNull Long datasetId,
        boolean success,
        Double qualityScore,
        Integer totalRows,
        Integer validRows,
        Integer invalidRows,
        String reportDetails,
        String errorMessage,
        List<InternalAnomalyEntry> anomalies
) {}
