package com.example.incident.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Application user. Can be a CUSTOMER (reports incidents), an AGENT (works on them)
 * or an ADMIN (full control, user management).
 */
@Entity
@Table(
        name = "users",
        indexes = {
                // Login always looks a user up by email; this index makes that O(log n)
                // even though the unique constraint itself already creates one in PostgreSQL.
                @Index(name = "idx_user_email", columnList = "email", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor // used by @Builder
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * BCrypt hash. NEVER the plain-text password; plain passwords are not even
     * stored in this entity - they exist only inside the request DTO for a moment.
     */
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Keeps the incident -> owner query simple (no join needed to filter by owner). */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public enum Role {
        CUSTOMER,
        AGENT,
        ADMIN
    }
}
