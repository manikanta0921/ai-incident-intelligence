package com.example.incident.service.impl;

import com.example.incident.dto.auth.AuthResponse;
import com.example.incident.dto.auth.LoginRequest;
import com.example.incident.dto.auth.MessageResponse;
import com.example.incident.dto.auth.RefreshTokenRequest;
import com.example.incident.dto.auth.RegisterRequest;
import com.example.incident.entity.RefreshToken;
import com.example.incident.entity.User;
import com.example.incident.exception.BusinessException;
import com.example.incident.exception.InvalidTokenException;
import com.example.incident.repository.RefreshTokenRepository;
import com.example.incident.repository.UserRepository;
import com.example.incident.security.JwtProperties;
import com.example.incident.security.JwtService;
import com.example.incident.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Implements the three authentication flows.
 *
 * REGISTER: validate -> email unique? -> BCrypt hash -> save
 * LOGIN:    AuthenticationManager -> load user -> issue access+refresh tokens
 * REFRESH:  verify stored hash -> rotate (revoke old, issue new) -> new pair
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final String TOKEN_TYPE_BEARER = "Bearer";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthServiceImpl(UserRepository userRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JwtService jwtService,
                           JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Override
    @Transactional
    public MessageResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            // 400 - consistent with other business-rule violations
            throw new BusinessException("Email is already registered");
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(normalizedEmail)
                // BCrypt: salted per user, deliberately slow, one-way.
                // The plain password is never stored and never logged.
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();

        userRepository.save(user);
        log.info("Registered new user id={} role={}", user.getId(), user.getRole());
        return new MessageResponse("User registered successfully");
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            // Delegates to CustomUserDetailsService + PasswordEncoder via the
            // DaoAuthenticationProvider. Wrong credentials -> BadCredentialsException.
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException e) {
            // Same message for unknown email and wrong password (no user enumeration).
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        return buildTokenPair(user);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String presentedHash = jwtService.hashRefreshToken(request.refreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(presentedHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (!stored.isUsable(Instant.now())) {
            // Expired or revoked (possibly stolen and already used) -> force a fresh login.
            throw new InvalidTokenException("Refresh token is expired or revoked");
        }

        // Rotation: each refresh token is single-use. The presented one is revoked and a
        // new pair is issued. Replaying a stolen, already-used token therefore fails.
        stored.setRevoked(true);

        return buildTokenPair(stored.getUser());
    }

    @Override
    @Transactional
    public void revokeAllSessions(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    // ---------- helpers ----------

    private AuthResponse buildTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenValue = jwtService.generateRefreshTokenValue();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                // Only the SHA-256 hash is persisted - never the raw token.
                .tokenHash(jwtService.hashRefreshToken(refreshTokenValue))
                .expiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                refreshTokenValue,
                TOKEN_TYPE_BEARER,
                jwtProperties.accessTokenExpirationSeconds(),
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
    }
}
