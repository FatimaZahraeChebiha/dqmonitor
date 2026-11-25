package com.dataquality.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "datasets")
@Data
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Original filename as uploaded by the user, for display only. */
    private String fileName;

    /** UUID-based name actually used on disk - never derived from user input. */
    private String storedFileName;

    private String filePath;
    private Long fileSize;

    /** Set once at least one anomaly correction has been applied - the original
     * file above is never modified in place, so these point at a separate copy. */
    private String correctedFilePath;
    private String correctedStoredFileName;
    private LocalDateTime lastCorrectedAt;

    @Enumerated(EnumType.STRING)
    private DatasetStatus status;

    @ManyToOne
    @JoinColumn(name = "uploaded_by_id")
    private User uploadedBy;

    private LocalDateTime uploadedAt;

    @PrePersist
    public void prePersist() {
        uploadedAt = LocalDateTime.now();
        if (status == null) {
            status = DatasetStatus.PENDING;
        }
    }
}
