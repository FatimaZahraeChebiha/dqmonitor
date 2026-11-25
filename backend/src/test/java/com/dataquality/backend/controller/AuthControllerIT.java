package com.dataquality.backend.controller;

import com.dataquality.backend.dto.auth.*;
import com.dataquality.backend.dto.dataset.DatasetResponse;
import com.dataquality.backend.dto.user.UserResponse;
import com.dataquality.backend.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real HTTP + Spring Security stack (JwtAuthFilter, @PreAuthorize,
 * refresh-token rotation) end to end against a real database, rather than mocking
 * the security context - that's the whole point of this class over AuthServiceTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIT {

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String uniqueEmail() {
        return "it-" + UUID.randomUUID() + "@dqmonitor.com";
    }

    @Test
    void registerThenLoginIssuesWorkingAccessAndRefreshTokens() {
        String email = uniqueEmail();
        RegisterRequest registerRequest = new RegisterRequest("IT User", email, "password123");

        ResponseEntity<UserResponse> registerResponse =
                restTemplate.postForEntity(baseUrl() + "/api/auth/register", registerRequest, UserResponse.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody().role()).isEqualTo(Role.USER);

        LoginResponse login = login(email, "password123");
        assertThat(login.token()).isNotBlank();
        assertThat(login.refreshToken()).isNotBlank();

        ResponseEntity<DatasetResponse[]> withoutToken =
                restTemplate.getForEntity(baseUrl() + "/api/datasets", DatasetResponse[].class);
        assertThat(withoutToken.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.token());
        ResponseEntity<String> withToken = restTemplate.exchange(
                baseUrl() + "/api/datasets", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(withToken.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void registerRejectsDuplicateEmailWithConflict() {
        String email = uniqueEmail();
        RegisterRequest registerRequest = new RegisterRequest("IT User", email, "password123");
        restTemplate.postForEntity(baseUrl() + "/api/auth/register", registerRequest, UserResponse.class);

        ResponseEntity<String> secondAttempt =
                restTemplate.postForEntity(baseUrl() + "/api/auth/register", registerRequest, String.class);

        assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void loginRejectsWrongPassword() {
        String email = uniqueEmail();
        restTemplate.postForEntity(baseUrl() + "/api/auth/register",
                new RegisterRequest("IT User", email, "password123"), UserResponse.class);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", new LoginRequest(email, "wrong-password"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void plainUserCannotAccessAdminOnlyEndpoint() {
        String email = uniqueEmail();
        restTemplate.postForEntity(baseUrl() + "/api/auth/register",
                new RegisterRequest("IT User", email, "password123"), UserResponse.class);
        LoginResponse login = login(email, "password123");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.token());
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/users", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void refreshTokenRotatesAndInvalidatesThePreviousOne() {
        String email = uniqueEmail();
        restTemplate.postForEntity(baseUrl() + "/api/auth/register",
                new RegisterRequest("IT User", email, "password123"), UserResponse.class);
        LoginResponse login = login(email, "password123");

        ResponseEntity<RefreshResponse> refreshResponse = restTemplate.postForEntity(
                baseUrl() + "/api/auth/refresh", new RefreshRequest(login.refreshToken()), RefreshResponse.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody().refreshToken()).isNotEqualTo(login.refreshToken());

        ResponseEntity<String> reuseOldToken = restTemplate.postForEntity(
                baseUrl() + "/api/auth/refresh", new RefreshRequest(login.refreshToken()), String.class);
        assertThat(reuseOldToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private LoginResponse login(String email, String password) {
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", new LoginRequest(email, password), LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
