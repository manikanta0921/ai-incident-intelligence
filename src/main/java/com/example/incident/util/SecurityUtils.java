package com.example.incident.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.incident.security.CustomUserDetails;

/**
 * Static helper for reading the authenticated caller inside services.
 * Using it keeps service method signatures free of security plumbing.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // static utility class - no instances
    }

    /** Returns the current principal, or throws if the caller is not authenticated. */
    public static CustomUserDetails getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        throw new IllegalStateException("No authenticated user found in security context");
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
