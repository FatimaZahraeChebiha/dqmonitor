package com.dataquality.backend.service;

import com.dataquality.backend.dto.PageResponse;
import com.dataquality.backend.dto.user.ChangePasswordRequest;
import com.dataquality.backend.dto.user.CreateUserRequest;
import com.dataquality.backend.dto.user.UserResponse;
import com.dataquality.backend.exception.DuplicateEmailException;
import com.dataquality.backend.exception.InvalidCredentialsException;
import com.dataquality.backend.exception.ResourceNotFoundException;
import com.dataquality.backend.dto.user.UserStatsResponse;
import com.dataquality.backend.model.AuditAction;
import com.dataquality.backend.model.Role;
import com.dataquality.backend.model.User;
import com.dataquality.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final RefreshTokenService refreshTokenService;

    public PageResponse<UserResponse> getUsersPage(Pageable pageable) {
        return PageResponse.from(userRepository.findAllByOrderByCreatedAtAsc(pageable), UserResponse::from);
    }

    public UserStatsResponse getStats() {
        long admins = userRepository.countByRole(Role.ADMIN);
        long users = userRepository.countByRole(Role.USER);
        return new UserStatsResponse(admins + users, admins, users);
    }

    /** Admin-only creation - unlike public self-registration, the role is caller-specified. */
    public UserResponse createUser(CreateUserRequest request, String actorEmail, String ip) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException("An account with this email already exists");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setIsActive(true);
        user = userRepository.save(user);

        auditService.log(AuditAction.USER_CREATED, actorEmail, "User", user.getId().toString(),
                "role=" + user.getRole(), ip);
        return UserResponse.from(user);
    }

    public void deleteUser(Long id, String actorEmail, String ip) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        userRepository.deleteById(id);
        auditService.log(AuditAction.USER_DELETED, actorEmail, "User", id.toString(), user.getEmail(), ip);
    }

    public void changePassword(Long userId, ChangePasswordRequest request, String ip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Changing your own password invalidates other sessions too - consistent
        // with the admin-triggered password reset flow.
        refreshTokenService.revokeAllForUser(user);

        auditService.log(AuditAction.PASSWORD_CHANGED, user.getEmail(), "User", user.getId().toString(), null, ip);
    }
}
