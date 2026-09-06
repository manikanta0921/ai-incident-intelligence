package com.example.incident.dto.user;

import com.example.incident.entity.User;
import com.example.incident.entity.User.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Public view of a user. Note: no password and no password hash.
 */
@Schema(description = "User details (never includes the password)")
public record UserResponse(

        @Schema(example = "7")
        Long id,

        @Schema(example = "Sneha Guddeti")
        String name,

        @Schema(example = "sneha@example.com")
        String email,

        @Schema(example = "CUSTOMER")
        Role role,

        @Schema(example = "2026-09-01T09:15:00Z")
        Instant createdAt
) {

    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
