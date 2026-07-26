package com.saas.billing.auth;

import com.saas.billing.auth.dto.AuthResponse;
import com.saas.billing.auth.dto.LoginRequest;
import com.saas.billing.auth.dto.RefreshTokenRequest;
import com.saas.billing.auth.dto.RefreshTokenResponse;
import com.saas.billing.auth.dto.RegisterRequest;
import com.saas.billing.auth.RefreshTokenService.TokenRotationResult;
import com.saas.billing.common.config.JwtProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;

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
        return ResponseEntity
                .ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(
            @RequestBody @Valid
            RefreshTokenRequest request) {

        TokenRotationResult result = refreshTokenService
                .rotateRefreshToken(
                        request.getRefreshToken());

        return ResponseEntity.ok(
                RefreshTokenResponse.builder()
                        .accessToken(result.accessToken())
                        .refreshToken(result.refreshToken())
                        .tokenType("Bearer")
                        .expiresIn(jwtProperties
                                .getAccessTokenMinutes() * 60)
                        .build()
        );
    }
}