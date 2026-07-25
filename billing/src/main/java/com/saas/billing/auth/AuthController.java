package com.saas.billing.auth;

import com.saas.billing.auth.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;
    private final com.saas.billing.common.config
            .JwtProperties jwtProperties;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody @Valid RegisterRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(
            @RequestBody @Valid
            RefreshTokenRequest request) {

        String newRawRefreshToken = refreshTokenService
                .rotateRefreshToken(request.getRefreshToken());

        String newAccessToken = jwtUtil.generateAccessToken(
                refreshTokenService
                        .getUserFromRawToken(newRawRefreshToken)
        );

        return ResponseEntity.ok(
                RefreshTokenResponse.builder()
                        .accessToken(newAccessToken)
                        .refreshToken(newRawRefreshToken)
                        .tokenType("Bearer")
                        .expiresIn(jwtProperties
                                .getAccessTokenMinutes() * 60)
                        .build()
        );
    }
}