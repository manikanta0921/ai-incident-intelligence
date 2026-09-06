package com.example.incident.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload to exchange a refresh token for a new token pair")
public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token is required")
        @Schema(description = "Refresh token obtained from login", example = "9f8b7c6d-5e4a-3b2c-1d0e-9f8a7b6c5d4e")
        String refreshToken
) {
}
