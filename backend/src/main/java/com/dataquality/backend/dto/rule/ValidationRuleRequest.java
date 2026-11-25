package com.dataquality.backend.dto.rule;

import com.dataquality.backend.model.RuleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ValidationRuleRequest(
        @NotBlank String ruleName,
        @NotBlank String columnName,
        @NotNull RuleType ruleType,
        String ruleValue,
        Boolean isActive,
        String description
) {}
