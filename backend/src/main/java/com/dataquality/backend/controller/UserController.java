package com.dataquality.backend.controller;

import com.dataquality.backend.dto.PageResponse;
import com.dataquality.backend.dto.user.ChangePasswordRequest;
import com.dataquality.backend.dto.user.CreateUserRequest;
import com.dataquality.backend.dto.user.UserResponse;
import com.dataquality.backend.dto.user.UserStatsResponse;
import com.dataquality.backend.security.AuthPrincipal;
import com.dataquality.backend.security.ClientIpResolver;
import com.dataquality.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management (listing/creation is admin-only; self-service password change for all users)")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<UserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(userService.getUsersPage(PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserStatsResponse> getStats() {
        return ResponseEntity.ok(userService.getStats());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        var created = userService.createUser(request, principal.email(), ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        userService.deleteUser(id, principal.email(), ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /** Any authenticated user can change their own password (used by the Settings page). */
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changeOwnPassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        userService.changePassword(principal.userId(), request, ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.noContent().build();
    }
}
