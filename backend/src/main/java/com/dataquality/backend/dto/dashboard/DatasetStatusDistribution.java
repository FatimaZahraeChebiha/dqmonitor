package com.dataquality.backend.dto.dashboard;

/**
 * Counts datasets (by their latest report's score) into the same three bands
 * used everywhere else in the app - compliant >=80, warning >=50, critical
 * below that (see DashboardService.STATUS_* thresholds, mirrored from the
 * frontend's scoreClass() helper) - so a dataset is never "green" on one
 * screen and "orange" on another.
 */
public record DatasetStatusDistribution(long compliant, long warning, long critical) {}
