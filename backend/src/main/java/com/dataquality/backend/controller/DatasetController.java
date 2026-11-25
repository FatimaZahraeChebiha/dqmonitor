package com.dataquality.backend.controller;

import com.dataquality.backend.dto.PageResponse;
import com.dataquality.backend.dto.dataset.DatasetResponse;
import com.dataquality.backend.security.AuthPrincipal;
import com.dataquality.backend.security.ClientIpResolver;
import com.dataquality.backend.service.AnomalyCorrectionService;
import com.dataquality.backend.service.DatasetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
@Tag(name = "Datasets", description = "Upload, list, and trigger quality analysis on datasets")
public class DatasetController {

    private final DatasetService datasetService;
    private final AnomalyCorrectionService anomalyCorrectionService;

    @PostMapping("/upload")
    public ResponseEntity<DatasetResponse> uploadDataset(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest
    ) throws IOException {
        DatasetResponse dataset = datasetService.uploadDataset(file, principal.userId(), ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(dataset);
    }

    @GetMapping
    public ResponseEntity<PageResponse<DatasetResponse>> getAllDatasets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(datasetService.getDatasetsPage(PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DatasetResponse> getDatasetById(@PathVariable Long id) {
        return ResponseEntity.ok(datasetService.getDatasetResponseById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataset(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        datasetService.deleteDataset(id, principal.email(), ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/corrected-file")
    public ResponseEntity<byte[]> downloadCorrectedFile(@PathVariable Long id) {
        AnomalyCorrectionService.CorrectedFileDownload file = anomalyCorrectionService.getCorrectedFile(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.content());
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<Void> analyzeDataset(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        datasetService.analyzeDataset(id, principal.email(), ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.accepted().build();
    }
}
