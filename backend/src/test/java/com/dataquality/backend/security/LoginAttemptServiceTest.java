package com.dataquality.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
    }

    @Test
    void accountIsBlockedAfterFiveFailuresFromTheSameEmail() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("victim@dqmonitor.com", "10.0.0.1");
            assertThat(service.isBlocked("victim@dqmonitor.com", "10.0.0.1")).isFalse();
        }

        service.recordFailure("victim@dqmonitor.com", "10.0.0.1");

        assertThat(service.isBlocked("victim@dqmonitor.com", "10.0.0.1")).isTrue();
    }

    @Test
    void emailMatchingIsCaseAndWhitespaceInsensitive() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("Victim@DQMonitor.com  ", "10.0.0.2");
        }

        assertThat(service.isBlocked("  victim@dqmonitor.com", "10.0.0.2")).isTrue();
    }

    @Test
    void successfulLoginClearsTheFailureCounter() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("someone@dqmonitor.com", "10.0.0.3");
        }

        service.recordSuccess("someone@dqmonitor.com", "10.0.0.3");

        assertThat(service.isBlocked("someone@dqmonitor.com", "10.0.0.3")).isFalse();
    }

    @Test
    void perIpThresholdBlocksEvenAcrossDifferentAccounts() {
        for (int i = 0; i < 20; i++) {
            service.recordFailure("account" + i + "@dqmonitor.com", "10.0.0.4");
        }

        assertThat(service.isBlocked("brand-new-account@dqmonitor.com", "10.0.0.4")).isTrue();
    }

    @Test
    void unrelatedAccountsAndIpsAreNotAffected() {
        service.recordFailure("attacker@dqmonitor.com", "10.0.0.5");

        assertThat(service.isBlocked("innocent@dqmonitor.com", "10.0.0.6")).isFalse();
    }
}
