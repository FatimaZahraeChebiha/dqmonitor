package com.dataquality.backend.security;

import jakarta.servlet.http.HttpServletRequest;

/** Prefers X-Forwarded-For since the app sits behind nginx in the docker-compose setup. */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
