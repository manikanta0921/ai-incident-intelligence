package com.example.incident.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Simple message response")
public record MessageResponse(

        @Schema(example = "User registered successfully")
        String message
) {
}
