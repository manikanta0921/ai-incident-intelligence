package com.example.incident.security;

import com.example.incident.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates and verifies JWT access tokens (HS256) and opaque refresh tokens.
 *
 * Access token claims:
 *   sub  = user email (the "subject" - who the token belongs to)
 *   uid  = user id (used for ownership checks without a DB hit)
 *   role = user role (used for authorization)
 *   iat  = issued at, exp = expiration (both set automatically verified)
 *
 * No sensitive data (password, personal details) ever goes into claims:
 * anyone holding the token can Base64-decode its payload and read it.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        // HMAC-SHA key from the configured secret; jjwt enforces >= 256-bit keys for HS256.
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    // ---------- Access tokens ----------

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_UID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(signingKey) // algorithm chosen from the key -> HS256
                .compact();
    }

    /**
     * Verifies signature and expiry, then returns the claims.
     * Empty Optional for every invalid case (bad signature, expired, malformed).
     */
    public Optional<Claims> parseValidAccessToken(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            // ExpiredJwtException, SignatureException, MalformedJwtException... all extend JwtException
            log.debug("Rejected JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public String extractUsername(Claims claims) {
        return claims.getSubject();
    }

    // ---------- Refresh tokens ----------

    /**
     * Refresh tokens are opaque random values (not JWTs): nothing readable inside,
     * revocable server-side, and a leaked refresh token reveals nothing.
     */
    public String generateRefreshTokenValue() {
        return UUID.randomUUID().toString();
    }

    /**
     * Only the SHA-256 hash of a refresh token is stored in the database.
     * If the DB leaks, the attacker cannot reconstruct usable tokens from hashes.
     */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is part of every standard JVM; this cannot happen in practice.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
