package com.ticketbooking.user.security;

import com.ticketbooking.user.entity.User;
import com.ticketbooking.user.entity.UserRole;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    @Test
    void issues_a_three_part_token_with_claims() {
        JwtService jwt = new JwtService("0123456789012345678901234567890123", 3600);
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("a@b.com");
        u.setRole(UserRole.CUSTOMER);

        String token = jwt.issue(u);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertTrue(payload.contains("\"email\":\"a@b.com\""));
        assertTrue(payload.contains("\"role\":\"CUSTOMER\""));
    }
}
