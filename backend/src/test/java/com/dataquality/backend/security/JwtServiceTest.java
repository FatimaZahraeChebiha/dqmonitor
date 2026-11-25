package com.dataquality.backend.security;

import com.dataquality.backend.model.Role;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-must-be-long-enough-for-hs512-signing-1234567890";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 900_000L);
    }

    @Test
    void generatedTokenEncodesSubjectUserIdAndRole() {
        String token = jwtService.generateToken(42L, "admin@dqmonitor.com", Role.ADMIN);

        assertThat(jwtService.extractEmail(token)).isEqualTo("admin@dqmonitor.com");
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.extractRole(token)).isEqualTo(Role.ADMIN);
    }

    @Test
    void freshlyIssuedTokenIsValid() {
        String token = jwtService.generateToken(1L, "user@dqmonitor.com", Role.USER);

        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    void expiredTokenIsNotValid() {
        JwtService shortLived = new JwtService(SECRET, -1_000L);
        String token = shortLived.generateToken(1L, "user@dqmonitor.com", Role.USER);

        assertThat(shortLived.isValid(token)).isFalse();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtService other = new JwtService("a-completely-different-secret-key-of-sufficient-length-000000", 900_000L);
        String token = other.generateToken(1L, "user@dqmonitor.com", Role.USER);

        assertThatThrownBy(() -> jwtService.extractEmail(token)).isInstanceOf(SignatureException.class);
    }
}
