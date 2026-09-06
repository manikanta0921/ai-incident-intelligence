package com.example.incident.exception;

/**
 * Thrown when a refresh token is missing, unknown, expired or revoked.
 * Maps to HTTP 401 (the client must log in again).
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
