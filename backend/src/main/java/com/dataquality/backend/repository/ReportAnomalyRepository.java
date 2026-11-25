package com.dataquality.backend.repository;

import com.dataquality.backend.model.ReportAnomaly;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportAnomalyRepository extends JpaRepository<ReportAnomaly, Long> {

    long countByReportId(Long reportId);

    /**
     * All filters are optional (null = ignored) so the same query backs both the
     * unfiltered explorer and every filter/search combination. `search` is
     * expected pre-lowercased by the caller; matched against value, column and
     * rule name so free-text search covers what a user would actually recognize.
     */
    @Query("select a from ReportAnomaly a where a.report.id = :reportId "
            + "and (:column is null or a.columnName = :column) "
            + "and (:ruleName is null or a.ruleName = :ruleName) "
            + "and (:checkType is null or a.checkType = :checkType) "
            + "and (:search is null "
            + "     or lower(a.anomalyValue) like concat('%', :search, '%') "
            + "     or lower(a.columnName) like concat('%', :search, '%') "
            + "     or lower(a.ruleName) like concat('%', :search, '%')) "
            + "order by a.rowIndex asc")
    Page<ReportAnomaly> search(
            @Param("reportId") Long reportId,
            @Param("column") String column,
            @Param("ruleName") String ruleName,
            @Param("checkType") String checkType,
            @Param("search") String search,
            Pageable pageable);

    @Query("select a.columnName, count(a) from ReportAnomaly a where a.report.id = :reportId "
            + "group by a.columnName order by count(a) desc")
    List<Object[]> countByColumn(@Param("reportId") Long reportId);

    @Query("select a.ruleName, count(a) from ReportAnomaly a where a.report.id = :reportId "
            + "group by a.ruleName order by count(a) desc")
    List<Object[]> countByRuleName(@Param("reportId") Long reportId);

    @Query("select a.checkType, count(a) from ReportAnomaly a where a.report.id = :reportId "
            + "group by a.checkType order by count(a) desc")
    List<Object[]> countByCheckType(@Param("reportId") Long reportId);
}
