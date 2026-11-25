package com.dataquality.backend.dto.report;

import com.dataquality.backend.dto.dataset.DatasetSummary;
import com.dataquality.backend.model.QualityReport;

import java.time.LocalDateTime;

public record QualityReportResponse(
        Long id,
        DatasetSummary dataset,
        Double qualityScore,
        Integer totalRows,
        Integer validRows,
        Integer invalidRows,
        String status,
        LocalDateTime generatedAt,
        String reportDetails
) {
    public static QualityReportResponse from(QualityReport report) {
        return new QualityReportResponse(
                report.getId(),
                DatasetSummary.from(report.getDataset()),
                report.getQualityScore(),
                report.getTotalRows(),
                report.getValidRows(),
                report.getInvalidRows(),
                report.getStatus(),
                report.getGeneratedAt(),
                report.getReportDetails()
        );
    }
}
