package com.dataquality.backend.controller;

import com.dataquality.backend.dto.internal.InternalReportRequest;
import com.dataquality.backend.dto.internal.PendingDatasetResponse;
import com.dataquality.backend.dto.rule.ValidationRuleResponse;
import com.dataquality.backend.service.DatasetService;
import com.dataquality.backend.service.QualityReportService;
import com.dataquality.backend.service.ValidationRuleService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Trusted, loopback-only endpoints for the Python quality_check job and the Airflow
 * scheduled-analysis DAG. Not reachable by the browser client - protected by
 * InternalApiKeyFilter (X-Internal-Key), not JWT. Hidden from Swagger UI since it's
 * a service-to-service contract, not part of the browsable API surface.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Hidden
public class InternalController {

    private static final String SCHEDULER_ACTOR = "system:airflow-scheduler";
    private static final String SCHEDULER_IP = "internal";

    private final ValidationRuleService validationRuleService;
    private final QualityReportService qualityReportService;
    private final DatasetService datasetService;

    @GetMapping("/validation-rules/active")
    public ResponseEntity<List<ValidationRuleResponse>> getActiveRules() {
        return ResponseEntity.ok(validationRuleService.getActiveRules());
    }

    @PostMapping("/reports")
    public ResponseEntity<Void> submitReport(@Valid @RequestBody InternalReportRequest request) {
        qualityReportService.applyInternalReport(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/datasets/pending")
    public ResponseEntity<List<PendingDatasetResponse>> getPendingDatasets() {
        return ResponseEntity.ok(datasetService.getPendingDatasets());
    }

    @PostMapping("/datasets/{id}/analyze")
    public ResponseEntity<Void> analyzeDataset(@PathVariable Long id) {
        datasetService.analyzeDataset(id, SCHEDULER_ACTOR, SCHEDULER_IP);
        return ResponseEntity.accepted().build();
    }
}
