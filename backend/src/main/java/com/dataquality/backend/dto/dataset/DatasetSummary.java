package com.dataquality.backend.dto.dataset;

import com.dataquality.backend.model.Dataset;

/** Minimal nested reference used inside QualityReportResponse - avoids embedding the full Dataset entity. */
public record DatasetSummary(Long id, String fileName) {
    public static DatasetSummary from(Dataset dataset) {
        if (dataset == null) return null;
        return new DatasetSummary(dataset.getId(), dataset.getFileName());
    }
}
