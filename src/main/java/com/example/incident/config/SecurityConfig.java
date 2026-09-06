package com.example.incident.config;

import com.example.incident.security.JwtAuthenticationFilter;
import com.example.incident.security.RestAccessDeniedHandler;
import com.example.incident.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Modern Spring Security configuration (SecurityFilterChain - the deprecated
 * WebSecurityConfigurerAdapter is NOT used).
 *
 * Every request travels through a chain of servlet filters. The important ones here:
 *   JwtAuthenticationFilter (ours)  -> "who are you?"  (authentication)
 *   AuthorizationFilter             -> "are you allowed?" (authorization)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize / @PostAuthorize on service/controller methods
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /** Hashes passwords with BCrypt (salted per user, deliberately slow - brute-force resistant). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The object that performs "email + password" authentication.
     * It wires together CustomUserDetailsService (finds the user, provides the stored
     * BCrypt hash) and PasswordEncoder (compares hash with submitted password).
     * AuthService calls authenticate() and receives either an authenticated result
     * or BadCredentialsException.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protects browser cookies from cross-site attacks. This API is stateless
                // (no cookies, no server sessions; JWT comes in the Authorization header), so
                // CSRF is not a threat here and is disabled deliberately.
                .csrf(csrf -> csrf.disable())
                // Uses the CorsConfigurationSource bean defined in CorsConfig.
                .cors(Customizer.withDefaults())
                // No HttpSession: every request must carry its own JWT.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        // No JWT / invalid JWT on a protected endpoint -> 401
                        .authenticationEntryPoint(authenticationEntryPoint)
                        // Valid JWT but insufficient role -> 403
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // Public: registration, login, token refresh
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                        // Public: API docs (Swagger UI + raw OpenAPI JSON)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Health/info are public; richer actuator data stays ADMIN-only
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // User management is ADMIN-only
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        // Everything else (all incident endpoints) needs a valid JWT
                        .anyRequest().authenticated())
                // Register our JWT filter BEFORE Spring Security's username/password filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
