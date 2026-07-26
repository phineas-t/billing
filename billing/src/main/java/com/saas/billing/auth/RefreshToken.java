package com.saas.billing.auth;

import com.saas.billing.organization.Organization;
import com.saas.billing.organization.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization org;

    @Column(name = "token_hash", nullable = false, unique = true,
            length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "replaced_by_token_hash", length = 64)
    private String replacedByTokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return revokedAt == null
                && LocalDateTime.now().isBefore(expiresAt);
    }

    /**
     * Returns true when this token was already rotated
     * (replacedByTokenHash is set) and someone is attempting
     * to reuse it. This indicates a replay attack on a rotated
     * token and should trigger full session revocation.
     *
     * Contrast with a token revoked by logout or admin action:
     * revokedAt is set but replacedByTokenHash is null.
     * That case is handled as ordinary revocation — reject only,
     * no nuclear revocation.
     */
    public boolean isRotatedTokenReuse() {
        return revokedAt != null
                && replacedByTokenHash != null;
    }
}