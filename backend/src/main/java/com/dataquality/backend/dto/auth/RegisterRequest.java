package com.dataquality.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public self-registration always creates a USER account - role can't be set here. */
public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 100) String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password
) {}
