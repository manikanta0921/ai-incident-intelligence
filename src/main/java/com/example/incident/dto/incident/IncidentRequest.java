package com.example.incident.dto.incident;

import com.example.incident.entity.IncidentCategory;
import com.example.incident.entity.IncidentSeverity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/incidents.
 *
 * The client does NOT choose id, status, summary, resolution, createdAt or
 * updatedAt - the server owns those fields.
 */
@Schema(description = "Request payload to create a new incident")
public record IncidentRequest(

        @NotBlank(message = "Title is required")
        @Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
        @Schema(description = "Short summary of the problem", example = "Payment deducted but order failed")
        String title,

        @NotBlank(message = "Description is required")
        @Size(min = 10, max = 5000, message = "Description must be between 10 and 5000 characters")
        @Schema(description = "Detailed description of what went wrong",
                example = "Customer was charged 999 but the order was not created and the amount has not been refunded.")
        String description,

        @NotNull(message = "Category is required")
        @Schema(description = "Functional area of the incident", example = "PAYMENT")
        IncidentCategory category,

        @NotNull(message = "Severity is required")
        @Schema(description = "Business impact", example = "HIGH")
        IncidentSeverity severity
) {
}
