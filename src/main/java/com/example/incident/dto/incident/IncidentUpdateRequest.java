package com.example.incident.dto.incident;

import com.example.incident.entity.IncidentCategory;
import com.example.incident.entity.IncidentSeverity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/incidents/{id} (full update).
 * Status changes go through the dedicated PATCH endpoint instead, so the
 * status lifecycle is always validated in one place.
 */
@Schema(description = "Request payload to update an incident")
public record IncidentUpdateRequest(

        @NotBlank(message = "Title is required")
        @Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
        @Schema(example = "Payment deducted but order failed")
        String title,

        @NotBlank(message = "Description is required")
        @Size(min = 10, max = 5000, message = "Description must be between 10 and 5000 characters")
        @Schema(example = "Customer was charged 999 but the order was not created and the amount has not been refunded.")
        String description,

        @NotNull(message = "Category is required")
        @Schema(example = "PAYMENT")
        IncidentCategory category,

        @NotNull(message = "Severity is required")
        @Schema(example = "HIGH")
        IncidentSeverity severity
) {
}
