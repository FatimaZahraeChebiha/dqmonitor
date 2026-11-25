package com.dataquality.backend.dto.anomaly;

import java.util.List;

/**
 * hasFullData is false for reports generated before the Anomaly Explorer existed
 * (the old pipeline only ever captured a capped 20-example sample per rule, and
 * that sample was never persisted per-row) - the frontend uses it to ask for a
 * re-analysis instead of presenting a silently incomplete anomaly list.
 */
public record AnomalyStatsResponse(
        long totalAnomalies,
        List<AnomalyGroupCount> byColumn,
        List<AnomalyGroupCount> byRule,
        List<AnomalyGroupCount> byType,
        AnomalyGroupCount topColumn,
        Double topColumnPct,
        boolean hasFullData
) {}
