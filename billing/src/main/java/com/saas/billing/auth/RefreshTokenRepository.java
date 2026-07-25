package com.saas.billing.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
        UPDATE RefreshToken rt
        SET rt.revokedAt = :revokedAt
        WHERE rt.user.id = :userId
        AND rt.revokedAt IS NULL
        """)
    void revokeAllActiveTokensForUser(UUID userId,
                                      LocalDateTime revokedAt);
}