package com.dataquality.backend.dto.auth;

public record RefreshResponse(String token, long expiresInMs, String refreshToken) {}
