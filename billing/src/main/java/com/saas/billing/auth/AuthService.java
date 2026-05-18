package com.saas.billing.auth;

import com.saas.billing.auth.dto.AuthResponse;
import com.saas.billing.auth.dto.LoginRequest;
import com.saas.billing.auth.dto.RegisterRequest;
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


    @Transactional
    public AuthResponse register(RegisterRequest request){

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        Organization org = Organization.builder()
                .name(request.getCompanyName())
                .email(request.getEmail())
                .status(Organization.OrgStatus.ACTIVE)
                .build();

        Organization savedOrg=orgRepository.save(org);

        User user=User.builder().
                org(savedOrg)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(User.UserRole.ADMIN)
                .build();

        userRepository.save(user);

        return AuthResponse.builder()
                .orgId(savedOrg.getId())
                .email(savedOrg.getEmail())
                .message("Registration successful")
                .build();

    }

    public AuthResponse login(LoginRequest request){

        User user=userRepository.findByEmail(request.getEmail())
                .orElseThrow(()->
                    new IllegalArgumentException("Invalid email or password")
                );

        // Same error for both wrong email and wrong password — prevents user enumeration
        if(!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())){
            throw new IllegalArgumentException("Invalid email or password");
        }

        return AuthResponse.builder()
                .orgId(user.getOrg().getId())
                .email(user.getEmail())
                .message("Login successful")
                .build();
    }

}
