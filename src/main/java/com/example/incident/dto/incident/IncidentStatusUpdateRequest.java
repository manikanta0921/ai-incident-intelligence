package com.example.incident.dto.incident;

import com.example.incident.entity.IncidentStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of PATCH /api/incidents/{id}/status.
 * The optional resolution field lets an agent close the loop in one call
 * (e.g. status = RESOLVED + resolution text).
 */
@Schema(description = "Request payload to change an incident's status")
public record IncidentStatusUpdateRequest(

        @NotNull(message = "Status is required")
        @Schema(description = "New status", example = "IN_PROGRESS")
        IncidentStatus status,

        @Size(max = 5000, message = "Resolution must not exceed 5000 characters")
        @Schema(description = "Optional resolution text (typically set with RESOLVED/CLOSED)",
                example = "Refund of 999 initiated; order recreated and confirmed by customer.")
        String resolution
) {
}
