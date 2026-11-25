package com.dataquality.backend.service;

import com.dataquality.backend.exception.InvalidCredentialsException;
import com.dataquality.backend.model.RefreshToken;
import com.dataquality.backend.model.User;
import com.dataquality.backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refresh tokens are opaque random strings validated against the database, not
 * self-contained JWTs - that makes them revocable (logout, password change) in a
 * way a stateless JWT refresh token wouldn't be.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-expiration-days}")
    private long refreshExpirationDays;

    public RefreshToken create(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(refreshExpirationDays));
        return refreshTokenRepository.save(refreshToken);
    }

    /** Validates the given token and rotates it (revokes the old one, issues a new one). */
    public RefreshToken rotate(String tokenValue) {
        RefreshToken existing = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (!existing.isValid()) {
            throw new InvalidCredentialsException("Refresh token expired or revoked");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        return create(existing.getUser());
    }

    public void revoke(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.revokeAllForUser(user);
    }
}
