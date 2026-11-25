package com.dataquality.backend.service;

import com.dataquality.backend.model.Dataset;
import com.dataquality.backend.model.QualityReport;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PdfReportServiceTest {

    private final PdfReportService pdfReportService = new PdfReportService();

    @Test
    void generatesAWellFormedPdfForAReportWithChecks() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileName("sample.csv");

        QualityReport report = new QualityReport();
        report.setId(1L);
        report.setDataset(dataset);
        report.setQualityScore(50.0);
        report.setTotalRows(3);
        report.setValidRows(2);
        report.setInvalidRows(1);
        report.setGeneratedAt(LocalDateTime.of(2026, 7, 20, 20, 33));
        report.setReportDetails(
                "[{\"check_name\": \"expect_column_values_to_match_regex_email\", \"success\": false, "
                        + "\"unexpected_count\": 1, \"unexpected_pct\": 33.33}, "
                        + "{\"check_name\": \"no_duplicates\", \"success\": true, "
                        + "\"unexpected_count\": 0, \"unexpected_pct\": 0.0}]"
        );

        byte[] pdf = pdfReportService.generate(report);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void handlesAReportWithNoChecksWithoutThrowing() {
        Dataset dataset = new Dataset();
        dataset.setId(2L);
        dataset.setFileName("empty.csv");

        QualityReport report = new QualityReport();
        report.setId(2L);
        report.setDataset(dataset);
        report.setQualityScore(100.0);
        report.setTotalRows(0);
        report.setValidRows(0);
        report.setInvalidRows(0);
        report.setGeneratedAt(LocalDateTime.now());
        report.setReportDetails(null);

        byte[] pdf = pdfReportService.generate(report);

        assertThat(pdf).isNotEmpty();
    }
}
