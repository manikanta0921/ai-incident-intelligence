package com.example.incident.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

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
import com.example.incident.service.impl.AuthServiceImpl;

/**
 * Unit tests for authentication flows. Everything external is mocked:
 * no real database, no real token signing, no real password hashing.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthServiceImpl authService;

    private User customer;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .id(7L)
                .name("Sneha")
                .email("sneha@example.com")
                .password("bcrypt-hash")
                .role(User.Role.CUSTOMER)
                .build();
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("hashes the password with BCrypt and saves the user")
        void registersSuccessfully() {
            RegisterRequest request =
                    new RegisterRequest("Sneha", "Sneha@Example.com", "Str0ngPass", User.Role.CUSTOMER);
            when(userRepository.existsByEmail("sneha@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Str0ngPass")).thenReturn("$2a$10$hashedvalue");
            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0, User.class));

            MessageResponse response = authService.register(request);

            assertThat(response.message()).isEqualTo("User registered successfully");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getPassword())          // plain password must never be stored
                    .isEqualTo("$2a$10$hashedvalue").isNotEqualTo("Str0ngPass");
            assertThat(saved.getEmail()).isEqualTo("sneha@example.com"); // normalized to lower case
        }

        @Test
        @DisplayName("rejects a duplicate email")
        void duplicateEmailRejected() {
            RegisterRequest request = new RegisterRequest("Sneha", "sneha@example.com", "Str0ngPass", User.Role.CUSTOMER);
            when(userRepository.existsByEmail("sneha@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already registered");
            verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("returns a token pair on successful authentication")
        void loginSuccess() {
            LoginRequest request = new LoginRequest("sneha@example.com", "Str0ngPass");
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(new UsernamePasswordAuthenticationToken("principal", null));
            when(userRepository.findByEmail("sneha@example.com")).thenReturn(Optional.of(customer));
            when(jwtService.generateAccessToken(customer)).thenReturn("access-token");
            when(jwtService.generateRefreshTokenValue()).thenReturn("refresh-token-raw");
            when(jwtService.hashRefreshToken("refresh-token-raw")).thenReturn("refresh-token-hash");
            when(jwtProperties.refreshTokenTtl()).thenReturn(Duration.ofDays(7));
            when(jwtProperties.accessTokenExpirationSeconds()).thenReturn(900L);

            AuthResponse response = authService.login(request);

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token-raw");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(900L);
            assertThat(response.role()).isEqualTo(User.Role.CUSTOMER);
            // the raw refresh token is never persisted - only its hash
            verify(refreshTokenRepository).save(org.mockito.ArgumentMatchers.argThat(
                    token -> "refresh-token-hash".equals(token.getTokenHash())));
        }

        @Test
        @DisplayName("wrong credentials throw BadCredentialsException (unit level)")
        void loginFailure() {
            LoginRequest request = new LoginRequest("sneha@example.com", "wrongPassword1");
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("bad"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("Invalid email or password");
        }

        @Test
        @DisplayName("error message is identical for unknown email and wrong password (no user enumeration)")
        void noUserEnumeration() {
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("bad"));

            assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "whatever1")))
                    .hasMessage("Invalid email or password");
        }
    }

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("valid refresh token is rotated and a new pair is returned")
        void refreshRotatesToken() {
            RefreshToken stored = RefreshToken.builder()
                    .user(customer)
                    .tokenHash("stored-hash")
                    .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                    .revoked(false)
                    .build();
            when(jwtService.hashRefreshToken("presented-token")).thenReturn("stored-hash");
            when(refreshTokenRepository.findByTokenHash("stored-hash")).thenReturn(Optional.of(stored));
            when(jwtService.generateAccessToken(customer)).thenReturn("new-access");
            when(jwtService.generateRefreshTokenValue()).thenReturn("new-refresh");
            when(jwtService.hashRefreshToken("new-refresh")).thenReturn("new-hash");
            when(jwtProperties.refreshTokenTtl()).thenReturn(Duration.ofDays(7));
            when(jwtProperties.accessTokenExpirationSeconds()).thenReturn(900L);

            AuthResponse response = authService.refresh(new RefreshTokenRequest("presented-token"));

            assertThat(response.accessToken()).isEqualTo("new-access");
            assertThat(stored.isRevoked()).isTrue(); // old token cannot be reused
        }

        @Test
        @DisplayName("unknown refresh token is rejected")
        void unknownTokenRejected() {
            when(jwtService.hashRefreshToken("bogus")).thenReturn("hash-of-bogus");
            when(refreshTokenRepository.findByTokenHash("hash-of-bogus")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("bogus")))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("Invalid refresh token");
        }

        @Test
        @DisplayName("revoked refresh token is rejected (replay protection)")
        void revokedTokenRejected() {
            RefreshToken revoked = RefreshToken.builder()
                    .user(customer)
                    .tokenHash("stored-hash")
                    .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                    .revoked(true)
                    .build();
            when(jwtService.hashRefreshToken("used-token")).thenReturn("stored-hash");
            when(refreshTokenRepository.findByTokenHash("stored-hash")).thenReturn(Optional.of(revoked));

            assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("used-token")))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("expired or revoked");
        }

        @Test
        @DisplayName("expired refresh token is rejected")
        void expiredTokenRejected() {
            RefreshToken expired = RefreshToken.builder()
                    .user(customer)
                    .tokenHash("stored-hash")
                    .expiresAt(Instant.now().minus(Duration.ofDays(1)))
                    .revoked(false)
                    .build();
            when(jwtService.hashRefreshToken("old-token")).thenReturn("stored-hash");
            when(refreshTokenRepository.findByTokenHash("stored-hash")).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("old-token")))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("expired or revoked");
        }
    }
}
