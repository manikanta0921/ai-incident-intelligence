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
 * Refresh token, stored so it can be revoked (e.g. on logout or token reuse).
 *
 * Only a SHA-256 hash of the token is stored: if the database is leaked,
 * the attacker cannot mint valid refresh tokens from it.
 */
@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                // Lookup by token happens on every /api/auth/refresh call.
                @Index(name = "idx_refresh_token_hash", columnList = "tokenHash", unique = true),
                // Revoking "all sessions of this user" filters by user.
                @Index(name = "idx_refresh_user_id", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor // used by @Builder
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_refresh_user"))
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isUsable(Instant now) {
        return !revoked && !isExpired(now);
    }
}
