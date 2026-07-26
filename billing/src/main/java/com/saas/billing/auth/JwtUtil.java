package com.saas.billing.auth;

import com.saas.billing.common.config.JwtProperties;
import com.saas.billing.organization.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret()
                .getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(
                jwtProperties.getAccessTokenMinutes() * 60L);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("orgId", user.getOrg().getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(getSigningKey())
                .compact();
    }

//    public String generateRefreshToken(User user) {
//        Instant now = Instant.now();
//        Instant expiry = now.plusSeconds(
//                jwtProperties.getRefreshTokenDays() * 86400L);
//
//        return Jwts.builder()
//                .subject(user.getId().toString())
//                .claim("orgId", user.getOrg().getId().toString())
//                .claim("type", "refresh")
//                .id(UUID.randomUUID().toString())
//                .issuedAt(Date.from(now))
//                .expiration(Date.from(expiry))
//                .signWith(getSigningKey())
//                .compact();
//    }

    public Claims parseAndValidate(String token,
                                   String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String actualType = claims.get("type", String.class);
            if (!expectedType.equals(actualType)) {
                throw new JwtException(
                        "Invalid token type. Expected: "
                                + expectedType
                                + ", got: " + actualType
                );
            }

            return claims;

        } catch (ExpiredJwtException e) {
            throw new JwtException("Token has expired", e);
        } catch (JwtException e) {
            throw new JwtException("Invalid token: "
                    + e.getMessage(), e);
        }
    }
}