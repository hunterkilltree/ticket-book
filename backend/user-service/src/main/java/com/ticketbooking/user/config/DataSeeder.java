package com.ticketbooking.user.config;

import com.ticketbooking.user.entity.User;
import com.ticketbooking.user.entity.UserRole;
import com.ticketbooking.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds demo accounts on startup so the app is testable out of the box.
 * Idempotent: only creates a user if its email is not already present.
 *
 *   customer@demo.local / Password123!   (CUSTOMER)
 *   admin@demo.local    / Password123!   (ADMIN — sees the Admin nav link)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seed("customer@demo.local", "Password123!", "Demo Customer", UserRole.CUSTOMER);
        seed("admin@demo.local", "Password123!", "Demo Admin", UserRole.ADMIN);
    }

    private void seed(String email, String rawPassword, String fullName, UserRole role) {
        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName);
        user.setRole(role);
        userRepository.save(user);
        log.info("Seeded demo user {} ({})", email, role);
    }
}
