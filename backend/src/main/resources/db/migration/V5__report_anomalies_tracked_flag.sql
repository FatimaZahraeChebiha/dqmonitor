-- Distinguishes "the new pipeline ran and found zero anomalies" (clean report,
-- correction/export UI should stay available) from "this report predates the
-- Anomaly Explorer and simply never had per-row anomaly data captured" (needs
-- a re-analysis). Both look identical as "zero rows in report_anomalies"
-- without this flag, which made a 100%-score report after a correction wrongly
-- show the "re-analyze needed" banner instead of its real, complete data.

ALTER TABLE quality_reports
    ADD COLUMN anomalies_tracked BOOLEAN NOT NULL DEFAULT FALSE;
