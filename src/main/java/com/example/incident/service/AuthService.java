package com.example.incident.service;

import com.example.incident.dto.auth.AuthResponse;
import com.example.incident.dto.auth.LoginRequest;
import com.example.incident.dto.auth.MessageResponse;
import com.example.incident.dto.auth.RefreshTokenRequest;
import com.example.incident.dto.auth.RegisterRequest;

/**
 * Authentication business logic contract.
 */
public interface AuthService {

    /** Validates input, checks email uniqueness, hashes the password and saves the user. */
    MessageResponse register(RegisterRequest request);

    /** Authenticates email+password and returns a JWT token pair. */
    AuthResponse login(LoginRequest request);

    /** Exchanges a valid refresh token for a new token pair (rotation: old one is revoked). */
    AuthResponse refresh(RefreshTokenRequest request);

    /** Revokes every active refresh token of a user ("log out everywhere"). */
    void revokeAllSessions(Long userId);
}
