package com.dataquality.backend.service;

import com.dataquality.backend.dto.correction.CorrectionRequest;
import com.dataquality.backend.dto.correction.CorrectionResponse;
import com.dataquality.backend.exception.ResourceNotFoundException;
import com.dataquality.backend.exception.UnsupportedFileTypeException;
import com.dataquality.backend.model.AnomalyCorrection;
import com.dataquality.backend.model.AuditAction;
import com.dataquality.backend.model.Dataset;
import com.dataquality.backend.model.QualityReport;
import com.dataquality.backend.repository.AnomalyCorrectionRepository;
import com.dataquality.backend.repository.DatasetRepository;
import com.dataquality.backend.repository.QualityReportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AnomalyCorrectionService {

    private final QualityReportRepository qualityReportRepository;
    private final DatasetRepository datasetRepository;
    private final AnomalyCorrectionRepository correctionRepository;
    private final FileCorrectionService fileCorrectionService;
    private final AuditService auditService;

    @Value("${app.storage.upload-dir}")
    private String uploadDir;

    public AnomalyCorrectionService(
            QualityReportRepository qualityReportRepository,
            DatasetRepository datasetRepository,
            AnomalyCorrectionRepository correctionRepository,
            FileCorrectionService fileCorrectionService,
            AuditService auditService
    ) {
        this.qualityReportRepository = qualityReportRepository;
        this.datasetRepository = datasetRepository;
        this.correctionRepository = correctionRepository;
        this.fileCorrectionService = fileCorrectionService;
        this.auditService = auditService;
    }

    /**
     * Applies every requested cell correction to a fresh (or already-existing)
     * corrected copy of the dataset's file - the original upload is never
     * touched. Corrections accumulate: a second batch edits the already-
     * corrected file, not a fresh copy of the original.
     */
    @Transactional
    @CacheEvict(value = "dashboardSummary", allEntries = true)
    public List<CorrectionResponse> applyCorrections(Long reportId, List<CorrectionRequest> corrections, String actorEmail, String ip) {
        QualityReport report = qualityReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId));
        Dataset dataset = report.getDataset();

        String extension = extractExtension(dataset.getFileName());
        if (!fileCorrectionService.isSupported(extension)) {
            throw new UnsupportedFileTypeException("La correction directe n'est pas supportée pour les fichiers ." + extension);
        }

        Path source = resolveCurrentFile(dataset);
        Path target = resolveCorrectedTarget(dataset, extension);

        List<FileCorrectionService.AppliedCorrection> applied;
        try {
            applied = fileCorrectionService.apply(source, target, extension, corrections);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible d'écrire le fichier corrigé", e);
        }

        dataset.setCorrectedFilePath(target.toString());
        dataset.setCorrectedStoredFileName(target.getFileName().toString());
        dataset.setLastCorrectedAt(LocalDateTime.now());
        datasetRepository.save(dataset);

        List<AnomalyCorrection> entities = applied.stream().map(a -> {
            AnomalyCorrection entity = new AnomalyCorrection();
            entity.setDataset(dataset);
            entity.setReport(report);
            entity.setRowIndex(a.rowIndex());
            entity.setColumnName(a.columnName());
            entity.setOldValue(a.oldValue());
            entity.setNewValue(a.newValue());
            entity.setCorrectedBy(actorEmail);
            return entity;
        }).toList();
        correctionRepository.saveAll(entities);

        auditService.log(AuditAction.ANOMALY_CORRECTED, actorEmail, "Dataset", dataset.getId().toString(),
                entities.size() + " cellule(s) corrigée(s) dans " + dataset.getFileName(), ip);

        return entities.stream().map(CorrectionResponse::from).toList();
    }

    public List<CorrectionResponse> getHistory(Long datasetId) {
        return correctionRepository.findByDatasetIdOrderByCorrectedAtDesc(datasetId).stream()
                .map(CorrectionResponse::from)
                .toList();
    }

    public record CorrectedFileDownload(byte[] content, String fileName, String contentType) {}

    public CorrectedFileDownload getCorrectedFile(Long datasetId) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found: " + datasetId));
        if (dataset.getCorrectedFilePath() == null) {
            throw new ResourceNotFoundException("Aucun fichier corrigé pour ce dataset: " + datasetId);
        }
        try {
            byte[] content = Files.readAllBytes(Paths.get(dataset.getCorrectedFilePath()));
            String extension = extractExtension(dataset.getFileName());
            String contentType = switch (extension) {
                case "json" -> "application/json";
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                default -> "text/csv";
            };
            return new CorrectedFileDownload(content, "corrige-" + dataset.getFileName(), contentType);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de lire le fichier corrigé", e);
        }
    }

    /** Corrections apply on top of the current corrected copy if one already exists, else the original upload. */
    private Path resolveCurrentFile(Dataset dataset) {
        return Paths.get(dataset.getCorrectedFilePath() != null ? dataset.getCorrectedFilePath() : dataset.getFilePath());
    }

    private Path resolveCorrectedTarget(Dataset dataset, String extension) {
        String storedFileName = dataset.getCorrectedStoredFileName() != null
                ? dataset.getCorrectedStoredFileName()
                : UUID.randomUUID() + "-corrected." + extension;
        return Paths.get(uploadDir).resolve(storedFileName);
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
