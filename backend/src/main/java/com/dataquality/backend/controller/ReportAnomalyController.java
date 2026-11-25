package com.dataquality.backend.controller;

import com.dataquality.backend.dto.PageResponse;
import com.dataquality.backend.dto.anomaly.AnomalyResponse;
import com.dataquality.backend.dto.anomaly.AnomalyStatsResponse;
import com.dataquality.backend.service.ReportAnomalyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/reports/{reportId}/anomalies")
@RequiredArgsConstructor
@Tag(name = "Anomaly Explorer", description = "Full per-row anomaly detail for a report: search, filter, group, export")
public class ReportAnomalyController {

    private final ReportAnomalyService anomalyService;

    @GetMapping
    public ResponseEntity<PageResponse<AnomalyResponse>> search(
            @PathVariable Long reportId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String column,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) String checkType,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(anomalyService.search(reportId, page, size, column, ruleName, checkType, search));
    }

    @GetMapping("/stats")
    public ResponseEntity<AnomalyStatsResponse> getStats(@PathVariable Long reportId) {
        return ResponseEntity.ok(anomalyService.getStats(reportId));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @PathVariable Long reportId,
            @RequestParam(required = false) String column,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) String checkType,
            @RequestParam(required = false) String search
    ) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(buffer, StandardCharsets.UTF_8)) {
            anomalyService.exportCsv(reportId, column, ruleName, checkType, search, writer);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"anomalies-report-" + reportId + ".csv\"")
                .body(buffer.toByteArray());
    }
}
