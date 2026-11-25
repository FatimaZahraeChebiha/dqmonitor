package com.dataquality.backend.service;

import com.dataquality.backend.dto.PageResponse;
import com.dataquality.backend.dto.anomaly.AnomalyGroupCount;
import com.dataquality.backend.dto.anomaly.AnomalyResponse;
import com.dataquality.backend.dto.anomaly.AnomalyStatsResponse;
import com.dataquality.backend.exception.ResourceNotFoundException;
import com.dataquality.backend.model.QualityReport;
import com.dataquality.backend.model.ReportAnomaly;
import com.dataquality.backend.repository.QualityReportRepository;
import com.dataquality.backend.repository.ReportAnomalyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportAnomalyService {

    private static final int MAX_PAGE_SIZE = 200;

    private final ReportAnomalyRepository anomalyRepository;
    private final QualityReportRepository qualityReportRepository;

    public PageResponse<AnomalyResponse> search(
            Long reportId, int page, int size, String column, String ruleName, String checkType, String search
    ) {
        getReport(reportId);
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        Page<ReportAnomaly> result = anomalyRepository
                .search(reportId, blankToNull(column), blankToNull(ruleName), blankToNull(checkType), normalize(search), pageable);
        return PageResponse.from(result, AnomalyResponse::from);
    }

    public AnomalyStatsResponse getStats(Long reportId) {
        QualityReport report = getReport(reportId);
        long total = anomalyRepository.countByReportId(reportId);
        List<AnomalyGroupCount> byColumn = toGroupCounts(anomalyRepository.countByColumn(reportId));
        List<AnomalyGroupCount> byRule = toGroupCounts(anomalyRepository.countByRuleName(reportId));
        List<AnomalyGroupCount> byType = toGroupCounts(anomalyRepository.countByCheckType(reportId));

        AnomalyGroupCount topColumn = byColumn.isEmpty() ? null : byColumn.get(0);
        Double topColumnPct = (topColumn != null && total > 0)
                ? Math.round(topColumn.count() * 1000.0 / total) / 10.0
                : null;

        // Reading the report's own flag (not "does report_anomalies have any
        // rows") is what tells a genuinely clean report (new pipeline ran,
        // found nothing) apart from an old report that predates this feature
        // and was simply never given per-row data - both have zero rows here.
        return new AnomalyStatsResponse(total, byColumn, byRule, byType, topColumn, topColumnPct, report.isAnomaliesTracked());
    }

    /** Streams every anomaly matching the given filters as CSV - same filter semantics as search(), just unpaged. */
    public void exportCsv(Long reportId, String column, String ruleName, String checkType, String search, Writer writer) throws IOException {
        getReport(reportId);
        List<AnomalyResponse> all = anomalyRepository
                .search(reportId, blankToNull(column), blankToNull(ruleName), blankToNull(checkType), normalize(search), Pageable.unpaged())
                .map(AnomalyResponse::from)
                .getContent();

        writer.write("row_index,column,rule_name,check_type,value\n");
        for (AnomalyResponse a : all) {
            writer.write(csvField(a.rowIndex() == null ? "" : a.rowIndex().toString()));
            writer.write(",");
            writer.write(csvField(a.columnName()));
            writer.write(",");
            writer.write(csvField(a.ruleName()));
            writer.write(",");
            writer.write(csvField(a.checkType()));
            writer.write(",");
            writer.write(csvField(a.value()));
            writer.write("\n");
        }
    }

    private String csvField(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private QualityReport getReport(Long reportId) {
        return qualityReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId));
    }

    private List<AnomalyGroupCount> toGroupCounts(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new AnomalyGroupCount(row[0] == null ? "—" : row[0].toString(), (Long) row[1]))
                .toList();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String normalize(String search) {
        return StringUtils.hasText(search) ? search.trim().toLowerCase() : null;
    }
}
