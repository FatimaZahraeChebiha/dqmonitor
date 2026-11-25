package com.dataquality.backend.controller;

import com.dataquality.backend.dto.correction.ApplyCorrectionsRequest;
import com.dataquality.backend.dto.correction.CorrectionResponse;
import com.dataquality.backend.model.QualityReport;
import com.dataquality.backend.security.AuthPrincipal;
import com.dataquality.backend.security.ClientIpResolver;
import com.dataquality.backend.service.AnomalyCorrectionService;
import com.dataquality.backend.service.QualityReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports/{reportId}/corrections")
@RequiredArgsConstructor
@Tag(name = "Anomaly Corrections", description = "Direct cell-level correction of anomalies, with full audit trail")
public class AnomalyCorrectionController {

    private final AnomalyCorrectionService correctionService;
    private final QualityReportService qualityReportService;

    @PostMapping
    public ResponseEntity<List<CorrectionResponse>> apply(
            @PathVariable Long reportId,
            @Valid @RequestBody ApplyCorrectionsRequest request,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        List<CorrectionResponse> applied = correctionService.applyCorrections(
                reportId, request.corrections(), principal.email(), ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(applied);
    }

    @GetMapping
    public ResponseEntity<List<CorrectionResponse>> history(@PathVariable Long reportId) {
        QualityReport report = qualityReportService.getReportEntityById(reportId);
        return ResponseEntity.ok(correctionService.getHistory(report.getDataset().getId()));
    }
}
