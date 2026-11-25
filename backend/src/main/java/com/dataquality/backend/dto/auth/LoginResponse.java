package com.dataquality.backend.dto.auth;

import com.dataquality.backend.dto.user.UserResponse;

public record LoginResponse(
        String token,
        long expiresInMs,
        String refreshToken,
        UserResponse user
) {}
