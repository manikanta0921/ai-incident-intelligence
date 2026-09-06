package com.example.incident.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for app.security.jwt.* properties.
 *
 * All values come from environment variables (see .env.example):
 *   JWT_SECRET, JWT_ACCESS_EXPIRATION, JWT_REFRESH_EXPIRATION
 * No secret is ever hard-coded in source.
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String secret,
        int accessTokenExpirationMinutes,
        int refreshTokenExpirationDays
) {

    /** Access token lifetime in seconds (for the expiresIn field of AuthResponse). */
    public long accessTokenExpirationSeconds() {
        return accessTokenExpirationMinutes * 60L;
    }

    public java.time.Duration accessTokenTtl() {
        return java.time.Duration.ofMinutes(accessTokenExpirationMinutes);
    }

    public java.time.Duration refreshTokenTtl() {
        return java.time.Duration.ofDays(refreshTokenExpirationDays);
    }
}
