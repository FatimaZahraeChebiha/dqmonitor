package com.dataquality.backend.controller;

import com.dataquality.backend.dto.rule.ValidationRuleRequest;
import com.dataquality.backend.dto.rule.ValidationRuleResponse;
import com.dataquality.backend.security.AuthPrincipal;
import com.dataquality.backend.security.ClientIpResolver;
import com.dataquality.backend.service.ValidationRuleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Activates the previously dead ValidationRule entity: any authenticated user can
 * see which rules apply (viewing your organization's data contracts), only ADMIN
 * can define them. Consumed by the Python quality_check job via the separate
 * internal-key-protected /internal/validation-rules/active endpoint.
 */
@RestController
@RequestMapping("/api/validation-rules")
@RequiredArgsConstructor
@Tag(name = "Validation Rules", description = "Data-quality rules applied during analysis (admin-managed)")
public class ValidationRuleController {

    private final ValidationRuleService validationRuleService;

    @GetMapping
    public ResponseEntity<List<ValidationRuleResponse>> getAllRules() {
        return ResponseEntity.ok(validationRuleService.getAllRules());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ValidationRuleResponse> createRule(
            @Valid @RequestBody ValidationRuleRequest request,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        var created = validationRuleService.createRule(request, principal.email(), ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ValidationRuleResponse> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody ValidationRuleRequest request,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        var updated = validationRuleService.updateRule(id, request, principal.email(), ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRule(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        validationRuleService.deleteRule(id, principal.email(), ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.noContent().build();
    }
}
