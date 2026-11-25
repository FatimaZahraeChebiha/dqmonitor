package com.dataquality.backend.controller;

import com.dataquality.backend.dto.PageResponse;
import com.dataquality.backend.dto.audit.AuditLogResponse;
import com.dataquality.backend.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Audit Logs", description = "Admin-only audit trail of security-relevant actions")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<PageResponse<AuditLogResponse>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        var pageResult = auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 100)));
        return ResponseEntity.ok(PageResponse.from(pageResult, AuditLogResponse::from));
    }
}
