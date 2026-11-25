package com.dataquality.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Guards /internal/** - the trusted, loopback-only callback used by the Python
 * quality_check job (not a browser client, so JWT/CORS don't apply here). A shared
 * secret is a proportionate control for a same-host service call; mTLS/network
 * isolation would be the next step if this ever left a single trusted host.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final String expectedKey;

    public InternalApiKeyFilter(@Value("${app.internal.api-key}") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/internal/")) {
            String provided = request.getHeader("X-Internal-Key");
            if (provided == null || !provided.equals(expectedKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"INVALID_INTERNAL_KEY\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
