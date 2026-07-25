package com.saas.billing.auth;

import com.saas.billing.organization.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    @Value("${security.jwt.refresh-token-days}")
    private int refreshTokenDays;

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest
                    .getInstance("SHA-256");
            byte[] hashBytes = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(
                    "SHA-256 not available", e);
        }
    }

    public User getUserFromRawToken(String rawToken) {
        String hash = hashToken(rawToken);
        return refreshTokenRepository
                .findByTokenHash(hash)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Token not found"))
                .getUser();
    }

    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .org(user.getOrg())
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now()
                        .plusDays(refreshTokenDays))
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public String rotateRefreshToken(String rawToken) {
        String incomingHash = hashToken(rawToken);

        RefreshToken existing = refreshTokenRepository
                .findByTokenHash(incomingHash)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid refresh token")
                );

        if (existing.isReused()) {
            revokeAllForUser(existing.getUser().getId());
            throw new IllegalArgumentException(
                    "Refresh token reuse detected. " +
                            "All sessions revoked. Please log in again."
            );
        }

        if (!existing.isActive()) {
            throw new IllegalArgumentException(
                    "Refresh token is expired or revoked");
        }

        String newRawToken = UUID.randomUUID().toString();
        String newTokenHash = hashToken(newRawToken);

        existing.setRevokedAt(LocalDateTime.now());
        existing.setReplacedByTokenHash(newTokenHash);
        refreshTokenRepository.save(existing);

        User user = existing.getUser();

        RefreshToken newToken = RefreshToken.builder()
                .user(user)
                .org(user.getOrg())
                .tokenHash(newTokenHash)
                .expiresAt(LocalDateTime.now()
                        .plusDays(refreshTokenDays))
                .build();

        refreshTokenRepository.save(newToken);
        return newRawToken;
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllActiveTokensForUser(
                userId, LocalDateTime.now());
    }

    @Transactional
    public void revokeToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> {
                    token.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(token);
                });
    }
}