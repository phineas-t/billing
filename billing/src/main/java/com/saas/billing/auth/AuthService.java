package com.saas.billing.auth;

import com.saas.billing.auth.dto.AuthResponse;
import com.saas.billing.auth.dto.LoginRequest;
import com.saas.billing.auth.dto.RegisterRequest;
import com.saas.billing.common.config.JwtProperties;
import com.saas.billing.organization.Organization;
import com.saas.billing.organization.OrganizationRepository;
import com.saas.billing.organization.User;
import com.saas.billing.organization.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final OrganizationRepository orgRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "An account with this email already exists");
        }

        Organization org = Organization.builder()
                .name(request.getCompanyName())
                .email(request.getEmail())
                .status(Organization.OrgStatus.ACTIVE)
                .build();

        Organization savedOrg = orgRepository.save(org);

        User user = User.builder()
                .org(savedOrg)
                .email(request.getEmail())
                .passwordHash(
                        passwordEncoder.encode(request.getPassword()))
                .role(User.UserRole.ADMIN)
                .build();

        User savedUser = userRepository.save(user);

        String accessToken  = jwtUtil
                .generateAccessToken(savedUser);
        String refreshToken = refreshTokenService
                .createRefreshToken(savedUser);

        return AuthResponse.builder()
                .orgId(savedOrg.getId())
                .email(savedOrg.getEmail())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenMinutes()
                        * 60)
                .message("Registration successful")
                .build();
    }

    public AuthResponse login(LoginRequest request) {

        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(),
                user.getPasswordHash())) {
            throw new IllegalArgumentException(
                    "Invalid email or password");
        }

        String accessToken  = jwtUtil
                .generateAccessToken(user);
        String refreshToken = refreshTokenService
                .createRefreshToken(user);

        return AuthResponse.builder()
                .orgId(user.getOrg().getId())
                .email(user.getEmail())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenMinutes()
                        * 60)
                .message("Login successful")
                .build();
    }
}