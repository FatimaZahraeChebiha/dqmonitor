package com.dataquality.backend.dto.internal;

/** One real per-row rule violation, posted by the Python quality_check job alongside the report. */
public record InternalAnomalyEntry(
        Integer rowIndex,
        String column,
        String ruleName,
        String checkType,
        String value
) {}
