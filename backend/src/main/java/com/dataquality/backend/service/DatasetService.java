package com.dataquality.backend.service;

import com.dataquality.backend.dto.PageResponse;
import com.dataquality.backend.dto.dataset.DatasetResponse;
import com.dataquality.backend.dto.internal.AnalyzeJobRequest;
import com.dataquality.backend.dto.internal.PendingDatasetResponse;
import com.dataquality.backend.exception.ResourceNotFoundException;
import com.dataquality.backend.exception.UnsupportedFileTypeException;
import com.dataquality.backend.model.AuditAction;
import com.dataquality.backend.model.Dataset;
import com.dataquality.backend.model.DatasetStatus;
import com.dataquality.backend.model.User;
import com.dataquality.backend.repository.DatasetRepository;
import com.dataquality.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DatasetService {

    private static final Logger log = LoggerFactory.getLogger(DatasetService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("csv", "json", "xlsx");

    private final DatasetRepository datasetRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    // The JDK HttpClient-backed default request factory sends "Expect: 100-continue"
    // for POST bodies, which uvicorn (the analysis service) does not answer -
    // the request then goes out with no body at all. SimpleClientHttpRequestFactory
    // (HttpURLConnection-based) doesn't have this behavior.
    private final RestClient restClient = RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();

    @Value("${app.storage.upload-dir}")
    private String uploadDir;

    @Value("${app.analysis.python-executable}")
    private String pythonExecutable;

    @Value("${app.analysis.script-path}")
    private String scriptPath;

    // When set (Docker/production), the analysis job runs as a separate FastAPI
    // microservice reached over HTTP. When empty (local dev without Docker),
    // fall back to spawning the Python script directly via ProcessBuilder.
    @Value("${app.analysis.service-url}")
    private String analysisServiceUrl;

    @Autowired
    public DatasetService(DatasetRepository datasetRepository, UserRepository userRepository, AuditService auditService) {
        this.datasetRepository = datasetRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Test-only constructor: builds a DatasetService with just enough state to
     * exercise buildProcessBuilder() in isolation, without needing to mock the
     * repository/audit dependencies that the local-dev ProcessBuilder path never
     * touches. Never used by Spring - the @Autowired constructor above is what
     * gets wired at runtime.
     */
    DatasetService(DatasetRepository datasetRepository, String uploadDir, String pythonExecutable, String scriptPath) {
        this.datasetRepository = datasetRepository;
        this.userRepository = null;
        this.auditService = null;
        this.uploadDir = uploadDir;
        this.pythonExecutable = pythonExecutable;
        this.scriptPath = scriptPath;
    }

    @CacheEvict(value = "dashboardSummary", allEntries = true)
    public DatasetResponse uploadDataset(MultipartFile file, Long uploadedByUserId, String ip) throws IOException {
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        String extension = extractExtension(originalName).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new UnsupportedFileTypeException("Unsupported file type: ." + extension);
        }

        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Never derive the on-disk filename from user input - avoids path traversal
        // via a crafted "originalFilename" (e.g. "../../evil.sh").
        String storedFileName = UUID.randomUUID() + "." + extension;
        Path filePath = uploadPath.resolve(storedFileName);
        Files.write(filePath, file.getBytes());

        User uploadedBy = uploadedByUserId != null ? userRepository.findById(uploadedByUserId).orElse(null) : null;

        Dataset dataset = new Dataset();
        dataset.setFileName(originalName);
        dataset.setStoredFileName(storedFileName);
        dataset.setFilePath(filePath.toString());
        dataset.setFileSize(file.getSize());
        dataset.setUploadedBy(uploadedBy);
        dataset = datasetRepository.save(dataset);

        auditService.log(AuditAction.DATASET_UPLOADED, uploadedBy != null ? uploadedBy.getEmail() : null,
                "Dataset", dataset.getId().toString(), originalName, ip);

        return DatasetResponse.from(dataset);
    }

    public PageResponse<DatasetResponse> getDatasetsPage(Pageable pageable) {
        return PageResponse.from(datasetRepository.findAllByOrderByUploadedAtDesc(pageable), DatasetResponse::from);
    }

    public DatasetResponse getDatasetResponseById(Long id) {
        return DatasetResponse.from(getDatasetById(id));
    }

    public Dataset getDatasetById(Long id) {
        return datasetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found: " + id));
    }

    @CacheEvict(value = "dashboardSummary", allEntries = true)
    public void deleteDataset(Long id, String actorEmail, String ip) {
        Dataset dataset = getDatasetById(id);
        datasetRepository.deleteById(id);
        auditService.log(AuditAction.DATASET_DELETED, actorEmail, "Dataset", id.toString(), dataset.getFileName(), ip);
    }

    /** Datasets uploaded but never analyzed (manually or otherwise) - swept up by the Airflow scheduled DAG. */
    public List<PendingDatasetResponse> getPendingDatasets() {
        return datasetRepository.findByStatusOrderByUploadedAtAsc(DatasetStatus.PENDING).stream()
                .map(PendingDatasetResponse::from)
                .toList();
    }

    public void analyzeDataset(Long datasetId, String actorEmail, String ip) {
        Dataset dataset = getDatasetById(datasetId);
        dataset.setStatus(DatasetStatus.PROCESSING);
        datasetRepository.save(dataset);
        auditService.log(AuditAction.DATASET_ANALYZED, actorEmail, "Dataset", datasetId.toString(), dataset.getFileName(), ip);

        if (StringUtils.hasText(analysisServiceUrl)) {
            triggerViaHttp(dataset);
        } else {
            triggerViaProcess(dataset);
        }
    }

    /** Re-analysis after a correction must verify the CORRECTED file, not the untouched original. */
    private String resolveAnalysisFilePath(Dataset dataset) {
        return dataset.getCorrectedFilePath() != null ? dataset.getCorrectedFilePath() : dataset.getFilePath();
    }

    private void triggerViaHttp(Dataset dataset) {
        Long datasetId = dataset.getId();
        try {
            restClient.post()
                    .uri(analysisServiceUrl + "/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AnalyzeJobRequest(datasetId, resolveAnalysisFilePath(dataset)))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Submitted dataset {} to analysis service at {}", datasetId, analysisServiceUrl);
        } catch (Exception e) {
            dataset.setStatus(DatasetStatus.FAILED);
            datasetRepository.save(dataset);
            log.error("Failed to submit dataset {} to analysis service at {}", datasetId, analysisServiceUrl, e);
        }
    }

    /** Package-private for testing: the ProcessBuilder construction, without actually starting it. */
    ProcessBuilder buildProcessBuilder(Long datasetId, String filePath) {
        ProcessBuilder pb = new ProcessBuilder(pythonExecutable, scriptPath, datasetId.toString(), filePath);
        pb.redirectErrorStream(true);
        return pb;
    }

    private void triggerViaProcess(Dataset dataset) {
        Long datasetId = dataset.getId();
        try {
            ProcessBuilder pb = buildProcessBuilder(datasetId, resolveAnalysisFilePath(dataset));
            Process process = pb.start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("[quality_check:{}] {}", datasetId, line);
                    }
                } catch (IOException e) {
                    log.error("Error reading quality_check output for dataset {}", datasetId, e);
                }
            }, "quality-check-" + datasetId).start();

        } catch (IOException e) {
            dataset.setStatus(DatasetStatus.FAILED);
            datasetRepository.save(dataset);
            log.error("Failed to launch analysis job for dataset {}", datasetId, e);
        }
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }
}
