package com.dataquality.backend.controller;

import com.dataquality.backend.model.AuditAction;
import com.dataquality.backend.repository.DatasetRepository;
import com.dataquality.backend.repository.QualityReportRepository;
import com.dataquality.backend.security.AuthPrincipal;
import com.dataquality.backend.security.ClientIpResolver;
import com.dataquality.backend.service.AuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Destructive, admin-only operations - deliberately isolated from routine dataset/report
 * endpoints instead of living next to everyday actions. Enforced here server-side with
 * @PreAuthorize, not just hidden behind a client-side role check as before.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Destructive, admin-only operations (danger zone)")
public class AdminController {

    private final DatasetRepository datasetRepository;
    private final QualityReportRepository qualityReportRepository;
    private final AuditService auditService;

    @DeleteMapping("/reset-database")
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = "dashboardSummary", allEntries = true)
    public ResponseEntity<Void> resetDatabase(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest httpRequest) {
        long datasetCount = datasetRepository.count();
        long reportCount = qualityReportRepository.count();

        qualityReportRepository.deleteAll();
        datasetRepository.deleteAll();

        auditService.log(
                AuditAction.DATABASE_RESET, principal.email(), null, null,
                "datasets=" + datasetCount + ", reports=" + reportCount, ClientIpResolver.resolve(httpRequest)
        );
        return ResponseEntity.noContent().build();
    }
}
