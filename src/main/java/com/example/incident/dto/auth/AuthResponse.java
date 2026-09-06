package com.example.incident.dto.auth;

import com.example.incident.entity.User.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response of /api/auth/login and /api/auth/refresh.
 * Password (or its hash) is never part of any API response.
 */
@Schema(description = "Authentication response with JWT token pair")
public record AuthResponse(

        @Schema(description = "Short-lived JWT access token (send as: Authorization: Bearer <token>)",
                example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "Long-lived refresh token used to obtain a new access token",
                example = "9f8b7c6d-5e4a-3b2c-1d0e-9f8a7b6c5d4e")
        String refreshToken,

        @Schema(description = "Token type to use in the Authorization header", example = "Bearer")
        String tokenType,

        @Schema(description = "Access token lifetime in seconds", example = "900")
        long expiresIn,

        @Schema(example = "7")
        Long userId,

        @Schema(example = "sneha@example.com")
        String email,

        @Schema(example = "CUSTOMER")
        Role role
) {
}
