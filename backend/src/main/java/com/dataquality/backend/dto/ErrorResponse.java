package com.dataquality.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consistent error envelope for every failure response.
 * `code` is a stable, machine-readable identifier (e.g. "DUPLICATE_EMAIL") that the
 * frontend maps to a translated message via i18n - the backend never sends
 * user-facing text directly, since that text needs to exist in FR/EN/AR.
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String code) {}

    public static ErrorResponse of(int status, String code, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, code, message, path, List.of());
    }

    public static ErrorResponse of(int status, String code, String message, String path, List<FieldError> fieldErrors) {
        return new ErrorResponse(LocalDateTime.now(), status, code, message, path, fieldErrors);
    }
}
