package com.dataquality.backend.service;

import com.dataquality.backend.dto.dashboard.DashboardSummaryResponse;
import com.dataquality.backend.model.Dataset;
import com.dataquality.backend.model.QualityReport;
import com.dataquality.backend.model.ValidationRule;
import com.dataquality.backend.repository.DatasetRepository;
import com.dataquality.backend.repository.QualityReportRepository;
import com.dataquality.backend.repository.ValidationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The "retrieval" half of the AI assistant's RAG design. This app's data is
 * structured relational rows (datasets, reports, rules), not a corpus of
 * free-text documents - so retrieval here means SQL queries grounding the
 * prompt in real numbers, not a vector-embedding/similarity-search pipeline.
 * Building an embedding index over a handful of structured tables would be
 * over-engineering; the database itself is the retrieval index.
 *
 * Every answer is grounded in: the dashboard aggregate summary (always),
 * the active validation rules (always), and - if the user's message appears
 * to name a specific dataset - that dataset's latest report in full detail.
 * Otherwise a handful of recent reports are included so general questions
 * ("how are things looking?") still have concrete numbers to draw on.
 */
@Service
@RequiredArgsConstructor
public class AiContextService {

    private static final int RECENT_DATASETS_TO_SCAN = 200;
    private static final int RECENT_REPORTS_FALLBACK = 5;

    private final DatasetRepository datasetRepository;
    private final QualityReportRepository qualityReportRepository;
    private final ValidationRuleRepository validationRuleRepository;
    private final DashboardService dashboardService;

    public String buildContext(String userMessage) {
        StringBuilder context = new StringBuilder();
        appendDashboardSummary(context);
        appendActiveRules(context);

        Optional<Dataset> mentioned = findMentionedDataset(userMessage);
        if (mentioned.isPresent()) {
            appendDatasetDetail(context, mentioned.get());
        } else {
            appendRecentReports(context);
        }

        return context.toString();
    }

    private void appendDashboardSummary(StringBuilder context) {
        DashboardSummaryResponse summary = dashboardService.getSummary();
        context.append("Platform overview: average quality score ")
                .append(summary.avgQualityScore()).append("%, ")
                .append(summary.totalFiles()).append(" files analyzed, ")
                .append(summary.totalAnomalies()).append(" total anomalies found, ")
                .append(summary.totalValidations()).append(" valid rows recorded.\n\n");
    }

    private void appendActiveRules(StringBuilder context) {
        List<ValidationRule> activeRules = validationRuleRepository.findByIsActive(true);
        if (activeRules.isEmpty()) {
            return;
        }
        context.append("Active validation rules:\n");
        for (ValidationRule rule : activeRules) {
            context.append("- ").append(rule.getRuleName())
                    .append(" (column: ").append(rule.getColumnName())
                    .append(", type: ").append(rule.getRuleType()).append(")\n");
        }
        context.append("\n");
    }

    private void appendDatasetDetail(StringBuilder context, Dataset dataset) {
        context.append("The user appears to be asking about dataset \"")
                .append(dataset.getFileName()).append("\" (status: ").append(dataset.getStatus()).append(").\n");

        List<QualityReport> reports = qualityReportRepository.findByDatasetId(dataset.getId());
        if (reports.isEmpty()) {
            context.append("No quality report has been generated for it yet.\n\n");
            return;
        }

        QualityReport latest = reports.stream()
                .max(Comparator.comparing(QualityReport::getGeneratedAt))
                .orElseThrow();
        context.append("Latest report: score ").append(latest.getQualityScore())
                .append("%, ").append(latest.getValidRows()).append(" valid / ")
                .append(latest.getInvalidRows()).append(" invalid out of ")
                .append(latest.getTotalRows()).append(" total rows.\n")
                .append("Per-check results (JSON): ").append(latest.getReportDetails()).append("\n\n");
    }

    private void appendRecentReports(StringBuilder context) {
        List<QualityReport> recent = qualityReportRepository
                .findAllByOrderByGeneratedAtDesc(PageRequest.of(0, RECENT_REPORTS_FALLBACK))
                .getContent();
        if (recent.isEmpty()) {
            return;
        }
        context.append("Most recently generated quality reports:\n");
        for (QualityReport report : recent) {
            context.append("- ").append(report.getDataset().getFileName())
                    .append(": score ").append(report.getQualityScore())
                    .append("%, ").append(report.getInvalidRows()).append(" anomalies\n");
        }
    }

    /** Matches a dataset filename (with or without extension) appearing in the user's message. */
    private Optional<Dataset> findMentionedDataset(String userMessage) {
        String lower = userMessage.toLowerCase();
        List<Dataset> candidates = datasetRepository
                .findAllByOrderByUploadedAtDesc(PageRequest.of(0, RECENT_DATASETS_TO_SCAN))
                .getContent();

        return candidates.stream()
                .filter(dataset -> lower.contains(dataset.getFileName().toLowerCase())
                        || lower.contains(stripExtension(dataset.getFileName()).toLowerCase()))
                .max(Comparator.comparingInt(dataset -> dataset.getFileName().length()));
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
