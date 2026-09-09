package com.example.incident.exception;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Consistent error body for every failed request.
 * Intentionally contains no stack traces and no internal details.
 */
@Schema(description = "Standard error response")
public record ErrorResponse(

        @Schema(example = "2026-09-06T10:30:00Z")
        Instant timestamp,

        @Schema(example = "404")
        int status,

        @Schema(example = "NOT_FOUND")
        String error,

        @Schema(example = "Incident not found with id: 10")
        String message,

        @Schema(example = "/api/incidents/10")
        String path
) {
}
