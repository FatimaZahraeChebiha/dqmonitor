package com.dataquality.backend.dto.user;

import com.dataquality.backend.model.Role;
import com.dataquality.backend.model.User;

import java.time.LocalDateTime;

/** Never includes the password hash - fixes the current leak where GET /api/users exposed it. */
public record UserResponse(
        Long id,
        String username,
        String email,
        Role role,
        boolean isActive,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                Boolean.TRUE.equals(user.getIsActive()),
                user.getCreatedAt()
        );
    }
}
