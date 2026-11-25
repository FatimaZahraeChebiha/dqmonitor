-- Baseline schema for DQMonitor. Replaces Hibernate ddl-auto=update so schema
-- history is versioned and auditable instead of silently drifting.

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password      VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME     NOT NULL
);

CREATE TABLE datasets (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name          VARCHAR(255) NOT NULL,
    stored_file_name   VARCHAR(255) NOT NULL,
    file_path          VARCHAR(1024) NOT NULL,
    file_size          BIGINT NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    uploaded_by_id     BIGINT NULL,
    uploaded_at        DATETIME NOT NULL,
    CONSTRAINT fk_dataset_uploaded_by FOREIGN KEY (uploaded_by_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE quality_reports (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id     BIGINT NOT NULL,
    quality_score  DOUBLE NULL,
    total_rows     INT NULL,
    valid_rows     INT NULL,
    invalid_rows   INT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'GENERATED',
    generated_at   DATETIME NOT NULL,
    report_details TEXT NULL,
    CONSTRAINT fk_report_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE
);

CREATE TABLE validation_rules (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name    VARCHAR(255) NOT NULL,
    column_name  VARCHAR(255) NOT NULL,
    rule_type    VARCHAR(30)  NOT NULL,
    rule_value   VARCHAR(1024) NULL,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    description  VARCHAR(1024) NULL,
    created_at   DATETIME NOT NULL
);

CREATE INDEX idx_datasets_status ON datasets(status);
CREATE INDEX idx_reports_dataset_id ON quality_reports(dataset_id);
CREATE INDEX idx_rules_active ON validation_rules(is_active);
