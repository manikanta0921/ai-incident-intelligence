package com.example.incident.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.incident.dto.auth.AuthResponse;
import com.example.incident.dto.auth.LoginRequest;
import com.example.incident.dto.auth.MessageResponse;
import com.example.incident.dto.auth.RefreshTokenRequest;
import com.example.incident.dto.auth.RegisterRequest;
import com.example.incident.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Public authentication endpoints. No JWT required (see SecurityConfig permitAll).
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Register, login and refresh token endpoints")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED) // a new resource (the user) was created -> 201
    @Operation(summary = "Register a new user", description = "Creates an account with a BCrypt-hashed password")
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Returns an access token, refresh token and user info")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh tokens",
            description = "Exchanges a valid refresh token for a new token pair (rotation: old token revoked)")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }
}
