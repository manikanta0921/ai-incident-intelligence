package com.example.incident.exception;

/**
 * Thrown when a client asks for a resource that does not exist.
 * Handled by GlobalExceptionHandler and converted into HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /** Convenience factory, e.g. ResourceNotFoundException.of("Incident", 10). */
    public static ResourceNotFoundException of(String resourceName, Object id) {
        return new ResourceNotFoundException("%s not found with id: %s".formatted(resourceName, id));
    }
}
