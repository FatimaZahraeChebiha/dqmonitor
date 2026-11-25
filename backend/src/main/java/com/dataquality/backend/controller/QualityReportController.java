package com.dataquality.backend.controller;

import com.dataquality.backend.dto.PageResponse;
import com.dataquality.backend.dto.report.QualityReportResponse;
import com.dataquality.backend.model.QualityReport;
import com.dataquality.backend.security.AuthPrincipal;
import com.dataquality.backend.security.ClientIpResolver;
import com.dataquality.backend.service.PdfReportService;
import com.dataquality.backend.service.QualityReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Quality Reports", description = "Generated quality-check reports and their detail breakdowns")
public class QualityReportController {

    private static final Pattern NON_FILENAME_CHARS = Pattern.compile("[^a-zA-Z0-9-]+");

    private final QualityReportService qualityReportService;
    private final PdfReportService pdfReportService;

    @GetMapping
    public ResponseEntity<PageResponse<QualityReportResponse>> getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(qualityReportService.getReportsPage(PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<QualityReportResponse> getReportById(@PathVariable Long id) {
        return ResponseEntity.ok(qualityReportService.getReportById(id));
    }

    @GetMapping("/dataset/{datasetId}")
    public ResponseEntity<List<QualityReportResponse>> getReportsByDataset(@PathVariable Long datasetId) {
        return ResponseEntity.ok(qualityReportService.getReportsByDataset(datasetId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        qualityReportService.deleteReport(id, principal.email(), ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        QualityReport report = qualityReportService.getReportEntityById(id);
        byte[] pdf = pdfReportService.generate(report);

        String safeName = slugify(report.getDataset().getFileName());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("report-" + id + "-" + safeName + ".pdf", StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }

    private String slugify(String fileName) {
        String base = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = NON_FILENAME_CHARS.matcher(normalized).replaceAll("-").toLowerCase();
        return slug.isBlank() ? "dataset" : slug;
    }
}
