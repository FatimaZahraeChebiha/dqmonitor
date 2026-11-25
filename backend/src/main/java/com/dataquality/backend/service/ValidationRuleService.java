package com.dataquality.backend.service;

import com.dataquality.backend.dto.rule.ValidationRuleRequest;
import com.dataquality.backend.dto.rule.ValidationRuleResponse;
import com.dataquality.backend.exception.ResourceNotFoundException;
import com.dataquality.backend.model.AuditAction;
import com.dataquality.backend.model.ValidationRule;
import com.dataquality.backend.repository.ValidationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ValidationRuleService {

    private final ValidationRuleRepository validationRuleRepository;
    private final AuditService auditService;

    public List<ValidationRuleResponse> getAllRules() {
        return validationRuleRepository.findAll().stream()
                .map(ValidationRuleResponse::from)
                .toList();
    }

    public List<ValidationRuleResponse> getActiveRules() {
        return validationRuleRepository.findByIsActive(true).stream()
                .map(ValidationRuleResponse::from)
                .toList();
    }

    public ValidationRuleResponse createRule(ValidationRuleRequest request, String actorEmail, String ip) {
        ValidationRule rule = new ValidationRule();
        applyRequest(rule, request);
        rule = validationRuleRepository.save(rule);
        auditService.log(AuditAction.RULE_CREATED, actorEmail, "ValidationRule", rule.getId().toString(), rule.getRuleName(), ip);
        return ValidationRuleResponse.from(rule);
    }

    public ValidationRuleResponse updateRule(Long id, ValidationRuleRequest request, String actorEmail, String ip) {
        ValidationRule rule = validationRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Validation rule not found: " + id));
        applyRequest(rule, request);
        rule = validationRuleRepository.save(rule);
        auditService.log(AuditAction.RULE_UPDATED, actorEmail, "ValidationRule", rule.getId().toString(), rule.getRuleName(), ip);
        return ValidationRuleResponse.from(rule);
    }

    public void deleteRule(Long id, String actorEmail, String ip) {
        ValidationRule rule = validationRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Validation rule not found: " + id));
        validationRuleRepository.deleteById(id);
        auditService.log(AuditAction.RULE_DELETED, actorEmail, "ValidationRule", id.toString(), rule.getRuleName(), ip);
    }

    private void applyRequest(ValidationRule rule, ValidationRuleRequest request) {
        rule.setRuleName(request.ruleName());
        rule.setColumnName(request.columnName());
        rule.setRuleType(request.ruleType());
        rule.setRuleValue(request.ruleValue());
        rule.setDescription(request.description());
        rule.setIsActive(request.isActive() == null || request.isActive());
    }
}
