package com.example.incident.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS origins for the future React frontend.
 * Configured via CORS_ALLOWED_ORIGINS (comma-separated).
 * Deliberately no "allow all origins" option to make an insecure shortcut impossible.
 */
@ConfigurationProperties(prefix = "app.security.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
