package com.dataquality.backend.service;

import com.dataquality.backend.dto.auth.*;
import com.dataquality.backend.dto.user.UserResponse;
import com.dataquality.backend.exception.DuplicateEmailException;
import com.dataquality.backend.exception.InvalidCredentialsException;
import com.dataquality.backend.exception.TooManyRequestsException;
import com.dataquality.backend.model.AuditAction;
import com.dataquality.backend.model.PasswordResetToken;
import com.dataquality.backend.model.RefreshToken;
import com.dataquality.backend.model.Role;
import com.dataquality.backend.model.User;
import com.dataquality.backend.repository.PasswordResetTokenRepository;
import com.dataquality.backend.repository.UserRepository;
import com.dataquality.backend.security.JwtService;
import com.dataquality.backend.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int PASSWORD_RESET_VALID_HOURS = 1;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public LoginResponse login(LoginRequest request, String ip) {
        if (loginAttemptService.isBlocked(request.email(), ip)) {
            auditService.log(AuditAction.LOGIN_BLOCKED, request.email(), "User", null, "Too many failed attempts", ip);
            throw new TooManyRequestsException("Too many failed login attempts - try again later");
        }

        Optional<User> userOpt = userRepository.findByEmail(request.email());

        boolean valid = userOpt.isPresent()
                && passwordEncoder.matches(request.password(), userOpt.get().getPassword())
                && Boolean.TRUE.equals(userOpt.get().getIsActive());

        if (!valid) {
            loginAttemptService.recordFailure(request.email(), ip);
            auditService.log(AuditAction.LOGIN_FAILURE, request.email(), "User", null, null, ip);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        User user = userOpt.get();
        loginAttemptService.recordSuccess(request.email(), ip);
        auditService.log(AuditAction.LOGIN_SUCCESS, user.getEmail(), "User", user.getId().toString(), null, ip);

        return buildLoginResponse(user);
    }

    /** Public self-registration - always creates a USER account, never ADMIN. */
    public UserResponse register(RegisterRequest request, String ip) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException("An account with this email already exists");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        user.setIsActive(true);
        user = userRepository.save(user);

        auditService.log(AuditAction.USER_REGISTERED, user.getEmail(), "User", user.getId().toString(), null, ip);
        return UserResponse.from(user);
    }

    public RefreshResponse refresh(RefreshRequest request) {
        RefreshToken rotated = refreshTokenService.rotate(request.refreshToken());
        User user = rotated.getUser();
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new RefreshResponse(accessToken, jwtService.getExpirationMs(), rotated.getToken());
    }

    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    /** Always succeeds from the caller's perspective, whether or not the email exists - prevents user enumeration. */
    public void forgotPassword(ForgotPasswordRequest request, String ip) {
        Optional<User> userOpt = userRepository.findByEmail(request.email());
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for unknown email");
            return;
        }

        User user = userOpt.get();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setExpiresAt(LocalDateTime.now().plusHours(PASSWORD_RESET_VALID_HOURS));
        passwordResetTokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(user.getEmail(), resetToken.getToken());
        auditService.log(AuditAction.PASSWORD_RESET_REQUESTED, user.getEmail(), "User", user.getId().toString(), null, ip);
    }

    public void resetPassword(ResetPasswordRequest request, String ip) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.token())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired reset link"));

        if (!resetToken.isValid()) {
            throw new InvalidCredentialsException("Invalid or expired reset link");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // A password reset should invalidate every existing session, not just future logins.
        refreshTokenService.revokeAllForUser(user);

        auditService.log(AuditAction.PASSWORD_RESET_COMPLETED, user.getEmail(), "User", user.getId().toString(), null, ip);
    }

    private LoginResponse buildLoginResponse(User user) {
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        RefreshToken refreshToken = refreshTokenService.create(user);
        return new LoginResponse(accessToken, jwtService.getExpirationMs(), refreshToken.getToken(), UserResponse.from(user));
    }
}
