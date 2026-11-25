package com.dataquality.backend.dto.anomaly;

import com.dataquality.backend.model.ReportAnomaly;

public record AnomalyResponse(
        Long id,
        Integer rowIndex,
        String columnName,
        String ruleName,
        String checkType,
        String value
) {
    public static AnomalyResponse from(ReportAnomaly anomaly) {
        return new AnomalyResponse(
                anomaly.getId(),
                anomaly.getRowIndex(),
                anomaly.getColumnName(),
                anomaly.getRuleName(),
                anomaly.getCheckType(),
                anomaly.getAnomalyValue()
        );
    }
}
