package com.dataquality.backend.dto.dashboard;

import java.time.LocalDate;

/**
 * Number of analyses run (every report, including re-analyses - unlike the
 * rest of the dashboard this intentionally counts activity, not distinct
 * datasets) in the week starting weekStart. A week with no analyses is a real
 * zero, not a missing data point, so the series has no gaps.
 */
public record AnalysisVolumePoint(LocalDate weekStart, long count) {}
