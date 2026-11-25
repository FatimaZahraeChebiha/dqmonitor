-- Anomaly correction support: a corrected copy of the source file lives
-- alongside the original (never overwritten in place), plus a full audit
-- trail of every cell edit for traceability.

ALTER TABLE datasets
    ADD COLUMN corrected_file_path        VARCHAR(1024) NULL,
    ADD COLUMN corrected_stored_file_name VARCHAR(255)  NULL,
    ADD COLUMN last_corrected_at          DATETIME      NULL;

CREATE TABLE anomaly_corrections (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id   BIGINT NOT NULL,
    report_id    BIGINT NULL,
    row_index    INT NOT NULL,
    column_name  VARCHAR(255) NOT NULL,
    old_value    TEXT NULL,
    new_value    TEXT NULL,
    corrected_by VARCHAR(255) NULL,
    corrected_at DATETIME NOT NULL,
    CONSTRAINT fk_correction_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE,
    CONSTRAINT fk_correction_report FOREIGN KEY (report_id) REFERENCES quality_reports(id) ON DELETE SET NULL
);

CREATE INDEX idx_corrections_dataset ON anomaly_corrections(dataset_id);
