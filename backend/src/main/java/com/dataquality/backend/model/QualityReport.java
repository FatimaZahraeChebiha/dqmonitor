package com.dataquality.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "quality_reports")
@Data
public class QualityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    private Double qualityScore;
    private Integer totalRows;
    private Integer validRows;
    private Integer invalidRows;
    private String status;
    private LocalDateTime generatedAt;

    @Column(columnDefinition = "TEXT")
    private String reportDetails;

    /** True once this report's per-row anomaly data has been captured by the
     * new pipeline - even if it found zero anomalies. Lets the Anomaly
     * Explorer tell "genuinely clean report" apart from "predates this
     * feature, no data was ever captured". */
    private boolean anomaliesTracked;

    @PrePersist
    public void prePersist() {
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "GENERATED";
        }
    }
}
