package com.dataquality.backend.service;

import com.dataquality.backend.dto.PageResponse;
import com.dataquality.backend.dto.internal.InternalReportRequest;
import com.dataquality.backend.dto.report.QualityReportResponse;
import com.dataquality.backend.exception.ResourceNotFoundException;
import com.dataquality.backend.dto.internal.InternalAnomalyEntry;
import com.dataquality.backend.model.AuditAction;
import com.dataquality.backend.model.Dataset;
import com.dataquality.backend.model.DatasetStatus;
import com.dataquality.backend.model.QualityReport;
import com.dataquality.backend.model.ReportAnomaly;
import com.dataquality.backend.repository.DatasetRepository;
import com.dataquality.backend.repository.QualityReportRepository;
import com.dataquality.backend.repository.ReportAnomalyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QualityReportService {

    private final QualityReportRepository qualityReportRepository;
    private final DatasetRepository datasetRepository;
    private final ReportAnomalyRepository reportAnomalyRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    @Value("${app.notifications.low-quality-threshold}")
    private double lowQualityThreshold;

    public PageResponse<QualityReportResponse> getReportsPage(Pageable pageable) {
        return PageResponse.from(qualityReportRepository.findAllByOrderByGeneratedAtDesc(pageable), QualityReportResponse::from);
    }

    public List<QualityReportResponse> getReportsByDataset(Long datasetId) {
        return qualityReportRepository.findByDatasetId(datasetId).stream()
                .map(QualityReportResponse::from)
                .toList();
    }

    public QualityReportResponse getReportById(Long id) {
        return QualityReportResponse.from(getReportEntityById(id));
    }

    public QualityReport getReportEntityById(Long id) {
        return qualityReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
    }

    /**
     * Deletes exactly this report row - the dataset and its other reports are
     * untouched (QualityReport has no child rows of its own, so this is a plain
     * single-row delete, not a cascade). The dashboard cache is evicted so
     * aggregates (score, trend, "needs attention", etc.) recompute without this
     * report on the next request.
     */
    @Transactional
    @CacheEvict(value = "dashboardSummary", allEntries = true)
    public void deleteReport(Long id, String actorEmail, String ip) {
        QualityReport report = getReportEntityById(id);
        qualityReportRepository.deleteById(id);
        auditService.log(AuditAction.REPORT_DELETED, actorEmail, "QualityReport", id.toString(),
                report.getDataset().getFileName(), ip);
    }

    /**
     * Ingests the result posted back by the Python quality_check job. This is the
     * single owner of report-creation + dataset status transitions - the Python
     * side no longer writes to MySQL directly, so the schema has exactly one owner.
     */
    @Transactional
    @CacheEvict(value = "dashboardSummary", allEntries = true)
    public void applyInternalReport(InternalReportRequest request) {
        Dataset dataset = datasetRepository.findById(request.datasetId())
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found: " + request.datasetId()));

        if (!request.success()) {
            dataset.setStatus(DatasetStatus.FAILED);
            datasetRepository.save(dataset);
            return;
        }

        QualityReport report = new QualityReport();
        report.setDataset(dataset);
        report.setQualityScore(request.qualityScore());
        report.setTotalRows(request.totalRows());
        report.setValidRows(request.validRows());
        report.setInvalidRows(request.invalidRows());
        report.setReportDetails(request.reportDetails());
        // Non-null means the Python job posted its (possibly empty) anomaly
        // list - i.e. the new pipeline actually ran, as opposed to an older
        // report row that predates this field entirely.
        report.setAnomaliesTracked(request.anomalies() != null);
        report = qualityReportRepository.save(report);

        saveAnomalies(report, request.anomalies());

        dataset.setStatus(DatasetStatus.COMPLETED);
        datasetRepository.save(dataset);

        notifyUploader(dataset, request);
    }

    /** Persists the full per-row anomaly list backing the Anomaly Explorer - absent/empty is valid (a perfect report has none). */
    private void saveAnomalies(QualityReport report, List<InternalAnomalyEntry> anomalies) {
        if (anomalies == null || anomalies.isEmpty()) {
            return;
        }
        List<ReportAnomaly> entities = anomalies.stream().map(entry -> {
            ReportAnomaly anomaly = new ReportAnomaly();
            anomaly.setReport(report);
            anomaly.setRowIndex(entry.rowIndex());
            anomaly.setColumnName(entry.column());
            anomaly.setRuleName(entry.ruleName());
            anomaly.setCheckType(entry.checkType());
            anomaly.setAnomalyValue(entry.value());
            return anomaly;
        }).toList();
        reportAnomalyRepository.saveAll(entities);
    }

    private void notifyUploader(Dataset dataset, InternalReportRequest request) {
        if (dataset.getUploadedBy() == null || request.qualityScore() == null) {
            return;
        }
        String email = dataset.getUploadedBy().getEmail();
        double score = request.qualityScore();

        emailService.sendAnalysisCompleteEmail(email, dataset.getFileName(), score, request.invalidRows());
        if (score < lowQualityThreshold) {
            emailService.sendLowQualityAlertEmail(email, dataset.getFileName(), score, lowQualityThreshold);
        }
    }
}
