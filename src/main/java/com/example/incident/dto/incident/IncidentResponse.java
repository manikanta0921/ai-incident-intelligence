package com.example.incident.dto.incident;

import java.time.Instant;

import com.example.incident.entity.Incident;
import com.example.incident.entity.IncidentCategory;
import com.example.incident.entity.IncidentSeverity;
import com.example.incident.entity.IncidentStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API representation of an incident. Entities never leave the service layer;
 * this record is what clients actually see.
 */
@Schema(description = "Incident details")
public record IncidentResponse(

        @Schema(example = "42")
        Long id,

        @Schema(example = "Payment deducted but order failed")
        String title,

        @Schema(description = "Detailed description of the incident",
                example = "Customer was charged 999 but the order was not created and the amount has not been refunded.")
        String description,

        @Schema(example = "OPEN")
        IncidentStatus status,

        @Schema(example = "PAYMENT")
        IncidentCategory category,

        @Schema(example = "HIGH")
        IncidentSeverity severity,

        @Schema(description = "AI-generated summary; null until the AI phase is implemented", nullable = true,
                example = "null")
        String summary,

        @Schema(description = "Resolution text once resolved/closed", nullable = true, example = "null")
        String resolution,

        @Schema(description = "Id of the customer who reported this incident", example = "7")
        Long reportedById,

        @Schema(description = "Name of the customer who reported this incident", example = "Sneha")
        String reportedByName,

        @Schema(example = "2026-09-06T10:30:00Z", readOnly = true)
        Instant createdAt,

        @Schema(example = "2026-09-06T11:45:00Z", readOnly = true)
        Instant updatedAt
) {

    /** Maps an entity to its API representation (the only place entity fields are copied out). */
    public static IncidentResponse fromEntity(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getStatus(),
                incident.getCategory(),
                incident.getSeverity(),
                incident.getSummary(),
                incident.getResolution(),
                incident.getReportedBy().getId(),
                incident.getReportedBy().getName(),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}
