package com.dataquality.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_anomalies")
@Data
public class ReportAnomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "report_id")
    private QualityReport report;

    private Integer rowIndex;
    private String columnName;
    private String ruleName;
    private String checkType;

    @Column(columnDefinition = "TEXT")
    private String anomalyValue;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
