package com.dataquality.backend.repository;

import com.dataquality.backend.model.QualityReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface QualityReportRepository extends JpaRepository<QualityReport, Long> {
    List<QualityReport> findByDatasetId(Long datasetId);
    Page<QualityReport> findAllByOrderByGeneratedAtDesc(Pageable pageable);

    /**
     * Every report generated since the given instant - deliberately every
     * re-analysis, not deduplicated per dataset, since this backs the
     * "analysis volume" activity chart, which measures platform usage over
     * time rather than per-dataset quality.
     */
    List<QualityReport> findByGeneratedAtAfterOrderByGeneratedAtAsc(LocalDateTime since);

    /**
     * One row per dataset - only its most recently generated report. Plain
     * findAllByOrderByGeneratedAtDesc returns every report, so a dataset that has
     * been re-analyzed several times fills up all the "recent" slots by itself
     * and crowds out datasets that only needed a single analysis.
     *
     * This is also the single source of truth for the Dashboard: average score,
     * anomaly/validation totals, the trend chart and the anomaly breakdowns are
     * all derived from this same deduplicated list in DashboardService, instead
     * of from separate whole-table aggregates that counted every re-analysis.
     * A dataset re-tested ten times while fixing an issue should not outweigh
     * nine datasets that only needed one clean analysis.
     */
    @Query("select r from QualityReport r where r.generatedAt = "
            + "(select max(r2.generatedAt) from QualityReport r2 where r2.dataset = r.dataset) "
            + "order by r.generatedAt desc")
    List<QualityReport> findLatestPerDataset(Pageable pageable);
}
