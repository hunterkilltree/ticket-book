package com.ticketbooking.user.security;

import com.ticketbooking.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Minimal HS256 JWT issuer using only the JDK (no extra dependencies).
 * Produces a standard {@code header.payload.signature} token signed with a shared secret.
 */
@Service
public class JwtService {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final byte[] secret;
    private final long ttlSeconds;

    public JwtService(
            @Value("${jwt.secret:dev-secret-change-me-please-at-least-32-bytes}") String secret,
            @Value("${jwt.ttl-seconds:86400}") long ttlSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(User user) {
        long now = Instant.now().getEpochSecond();
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode(String.format(
                "{\"sub\":\"%s\",\"email\":\"%s\",\"role\":\"%s\",\"iat\":%d,\"exp\":%d}",
                user.getId(), escape(user.getEmail()), user.getRole().name(), now, now + ttlSeconds));
        String signingInput = header + "." + payload;
        return signingInput + "." + sign(signingInput);
    }

    private String encode(String json) {
        return B64.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
