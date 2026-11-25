package com.dataquality.backend.model;

/**
 * Maps 1:1 to a Great Expectations expectation built dynamically in quality_check.py.
 */
public enum RuleType {
    NOT_NULL,
    UNIQUE,
    REGEX,
    RANGE,
    ENUM,
    REQUIRED_COLUMN
}
