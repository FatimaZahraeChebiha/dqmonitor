package com.dataquality.backend.security;

import com.dataquality.backend.model.Role;

/** Lightweight principal carried in the SecurityContext - avoids a DB lookup on every request. */
public record AuthPrincipal(Long userId, String email, Role role) {}
