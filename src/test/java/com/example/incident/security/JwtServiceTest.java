package com.example.incident.security;

import com.example.incident.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real crypto, real tokens - JwtService is self-contained, so it is tested
 * directly without mocks (this is not an external service).
 */
class JwtServiceTest {

    private static final String SECRET_32_BYTES = "test-secret-that-is-at-least-32-chars!!";

    private JwtService jwtService;
    private JwtProperties properties;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(SECRET_32_BYTES, 15, 7);
        jwtService = new JwtService(properties);
    }

    private User testUser() {
        return User.builder()
                .id(7L)
                .name("Sneha")
                .email("sneha@example.com")
                .password("hash")
                .role(User.Role.CUSTOMER)
                .build();
    }

    @Test
    @DisplayName("generated token carries subject, uid, role, iat and exp claims")
    void tokenContainsExpectedClaims() {
        String token = jwtService.generateAccessToken(testUser());

        Claims claims = jwtService.parseValidAccessToken(token).orElseThrow();

        assertThat(claims.getSubject()).isEqualTo("sneha@example.com");
        assertThat(claims.get("uid", Long.class)).isEqualTo(7L);
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
        assertThat(claims.getIssuedAt()).isNotNull();
        // 15 minutes TTL (with a small tolerance for clock drift during the test)
        long ttlSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(ttlSeconds).isEqualTo(Duration.ofMinutes(15).toSeconds());
    }

    @Test
    @DisplayName("valid token parses successfully")
    void validTokenParses() {
        String token = jwtService.generateAccessToken(testUser());

        assertThat(jwtService.parseValidAccessToken(token)).isPresent();
    }

    @Test
    @DisplayName("expired token is rejected")
    void expiredTokenRejected() {
        JwtProperties shortLived = new JwtProperties(SECRET_32_BYTES, -1, 7); // already expired
        JwtService shortService = new JwtService(shortLived);
        String token = shortService.generateAccessToken(testUser());

        assertThat(shortService.parseValidAccessToken(token)).isEmpty();
    }

    @Test
    @DisplayName("token signed with a different secret is rejected")
    void foreignSignatureRejected() {
        JwtService otherService = new JwtService(new JwtProperties(SECRET_32_BYTES + "-different", 15, 7));
        String foreignToken = otherService.generateAccessToken(testUser());

        assertThat(jwtService.parseValidAccessToken(foreignToken)).isEmpty();
    }

    @Test
    @DisplayName("garbage string is rejected without throwing")
    void garbageRejected() {
        assertThat(jwtService.parseValidAccessToken("not-a-jwt")).isEmpty();
        assertThat(jwtService.parseValidAccessToken("")).isEmpty();
    }

    @Test
    @DisplayName("refresh token hash is a stable 64-char hex string (SHA-256)")
    void refreshTokenHashing() {
        String hash1 = jwtService.hashRefreshToken("some-random-token");
        String hash2 = jwtService.hashRefreshToken("some-random-token");
        String hash3 = jwtService.hashRefreshToken("different-token");

        assertThat(hash1).isEqualTo(hash2);          // deterministic
        assertThat(hash1).hasSize(64);               // SHA-256 -> 32 bytes -> 64 hex chars
        assertThat(hash1).matches("[0-9a-f]{64}");
        assertThat(hash1).isNotEqualTo(hash3);       // different input -> different hash
        assertThat(hash1).isNotEqualTo("some-random-token"); // not reversible/identical
    }
}
