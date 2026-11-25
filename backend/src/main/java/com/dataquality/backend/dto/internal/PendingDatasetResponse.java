package com.dataquality.backend.dto.internal;

import com.dataquality.backend.model.Dataset;

/** Read by the Airflow scheduled-analysis DAG to find datasets awaiting analysis. */
public record PendingDatasetResponse(Long id, String fileName, String uploadedAt) {
    public static PendingDatasetResponse from(Dataset dataset) {
        return new PendingDatasetResponse(dataset.getId(), dataset.getFileName(), dataset.getUploadedAt().toString());
    }
}
