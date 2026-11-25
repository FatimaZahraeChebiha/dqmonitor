package com.dataquality.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "validation_rules")
@Data
public class ValidationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ruleName;
    private String columnName;

    @Enumerated(EnumType.STRING)
    private RuleType ruleType;

    /**
     * Interpretation depends on ruleType: regex pattern, "min,max" for RANGE,
     * comma-separated allowed values for ENUM, unused for NOT_NULL/UNIQUE/REQUIRED_COLUMN.
     */
    private String ruleValue;

    private Boolean isActive = true;
    private String description;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
