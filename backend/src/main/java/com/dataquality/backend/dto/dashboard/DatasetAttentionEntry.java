package com.dataquality.backend.dto.dashboard;

import java.time.LocalDateTime;

/**
 * One dataset whose latest report scored below the compliant threshold,
 * worst-first - reportId lets the frontend link straight to that specific
 * report instead of just naming the problem and leaving the user to go find
 * it themselves.
 */
public record DatasetAttentionEntry(
        Long reportId,
        String fileName,
        double score,
        LocalDateTime generatedAt
) {}
