package com.example.incident.security;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs once per request, BEFORE the authorization checks.
 *
 * Flow:
 *   1. Read the "Authorization: Bearer <token>" header (absent -> pass through;
 *      the request stays anonymous and protected endpoints return 401 later).
 *   2. Verify signature + expiry with JwtService.
 *   3. Load the matching UserDetails from the database.
 *   4. Store an authenticated token in the SecurityContext -> the request is
 *      now "authenticated" for the rest of the filter chain.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        extractBearerToken(request)
                .flatMap(jwtService::parseValidAccessToken)
                .ifPresent(claims -> authenticate(claims, request));

        // Always continue the chain: authentication failures here do not stop the request;
        // Spring Security's authorization rules decide what an anonymous request may do.
        filterChain.doFilter(request, response);
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()).trim());
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        String email = claims.getSubject();
        if (email == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            return; // nothing to authenticate with, or request already authenticated
        }
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            // Valid JWT but the user no longer exists (e.g. deleted account) -> leave anonymous.
            log.debug("JWT subject not found in database: {}", email);
        }
    }
}
