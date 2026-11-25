package com.dataquality.backend.config;

import com.dataquality.backend.model.Role;
import com.dataquality.backend.model.User;
import com.dataquality.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Replaces the old manual step of running a raw SQL INSERT with a plaintext
 * password to create the first admin account. Idempotent - only acts if no
 * ADMIN user exists yet.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.bootstrap-email}")
    private String adminEmail;

    @Value("${app.admin.bootstrap-password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        boolean adminExists = userRepository.findAll().stream().anyMatch(u -> u.getRole() == Role.ADMIN);
        if (adminExists) {
            return;
        }

        User admin = new User();
        admin.setUsername("Admin");
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setIsActive(true);
        userRepository.save(admin);

        log.info("Bootstrapped default admin account ({}). Change the password after first login.", adminEmail);
    }
}
