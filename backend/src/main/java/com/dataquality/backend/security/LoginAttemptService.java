package com.dataquality.backend.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * In-memory brute-force protection. Tracks failed attempts per account (email)
 * and per source IP separately - per-account catches distributed attacks against
 * one user, per-IP catches one attacker cycling through many emails. Caffeine's
 * expireAfterWrite gives us a rolling lockout window without a scheduled cleanup job.
 *
 * Single-instance only: if this app ever runs as multiple replicas, this needs to
 * move to a shared store (Redis) - noted here rather than silently breaking later.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS_PER_ACCOUNT = 5;
    private static final int MAX_ATTEMPTS_PER_IP = 20;
    private static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);

    private final Cache<String, Integer> attemptsByAccount = Caffeine.newBuilder()
            .expireAfterWrite(LOCKOUT_WINDOW)
            .build();

    private final Cache<String, Integer> attemptsByIp = Caffeine.newBuilder()
            .expireAfterWrite(LOCKOUT_WINDOW)
            .build();

    public void recordFailure(String email, String ip) {
        attemptsByAccount.asMap().merge(normalize(email), 1, Integer::sum);
        attemptsByIp.asMap().merge(ip, 1, Integer::sum);
    }

    public void recordSuccess(String email, String ip) {
        attemptsByAccount.invalidate(normalize(email));
        attemptsByIp.invalidate(ip);
    }

    public boolean isBlocked(String email, String ip) {
        int accountAttempts = attemptsByAccount.asMap().getOrDefault(normalize(email), 0);
        int ipAttempts = attemptsByIp.asMap().getOrDefault(ip, 0);
        return accountAttempts >= MAX_ATTEMPTS_PER_ACCOUNT || ipAttempts >= MAX_ATTEMPTS_PER_IP;
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
