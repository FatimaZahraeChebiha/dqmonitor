package com.dataquality.backend.dto.dashboard;

/**
 * dimension is a stable key (COMPLETENESS, VALIDITY, UNIQUENESS, CONFORMITY) -
 * the frontend maps it to a translated label, the same pattern already used for
 * error codes elsewhere in the API.
 */
public record QualityDimensionEntry(String dimension, double score) {}
