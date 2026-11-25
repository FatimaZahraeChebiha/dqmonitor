package com.dataquality.backend.dto.rule;

import com.dataquality.backend.model.RuleType;
import com.dataquality.backend.model.ValidationRule;

import java.time.LocalDateTime;

public record ValidationRuleResponse(
        Long id,
        String ruleName,
        String columnName,
        RuleType ruleType,
        String ruleValue,
        boolean isActive,
        String description,
        LocalDateTime createdAt
) {
    public static ValidationRuleResponse from(ValidationRule rule) {
        return new ValidationRuleResponse(
                rule.getId(),
                rule.getRuleName(),
                rule.getColumnName(),
                rule.getRuleType(),
                rule.getRuleValue(),
                Boolean.TRUE.equals(rule.getIsActive()),
                rule.getDescription(),
                rule.getCreatedAt()
        );
    }
}
