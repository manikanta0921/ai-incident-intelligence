package com.example.incident.exception;

/**
 * Thrown when a business rule is violated that bean validation cannot express
 * (e.g. a duplicate email at registration, or an illegal status transition).
 * Converted into HTTP 400 by GlobalExceptionHandler.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
