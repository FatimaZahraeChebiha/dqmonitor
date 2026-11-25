package com.dataquality.backend.dto.dashboard;

public record ColumnAnomalyEntry(String columnName, long count, double percentage) {}
