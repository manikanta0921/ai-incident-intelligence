package com.example.incident.dto.user;

import com.example.incident.entity.User.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload to change a user's role (ADMIN only)")
public record RoleUpdateRequest(

        @NotNull(message = "Role is required")
        @Schema(example = "AGENT")
        Role role
) {
}
