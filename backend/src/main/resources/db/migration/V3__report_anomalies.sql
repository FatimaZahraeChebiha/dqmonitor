-- Dedicated table for per-anomaly detail, replacing the old approach of stuffing
-- a capped sample (20 per rule) into quality_reports.report_details as JSON text.
-- One row per real anomaly, so the Anomaly Explorer can filter/paginate/search
-- over the full set instead of a fixed in-memory sample.

CREATE TABLE report_anomalies (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id     BIGINT NOT NULL,
    row_index     INT NOT NULL,
    column_name   VARCHAR(255) NULL,
    rule_name     VARCHAR(255) NOT NULL,
    check_type    VARCHAR(100) NOT NULL,
    anomaly_value TEXT NULL,
    created_at    DATETIME NOT NULL,
    CONSTRAINT fk_anomaly_report FOREIGN KEY (report_id) REFERENCES quality_reports(id) ON DELETE CASCADE
);

CREATE INDEX idx_anomalies_report_id ON report_anomalies(report_id);
CREATE INDEX idx_anomalies_report_column ON report_anomalies(report_id, column_name);
CREATE INDEX idx_anomalies_report_rule ON report_anomalies(report_id, rule_name);
