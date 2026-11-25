package com.dataquality.backend.service;

import com.dataquality.backend.dto.auth.*;
import com.dataquality.backend.exception.DuplicateEmailException;
import com.dataquality.backend.exception.InvalidCredentialsException;
import com.dataquality.backend.exception.TooManyRequestsException;
import com.dataquality.backend.model.PasswordResetToken;
import com.dataquality.backend.model.RefreshToken;
import com.dataquality.backend.model.Role;
import com.dataquality.backend.model.User;
import com.dataquality.backend.repository.PasswordResetTokenRepository;
import com.dataquality.backend.repository.UserRepository;
import com.dataquality.backend.security.JwtService;
import com.dataquality.backend.security.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private AuditService auditService;
    @Mock private EmailService emailService;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;

    @InjectMocks
    private AuthService authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setId(1L);
        activeUser.setEmail("user@dqmonitor.com");
        activeUser.setPassword("hashed-password");
        activeUser.setRole(Role.USER);
        activeUser.setIsActive(true);
    }

    @Test
    void loginRejectsWhenAccountIsRateLimited() {
        when(loginAttemptService.isBlocked("user@dqmonitor.com", "1.2.3.4")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@dqmonitor.com", "whatever"), "1.2.3.4"))
                .isInstanceOf(TooManyRequestsException.class);

        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void loginRejectsWrongPasswordAndRecordsFailure() {
        when(loginAttemptService.isBlocked(anyString(), anyString())).thenReturn(false);
        when(userRepository.findByEmail("user@dqmonitor.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@dqmonitor.com", "wrong-password"), "1.2.3.4"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailure("user@dqmonitor.com", "1.2.3.4");
        verify(loginAttemptService, never()).recordSuccess(anyString(), anyString());
    }

    @Test
    void loginRejectsDeactivatedAccountEvenWithCorrectPassword() {
        activeUser.setIsActive(false);
        when(loginAttemptService.isBlocked(anyString(), anyString())).thenReturn(false);
        when(userRepository.findByEmail("user@dqmonitor.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@dqmonitor.com", "correct-password"), "1.2.3.4"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginSucceedsAndIssuesTokens() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token-value");

        when(loginAttemptService.isBlocked(anyString(), anyString())).thenReturn(false);
        when(userRepository.findByEmail("user@dqmonitor.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(1L, "user@dqmonitor.com", Role.USER)).thenReturn("access-token-value");
        when(jwtService.getExpirationMs()).thenReturn(900_000L);
        when(refreshTokenService.create(activeUser)).thenReturn(refreshToken);

        LoginResponse response = authService.login(new LoginRequest("user@dqmonitor.com", "correct-password"), "1.2.3.4");

        assertThat(response.token()).isEqualTo("access-token-value");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-value");
        verify(loginAttemptService).recordSuccess("user@dqmonitor.com", "1.2.3.4");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.findByEmail("user@dqmonitor.com")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("newuser", "user@dqmonitor.com", "password123"), "1.2.3.4"))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerAlwaysAssignsUserRoleRegardlessOfInput() {
        when(userRepository.findByEmail("new@dqmonitor.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        authService.register(new RegisterRequest("newuser", "new@dqmonitor.com", "password123"), "1.2.3.4");

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed");
    }

    @Test
    void resetPasswordRejectsUnknownToken() {
        when(passwordResetTokenRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("bad-token", "newPassword123"), "1.2.3.4"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        PasswordResetToken expired = new PasswordResetToken();
        expired.setToken("expired-token");
        expired.setUser(activeUser);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("expired-token", "newPassword123"), "1.2.3.4"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPasswordUpdatesPasswordAndRevokesAllSessions() {
        PasswordResetToken valid = new PasswordResetToken();
        valid.setToken("valid-token");
        valid.setUser(activeUser);
        valid.setExpiresAt(LocalDateTime.now().plusMinutes(30));

        when(passwordResetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(valid));
        when(passwordEncoder.encode("newPassword123")).thenReturn("new-hashed-password");

        authService.resetPassword(new ResetPasswordRequest("valid-token", "newPassword123"), "1.2.3.4");

        assertThat(activeUser.getPassword()).isEqualTo("new-hashed-password");
        assertThat(valid.getUsed()).isTrue();
        verify(refreshTokenService).revokeAllForUser(activeUser);
    }

    @Test
    void forgotPasswordDoesNothingObservableForUnknownEmailToAvoidEnumeration() {
        when(userRepository.findByEmail("nobody@dqmonitor.com")).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequest("nobody@dqmonitor.com"), "1.2.3.4");

        verifyNoInteractions(emailService);
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void forgotPasswordCreatesTokenAndSendsEmailForKnownUser() {
        when(userRepository.findByEmail("user@dqmonitor.com")).thenReturn(Optional.of(activeUser));

        authService.forgotPassword(new ForgotPasswordRequest("user@dqmonitor.com"), "1.2.3.4");

        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq("user@dqmonitor.com"), anyString());
    }
}
