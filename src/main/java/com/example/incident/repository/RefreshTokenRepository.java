package com.example.incident.repository;

import com.example.incident.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** /api/auth/refresh looks the token up by its SHA-256 hash. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoke all sessions of one user (e.g. password change, "log out everywhere"). */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);

    /** Housekeeping: delete rows that can never be used again. Called from a scheduled job. */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
