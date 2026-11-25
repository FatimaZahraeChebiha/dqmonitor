package com.dataquality.backend.dto.user;

import com.dataquality.backend.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin-only user creation - unlike public registration, the role can be set explicitly. */
public record CreateUserRequest(
        @NotBlank @Size(min = 2, max = 100) String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotNull Role role
) {}
