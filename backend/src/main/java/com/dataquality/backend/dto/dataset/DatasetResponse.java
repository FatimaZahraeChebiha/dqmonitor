package com.dataquality.backend.dto.dataset;

import com.dataquality.backend.model.Dataset;
import com.dataquality.backend.model.DatasetStatus;

import java.time.LocalDateTime;

public record DatasetResponse(
        Long id,
        String fileName,
        Long fileSize,
        DatasetStatus status,
        String uploadedByUsername,
        LocalDateTime uploadedAt,
        boolean hasCorrectedFile,
        LocalDateTime lastCorrectedAt
) {
    public static DatasetResponse from(Dataset dataset) {
        return new DatasetResponse(
                dataset.getId(),
                dataset.getFileName(),
                dataset.getFileSize(),
                dataset.getStatus(),
                dataset.getUploadedBy() != null ? dataset.getUploadedBy().getUsername() : null,
                dataset.getUploadedAt(),
                dataset.getCorrectedFilePath() != null,
                dataset.getLastCorrectedAt()
        );
    }
}
