package com.dataquality.backend.service;

import com.dataquality.backend.dto.dashboard.ColumnAnomalyEntry;
import com.dataquality.backend.dto.dashboard.DashboardSummaryResponse;
import com.dataquality.backend.dto.dashboard.DatasetStatusDistribution;
import com.dataquality.backend.dto.dashboard.QualityDimensionEntry;
import com.dataquality.backend.model.Dataset;
import com.dataquality.backend.model.QualityReport;
import com.dataquality.backend.repository.DatasetRepository;
import com.dataquality.backend.repository.QualityReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private final DatasetRepository datasetRepository = mock(DatasetRepository.class);
    private final QualityReportRepository qualityReportRepository = mock(QualityReportRepository.class);
    private final DashboardService dashboardService =
            new DashboardService(datasetRepository, qualityReportRepository, new ObjectMapper());

    private static QualityReport report(long id, String fileName, double score, int totalRows,
                                         int validRows, int invalidRows, LocalDateTime generatedAt,
                                         String reportDetails) {
        Dataset dataset = new Dataset();
        dataset.setId(id);
        dataset.setFileName(fileName);

        QualityReport report = new QualityReport();
        report.setId(id);
        report.setDataset(dataset);
        report.setQualityScore(score);
        report.setTotalRows(totalRows);
        report.setValidRows(validRows);
        report.setInvalidRows(invalidRows);
        report.setGeneratedAt(generatedAt);
        report.setReportDetails(reportDetails);
        return report;
    }

    @Test
    void computesAveragesAndTotalsFromTheDeduplicatedListOnly() {
        // findLatestPerDataset already deduplicates - the service must trust that
        // and not need any extra logic to avoid double-counting a re-analyzed file.
        List<QualityReport> latest = List.of(
                report(1, "cardio.csv", 100.0, 70000, 70000, 0, LocalDateTime.now(), null),
                report(2, "dataset_anomalies.csv", 53.3, 15, 8, 7, LocalDateTime.now().minusHours(1), null)
        );
        when(datasetRepository.count()).thenReturn(2L);
        when(qualityReportRepository.findLatestPerDataset(any(Pageable.class))).thenReturn(latest);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.totalFiles()).isEqualTo(2L);
        assertThat(summary.avgQualityScore()).isEqualTo(76.7); // (100.0 + 53.3) / 2, rounded to 1 decimal
        assertThat(summary.totalAnomalies()).isEqualTo(7L);
        assertThat(summary.totalValidations()).isEqualTo(70008L);
    }

    @Test
    void ordersTheTrendChronologicallyFromTheNewestFirstQueryResult() {
        LocalDateTime older = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 2, 1, 0, 0);
        // findLatestPerDataset returns newest-first, per its own ORDER BY.
        List<QualityReport> latest = List.of(
                report(1, "b.csv", 90.0, 10, 9, 1, newer, null),
                report(2, "a.csv", 80.0, 10, 8, 2, older, null)
        );
        when(datasetRepository.count()).thenReturn(2L);
        when(qualityReportRepository.findLatestPerDataset(any(Pageable.class))).thenReturn(latest);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.scoreTrend()).extracting(p -> p.date()).containsExactly(older, newer);
    }

    @Test
    void ranksColumnsByTotalUnexpectedCountAndComputesTheirShareOfTheTotal() {
        List<QualityReport> latest = List.of(report(1, "f.csv", 50.0, 15, 8, 7, LocalDateTime.now(),
                "["
                        + "{\"check_name\":\"expect_column_values_to_not_be_null_email\",\"check_type\":\"expect_column_values_to_not_be_null\",\"column\":\"email\",\"unexpected_count\":2},"
                        + "{\"check_name\":\"expect_column_values_to_be_between_age\",\"check_type\":\"expect_column_values_to_be_between\",\"column\":\"age\",\"unexpected_count\":3},"
                        + "{\"check_name\":\"no_duplicates\",\"check_type\":\"no_duplicates\",\"unexpected_count\":2}"
                        + "]"
        ));
        when(datasetRepository.count()).thenReturn(1L);
        when(qualityReportRepository.findLatestPerDataset(any(Pageable.class))).thenReturn(latest);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.topProblematicColumns()).containsExactly(
                new ColumnAnomalyEntry("age", 3, 60.0),
                new ColumnAnomalyEntry("email", 2, 40.0)
        );
        // The whole-row no_duplicates check has no single column - it stays in
        // the by-check breakdown but must not appear in the by-column one.
        assertThat(summary.anomalyDistribution()).hasSize(3);
    }

    @Test
    void computesEachDimensionAsARowWeightedRatioAcrossItsMappedChecks() {
        // 15 total rows. COMPLETENESS: not_null_email fails 2 rows -> 13/15.
        // VALIDITY: between_age fails 3 rows -> 12/15. UNIQUENESS: no_duplicates
        // fails 2 rows -> 13/15. CONFORMITY: no check ran for it -> defaults to 100.
        List<QualityReport> latest = List.of(report(1, "f.csv", 50.0, 15, 8, 7, LocalDateTime.now(),
                "["
                        + "{\"check_name\":\"expect_column_values_to_not_be_null_email\",\"check_type\":\"expect_column_values_to_not_be_null\",\"column\":\"email\",\"unexpected_count\":2},"
                        + "{\"check_name\":\"expect_column_values_to_be_between_age\",\"check_type\":\"expect_column_values_to_be_between\",\"column\":\"age\",\"unexpected_count\":3},"
                        + "{\"check_name\":\"no_duplicates\",\"check_type\":\"no_duplicates\",\"unexpected_count\":2}"
                        + "]"
        ));
        when(datasetRepository.count()).thenReturn(1L);
        when(qualityReportRepository.findLatestPerDataset(any(Pageable.class))).thenReturn(latest);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.qualityDimensions()).containsExactly(
                new QualityDimensionEntry("COMPLETENESS", 86.7),
                new QualityDimensionEntry("VALIDITY", 80.0),
                new QualityDimensionEntry("UNIQUENESS", 86.7),
                new QualityDimensionEntry("CONFORMITY", 100.0)
        );
    }

    @Test
    void combinesDimensionTotalsAcrossMultipleDatasetsBeforeComputingTheRatio() {
        List<QualityReport> latest = List.of(
                report(1, "a.csv", 100.0, 10, 10, 0, LocalDateTime.now(),
                        "[{\"check_name\":\"n\",\"check_type\":\"expect_column_values_to_not_be_null\",\"column\":\"x\",\"unexpected_count\":0}]"),
                report(2, "b.csv", 50.0, 10, 5, 5, LocalDateTime.now().minusHours(1),
                        "[{\"check_name\":\"n\",\"check_type\":\"expect_column_values_to_not_be_null\",\"column\":\"x\",\"unexpected_count\":5}]")
        );
        when(datasetRepository.count()).thenReturn(2L);
        when(qualityReportRepository.findLatestPerDataset(any(Pageable.class))).thenReturn(latest);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        // (10 - 0) + (10 - 5) = 15 valid rows out of 20 total -> 75.0
        assertThat(summary.qualityDimensions())
                .filteredOn(d -> d.dimension().equals("COMPLETENESS"))
                .containsExactly(new QualityDimensionEntry("COMPLETENESS", 75.0));
    }

    @Test
    void returnsAnEmptyColumnListWhenNoCheckHasAColumn() {
        List<QualityReport> latest = List.of(report(1, "f.csv", 100.0, 5, 5, 0, LocalDateTime.now(),
                "[{\"check_name\":\"no_duplicates\",\"unexpected_count\":0}]"));
        when(datasetRepository.count()).thenReturn(1L);
        when(qualityReportRepository.findLatestPerDataset(any(Pageable.class))).thenReturn(latest);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.topProblematicColumns()).isEmpty();
    }

    @Test
    void ignoresBlankOrUnparseableReportDetailsWithoutThrowing() {
        List<QualityReport> latest = List.of(
                report(1, "a.csv", 0.0, 0, 0, 0, LocalDateTime.now(), null),
                report(2, "b.csv", 0.0, 0, 0, 0, LocalDateTime.now(), ""),
                report(3, "c.csv", 0.0, 0, 0, 0, LocalDateTime.now(), "not valid json")
        );
        when(datasetRepository.count()).thenReturn(3L);
        when(qualityReportRepository.findLatestPerDataset(any(Pageable.class))).thenReturn(latest);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.topProblematicColumns()).isEmpty();
        assertThat(summary.anomalyDistribution()).isEmpty();
    }

    @Test
    void mapsTheLatestPerDatasetQueryIntoRecentActivity() {
        List<QualityReport> latest = List.of(
                report(42, "cardio.csv", 100.0, 70000, 70000, 0, LocalDateTime.of(2026, 8, 2, 16, 28), null)
        );
        when(datasetRepository.count()).thenReturn(1L);
        when(qualityReportRepository.findLatestPerDataset(any(Pageable.class))).thenReturn(latest);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.recentActivity()).hasSize(1);
        assertThat(summary.recentActivity().get(0).id()).isEqualTo(42L);
        assertThat(summary.recentActivity().get(0).dataset().fileName()).isEqualTo("cardio.csv");
        assertThat(summary.recentActivity().get(0).qualityScore()).isEqualTo(100.0);
    }

    @Test
    void collapsesRecentActivityToOneRowPerFileNameKeepingOnlyTheNewerUpload() {
        // Re-importing the same file creates a second Dataset row (a distinct
        // upload) rather than reusing the first - latestPerDataset legitimately
        // contains both. recentActivity must still show only the newer one.
        List<QualityReport> latest = List.of(
                report(20, "dataset_anomalies.csv", 53.3, 15, 8, 7, LocalDateTime.of(2026, 9, 15, 16, 58), null),
                report(10, "dataset_anomalies.csv", 0.0, 15, 8, 7, LocalDateTime.of(2026, 8, 19, 20, 45), null),
                report(30, "cardio.csv", 100.0, 70000, 70000, 0, LocalDateTime.of(2026, 8, 2, 16, 28), null)
        );
        when(datasetRepository.count()).thenReturn(3L);
        when(qualityReportRepository.findLatestPerDataset(any(Pageable.class))).thenReturn(latest);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.recentActivity()).hasSize(2);
        assertThat(summary.recentActivity()).extracting(r -> r.id()).containsExactly(20L, 30L);

        // The older upload of the same file must still count everywhere else -
        // only the "Activité récente" feed narrows to one row per file name.
        assertThat(summary.totalValidations()).isEqualTo(70008L + 8L);
    }

    @Test
    void bucketsDatasetsIntoTheSameStatusThresholdsUsedElsewhereInThePlatform() {
        List<QualityReport> latest = List.of(
                report(1, "a.csv", 100.0, 10, 10, 0, LocalDateTime.now(), null),  // compliant
                report(2, "b.csv", 80.0, 10, 8, 2, LocalDateTime.now(), null),    // compliant (boundary)
                report(3, "c.csv", 79.9, 10, 8, 2, LocalDateTime.now(), null),    // warning (just under 80)
                report(4, "d.csv", 50.0, 10, 5, 5, LocalDateTime.now(), null),    // warning (boundary)
                report(5, "e.csv", 49.9, 10, 5, 5, LocalDateTime.now(), null),    // critical (just under 50)
                report(6, "f.csv", 0.0, 10, 0, 10, LocalDateTime.now(), null)     // critical
        );
        when(datasetRepository.count()).thenReturn(6L);
        when(qualityReportRepository.findLatestPerDataset(any(Pageable.class))).thenReturn(latest);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.datasetStatusDistribution()).isEqualTo(new DatasetStatusDistribution(2, 2, 2));
    }

    @Test
    void listsOnlyNonCompliantDatasetsWorstScoreFirst() {
        List<QualityReport> latest = List.of(
                report(1, "a.csv", 100.0, 10, 10, 0, LocalDateTime.now(), null),  // compliant - excluded
                report(2, "b.csv", 79.9, 10, 8, 2, LocalDateTime.now(), null),    // warning
                report(3, "c.csv", 10.0, 10, 1, 9, LocalDateTime.now(), null)     // critical - worst
        );
        when(datasetRepository.count()).thenReturn(3L);
        when(qualityReportRepository.findLatestPerDataset(any(Pageable.class))).thenReturn(latest);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.datasetsNeedingAttention()).extracting(e -> e.fileName())
                .containsExactly("c.csv", "b.csv");
        assertThat(summary.datasetsNeedingAttention().get(0).reportId()).isEqualTo(3L);
        assertThat(summary.datasetsNeedingAttention().get(0).score()).isEqualTo(10.0);
    }

    @Test
    void capsDatasetsNeedingAttentionAtSixEntries() {
        List<QualityReport> latest = new java.util.ArrayList<>();
        for (long i = 1; i <= 9; i++) {
            latest.add(report(i, "f" + i + ".csv", 20.0, 10, 2, 8, LocalDateTime.now(), null));
        }
        when(datasetRepository.count()).thenReturn(9L);
        when(qualityReportRepository.findLatestPerDataset(any(Pageable.class))).thenReturn(latest);

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.datasetsNeedingAttention()).hasSize(6);
    }

    @Test
    void buildsAContinuousNineWeekAnalysisVolumeTrendEndingOnTheCurrentWeekWithZerosForQuietWeeks() {
        LocalDate currentWeekMonday = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1L);
        LocalDateTime thisWeek = currentWeekMonday.atTime(10, 0);
        LocalDateTime twoWeeksAgo = currentWeekMonday.minusWeeks(2).atTime(10, 0);

        when(datasetRepository.count()).thenReturn(0L);
        when(qualityReportRepository.findLatestPerDataset(any(Pageable.class))).thenReturn(List.of());
        when(qualityReportRepository.findByGeneratedAtAfterOrderByGeneratedAtAsc(any())).thenReturn(List.of(
                report(1, "a.csv", 100.0, 10, 10, 0, thisWeek, null),
                report(2, "b.csv", 100.0, 10, 10, 0, thisWeek, null),
                report(3, "c.csv", 100.0, 10, 10, 0, twoWeeksAgo, null)
        ));

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.analysisVolumeTrend()).hasSize(9);
        assertThat(summary.analysisVolumeTrend().get(8).weekStart()).isEqualTo(currentWeekMonday);
        assertThat(summary.analysisVolumeTrend().get(8).count()).isEqualTo(2L);
        assertThat(summary.analysisVolumeTrend().get(6).weekStart()).isEqualTo(currentWeekMonday.minusWeeks(2));
        assertThat(summary.analysisVolumeTrend().get(6).count()).isEqualTo(1L);
        // A quiet week still gets an explicit zero, not a gap.
        assertThat(summary.analysisVolumeTrend().get(7).count()).isZero();
    }

    @Test
    void returnsSensibleDefaultsWhenNoDatasetHasEverBeenAnalyzed() {
        when(datasetRepository.count()).thenReturn(0L);
        when(qualityReportRepository.findLatestPerDataset(any(Pageable.class))).thenReturn(List.of());

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.avgQualityScore()).isZero();
        assertThat(summary.totalAnomalies()).isZero();
        assertThat(summary.totalValidations()).isZero();
        assertThat(summary.scoreTrend()).isEmpty();
        assertThat(summary.anomalyDistribution()).isEmpty();
        assertThat(summary.topProblematicColumns()).isEmpty();
        assertThat(summary.recentActivity()).isEmpty();
        // No check has ever run for any dimension - each defaults to 100, not 0,
        // so an unconfigured platform reads as "nothing known to be wrong" rather
        // than a false "everything is failing".
        assertThat(summary.qualityDimensions()).containsExactly(
                new QualityDimensionEntry("COMPLETENESS", 100.0),
                new QualityDimensionEntry("VALIDITY", 100.0),
                new QualityDimensionEntry("UNIQUENESS", 100.0),
                new QualityDimensionEntry("CONFORMITY", 100.0)
        );
        assertThat(summary.datasetStatusDistribution()).isEqualTo(new DatasetStatusDistribution(0, 0, 0));
        // Still 9 weeks of real zeros, not an empty/absent series.
        assertThat(summary.analysisVolumeTrend()).hasSize(9);
        assertThat(summary.analysisVolumeTrend()).allMatch(p -> p.count() == 0);
        assertThat(summary.datasetsNeedingAttention()).isEmpty();
    }
}
