package com.dataquality.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/** Audit trail entry for one corrected cell: old value, new value, row, column, date, actor. */
@Entity
@Table(name = "anomaly_corrections")
@Data
public class AnomalyCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    @ManyToOne
    @JoinColumn(name = "report_id")
    private QualityReport report;

    private Integer rowIndex;
    private String columnName;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    private String correctedBy;
    private LocalDateTime correctedAt;

    @PrePersist
    public void prePersist() {
        if (correctedAt == null) {
            correctedAt = LocalDateTime.now();
        }
    }
}
