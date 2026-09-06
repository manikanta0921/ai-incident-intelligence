package com.example.incident.dto.auth;

import com.example.incident.entity.User.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/auth/register.
 * The plain password exists only for the duration of the request; it is
 * BCrypt-hashed in AuthService and never persisted or logged.
 */
@Schema(description = "Request payload to register a new user")
public record RegisterRequest(

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        @Schema(example = "Sneha Guddeti")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        @Schema(example = "sneha@example.com")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
                message = "Password must contain at least one lowercase letter, one uppercase letter and one digit"
        )
        @Schema(description = "At least 8 chars, with upper case, lower case and a digit", example = "Str0ngPass")
        String password,

        @NotNull(message = "Role is required")
        @Schema(description = "CUSTOMER can report incidents; AGENT and ADMIN manage them", example = "CUSTOMER")
        Role role
) {
}
