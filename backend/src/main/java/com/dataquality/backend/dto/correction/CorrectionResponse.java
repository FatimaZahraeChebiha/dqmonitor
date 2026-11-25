package com.dataquality.backend.dto.correction;

import com.dataquality.backend.model.AnomalyCorrection;

import java.time.LocalDateTime;

public record CorrectionResponse(
        Long id,
        Integer rowIndex,
        String columnName,
        String oldValue,
        String newValue,
        String correctedBy,
        LocalDateTime correctedAt
) {
    public static CorrectionResponse from(AnomalyCorrection correction) {
        return new CorrectionResponse(
                correction.getId(),
                correction.getRowIndex(),
                correction.getColumnName(),
                correction.getOldValue(),
                correction.getNewValue(),
                correction.getCorrectedBy(),
                correction.getCorrectedAt()
        );
    }
}
