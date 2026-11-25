package com.dataquality.backend.service;

import com.dataquality.backend.dto.dashboard.AnalysisVolumePoint;
import com.dataquality.backend.dto.dashboard.AnomalyDistributionEntry;
import com.dataquality.backend.dto.dashboard.ColumnAnomalyEntry;
import com.dataquality.backend.dto.dashboard.DashboardSummaryResponse;
import com.dataquality.backend.dto.dashboard.DatasetAttentionEntry;
import com.dataquality.backend.dto.dashboard.DatasetStatusDistribution;
import com.dataquality.backend.dto.dashboard.QualityDimensionEntry;
import com.dataquality.backend.dto.dashboard.ScoreTrendPoint;
import com.dataquality.backend.dto.report.QualityReportResponse;
import com.dataquality.backend.model.QualityReport;
import com.dataquality.backend.repository.DatasetRepository;
import com.dataquality.backend.repository.QualityReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Backs the Dashboard with SQL-computed aggregates instead of the original
 * approach of shipping every dataset and report to the browser just to sum a
 * few numbers client-side - that stopped scaling the moment there were more
 * than a handful of reports.
 *
 * Every number below is derived from exactly one list: the most recent report
 * per dataset (see QualityReportRepository.findLatestPerDataset). Earlier this
 * mixed two different views without meaning to - "files analyzed" counted
 * distinct datasets, while the score average, anomaly totals, trend chart and
 * breakdowns counted every individual re-analysis. A dataset re-tested five
 * times while fixing an issue would then dominate the average and make the
 * trend chart swing for one file instead of reflecting genuine quality
 * evolution across the whole portfolio. Grounding everything in one
 * deduplicated list makes that divergence structurally impossible.
 *
 * recentActivity alone applies one further, narrower dedup on top of that
 * list - see the comment at its call site in getSummary().
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    // Effectively "every dataset" for a project at this scale - large enough that
    // no real portfolio gets truncated, small enough to stay a safe upper bound.
    private static final int PORTFOLIO_LIMIT = 5000;
    private static final int TREND_LIMIT = 30;
    private static final int DISTRIBUTION_TOP_N = 8;
    private static final int TOP_COLUMNS_LIMIT = 5;
    private static final int RECENT_ACTIVITY_LIMIT = 5;
    private static final int ATTENTION_LIMIT = 6;

    // Mirrors the frontend's scoreClass() thresholds exactly - a dataset must
    // never read as compliant on one screen and warning on another.
    private static final double COMPLIANT_THRESHOLD = 80.0;
    private static final double WARNING_THRESHOLD = 50.0;
    private static final int ANALYSIS_VOLUME_WEEKS = 9;

    /**
     * Maps a Great Expectations check_type (or the hand-rolled "no_duplicates"
     * baseline) to one of the four standard data-quality dimensions this
     * project's rule types can actually express. There's deliberately no fifth
     * "Consistency" dimension: that would mean cross-field rules (e.g. "if
     * status=SHIPPED then ship_date must be set"), which DQMonitor's rule types
     * don't support - inventing a score for a dimension nothing measures would
     * be worse than not showing it.
     */
    private static final Map<String, String> CHECK_TYPE_TO_DIMENSION = Map.ofEntries(
            Map.entry("expect_column_values_to_not_be_null", "COMPLETENESS"),
            Map.entry("expect_column_to_exist", "COMPLETENESS"),
            Map.entry("expect_column_values_to_match_regex", "VALIDITY"),
            Map.entry("expect_column_values_to_be_between", "VALIDITY"),
            Map.entry("expect_column_values_to_be_unique", "UNIQUENESS"),
            Map.entry("no_duplicates", "UNIQUENESS"),
            Map.entry("expect_column_values_to_be_in_set", "CONFORMITY")
    );
    private static final List<String> DIMENSION_ORDER =
            List.of("COMPLETENESS", "VALIDITY", "UNIQUENESS", "CONFORMITY");

    private final DatasetRepository datasetRepository;
    private final QualityReportRepository qualityReportRepository;
    private final ObjectMapper objectMapper;

    @Cacheable("dashboardSummary")
    public DashboardSummaryResponse getSummary() {
        long totalFiles = datasetRepository.count();

        // Newest-first, one entry per dataset.
        List<QualityReport> latestPerDataset =
                qualityReportRepository.findLatestPerDataset(PageRequest.of(0, PORTFOLIO_LIMIT));

        double avgScore = latestPerDataset.stream()
                .mapToDouble(r -> r.getQualityScore() == null ? 0.0 : r.getQualityScore())
                .average()
                .orElse(0.0);

        long totalAnomalies = latestPerDataset.stream()
                .mapToLong(r -> r.getInvalidRows() == null ? 0 : r.getInvalidRows())
                .sum();

        long totalValidations = latestPerDataset.stream()
                .mapToLong(r -> r.getValidRows() == null ? 0 : r.getValidRows())
                .sum();

        List<ScoreTrendPoint> trend = latestPerDataset.stream()
                .limit(TREND_LIMIT)
                .map(r -> new ScoreTrendPoint(
                        r.getGeneratedAt(), r.getQualityScore() == null ? 0.0 : r.getQualityScore()))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(), list -> {
                            Collections.reverse(list); // newest-first -> chronological for the chart
                            return list;
                        }));

        ReportAggregates aggregates = computeReportAggregates(latestPerDataset);

        // Re-importing the same file creates a new Dataset row each time (upload
        // history is preserved on purpose - see Fichiers de données / Rapports),
        // so latestPerDataset can legitimately contain several entries that share
        // a fileName. That's correct for avgScore/totals/trend above, which count
        // every distinct upload as its own portfolio entry, but "Activité récente"
        // reads as a feed of files, not of uploads - showing "same filename" twice
        // in a 5-row feed looks like a bug even though it isn't one. Since
        // latestPerDataset is already newest-first, keeping only the first
        // occurrence per fileName keeps that file's single most recent analysis.
        Set<String> seenFileNames = new HashSet<>();
        List<QualityReportResponse> recentActivity = latestPerDataset.stream()
                .filter(r -> seenFileNames.add(r.getDataset().getFileName()))
                .limit(RECENT_ACTIVITY_LIMIT)
                .map(QualityReportResponse::from)
                .toList();

        DatasetStatusDistribution statusDistribution = computeStatusDistribution(latestPerDataset);
        List<AnalysisVolumePoint> analysisVolumeTrend = computeAnalysisVolumeTrend();
        List<DatasetAttentionEntry> datasetsNeedingAttention = computeDatasetsNeedingAttention(latestPerDataset);

        return new DashboardSummaryResponse(
                Math.round(avgScore * 10) / 10.0, totalFiles, totalAnomalies, totalValidations,
                trend, aggregates.byCheck(), aggregates.byColumn(), recentActivity, aggregates.byDimension(),
                statusDistribution, analysisVolumeTrend, datasetsNeedingAttention
        );
    }

    /**
     * Every dataset whose latest report scored below "compliant", worst first
     * - the same threshold already used for the status donut, just surfaced as
     * an actionable list instead of a count. Re-imports of the same file name
     * aren't collapsed here the way recentActivity collapses them: each is a
     * genuinely separate dataset that may independently need attention.
     */
    private List<DatasetAttentionEntry> computeDatasetsNeedingAttention(List<QualityReport> reports) {
        return reports.stream()
                .filter(r -> (r.getQualityScore() == null ? 0.0 : r.getQualityScore()) < COMPLIANT_THRESHOLD)
                .sorted(Comparator.comparingDouble(r -> r.getQualityScore() == null ? 0.0 : r.getQualityScore()))
                .limit(ATTENTION_LIMIT)
                .map(r -> new DatasetAttentionEntry(
                        r.getId(), r.getDataset().getFileName(),
                        r.getQualityScore() == null ? 0.0 : r.getQualityScore(), r.getGeneratedAt()))
                .toList();
    }

    private DatasetStatusDistribution computeStatusDistribution(List<QualityReport> reports) {
        long compliant = 0, warning = 0, critical = 0;
        for (QualityReport report : reports) {
            double score = report.getQualityScore() == null ? 0.0 : report.getQualityScore();
            if (score >= COMPLIANT_THRESHOLD) {
                compliant++;
            } else if (score >= WARNING_THRESHOLD) {
                warning++;
            } else {
                critical++;
            }
        }
        return new DatasetStatusDistribution(compliant, warning, critical);
    }

    /**
     * One point per week for the last ANALYSIS_VOLUME_WEEKS weeks, including
     * weeks with zero analyses - a real zero, not a gap, so the chart's x-axis
     * stays continuous instead of silently skipping quiet weeks.
     */
    private List<AnalysisVolumePoint> computeAnalysisVolumeTrend() {
        LocalDate currentWeekStart = mondayOfWeek(LocalDate.now());
        LocalDate windowStart = currentWeekStart.minusWeeks(ANALYSIS_VOLUME_WEEKS - 1L);

        List<QualityReport> reports =
                qualityReportRepository.findByGeneratedAtAfterOrderByGeneratedAtAsc(windowStart.atStartOfDay());

        Map<LocalDate, Long> countsByWeek = new HashMap<>();
        for (QualityReport report : reports) {
            if (report.getGeneratedAt() == null) {
                continue;
            }
            LocalDate weekStart = mondayOfWeek(report.getGeneratedAt().toLocalDate());
            countsByWeek.merge(weekStart, 1L, Long::sum);
        }

        List<AnalysisVolumePoint> points = new ArrayList<>();
        for (LocalDate week = windowStart; !week.isAfter(currentWeekStart); week = week.plusWeeks(1)) {
            points.add(new AnalysisVolumePoint(week, countsByWeek.getOrDefault(week, 0L)));
        }
        return points;
    }

    private static LocalDate mondayOfWeek(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }

    private record ReportAggregates(
            List<AnomalyDistributionEntry> byCheck,
            List<ColumnAnomalyEntry> byColumn,
            List<QualityDimensionEntry> byDimension
    ) {}

    /**
     * Walks each dataset's latest reportDetails JSON once and builds three
     * breakdowns from it in a single pass: by check (the "anomaly distribution"
     * chart), by column (the "top problematic columns" panel), and by dimension
     * (the "quality dimensions" panel).
     */
    private ReportAggregates computeReportAggregates(List<QualityReport> reports) {
        Map<String, Long> byCheckTotals = new LinkedHashMap<>();
        Map<String, Long> byColumnTotals = new LinkedHashMap<>();
        // dimension -> [rows that passed that dimension's checks, rows checked]
        Map<String, long[]> byDimensionTotals = new LinkedHashMap<>();

        for (QualityReport report : reports) {
            String reportDetails = report.getReportDetails();
            if (reportDetails == null || reportDetails.isBlank()) {
                continue;
            }
            Integer totalRows = report.getTotalRows();
            try {
                JsonNode checks = objectMapper.readTree(reportDetails);
                for (JsonNode check : checks) {
                    long unexpectedCount = check.path("unexpected_count").asLong(0);

                    // Dimension scoring needs every check that ran, including fully
                    // clean ones - it answers "what fraction of rows passed", not
                    // just "which checks found something wrong" like the two
                    // breakdowns below.
                    String checkType = check.path("check_type").asText(null);
                    if (checkType != null && totalRows != null && totalRows > 0) {
                        String dimension = CHECK_TYPE_TO_DIMENSION.get(checkType);
                        if (dimension != null) {
                            long[] acc = byDimensionTotals.computeIfAbsent(dimension, d -> new long[2]);
                            acc[0] += Math.max(totalRows - unexpectedCount, 0);
                            acc[1] += totalRows;
                        }
                    }

                    if (unexpectedCount <= 0) {
                        continue;
                    }
                    String checkName = check.path("check_name").asText("unknown");
                    byCheckTotals.merge(checkName, unexpectedCount, Long::sum);

                    // Whole-row checks (e.g. no_duplicates) have no single column and are
                    // intentionally left out of this breakdown - it answers "which column
                    // is the problem", which doesn't apply to a row-level duplicate.
                    String column = check.path("column").asText(null);
                    if (column != null && !column.isBlank()) {
                        byColumnTotals.merge(column, unexpectedCount, Long::sum);
                    }
                }
            } catch (Exception e) {
                log.warn("Could not parse reportDetails for dashboard aggregation: {}", e.getMessage());
            }
        }

        List<AnomalyDistributionEntry> byCheck = byCheckTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(DISTRIBUTION_TOP_N)
                .map(e -> new AnomalyDistributionEntry(e.getKey(), e.getValue()))
                .toList();

        long columnGrandTotal = byColumnTotals.values().stream().mapToLong(Long::longValue).sum();
        List<ColumnAnomalyEntry> byColumn = columnGrandTotal == 0 ? List.of() : byColumnTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_COLUMNS_LIMIT)
                .map(e -> new ColumnAnomalyEntry(
                        e.getKey(), e.getValue(), Math.round(e.getValue() * 1000.0 / columnGrandTotal) / 10.0))
                .toList();

        // Always all 4 dimensions, in a fixed order, so the panel's layout never
        // shifts based on which rule types happen to be configured. A dimension
        // nothing has ever checked defaults to 100 - "nothing measured" reads as
        // "no known problem", the same convention the overall score already uses
        // for an empty check set.
        List<QualityDimensionEntry> byDimension = DIMENSION_ORDER.stream()
                .map(dimension -> {
                    long[] acc = byDimensionTotals.get(dimension);
                    double score = (acc == null || acc[1] == 0) ? 100.0 : Math.round(acc[0] * 1000.0 / acc[1]) / 10.0;
                    return new QualityDimensionEntry(dimension, score);
                })
                .toList();

        return new ReportAggregates(byCheck, byColumn, byDimension);
    }
}
