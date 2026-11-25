package com.dataquality.backend.dto.internal;

/**
 * Posted by the backend to the data-processing FastAPI service's POST /analyze
 * when ANALYSIS_SERVICE_URL is configured (Docker), instead of spawning
 * quality_check.py directly via ProcessBuilder.
 */
public record AnalyzeJobRequest(Long datasetId, String filePath) {}
