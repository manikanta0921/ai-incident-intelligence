package com.example.incident.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload to log in")
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Schema(example = "sneha@example.com")
        String email,

        @NotBlank(message = "Password is required")
        @Schema(example = "Str0ngPass")
        String password
) {
}
