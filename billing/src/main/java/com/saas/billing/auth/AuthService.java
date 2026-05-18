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

    //@Transactional on register() — saving the org and saving the user are two separate database operations.
    // it wraps them in one transaction. If userRepository.save(user) fails for any reason after orgRepository.save(org) succeeded, the entire operation rolls back
    // no orphaned organization with no user. Either both save or neither does.
    @Transactional
    public AuthResponse register(RegisterRequest request){

        if(orgRepository.existsByEmail(request.getEmail())){
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
                .passwordHash(passwordEncoder.encode(request.getPassword())) //encode() converts password to BCrypt hash
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

        //BCrypt re-hashes the incoming password and compares it to the stored hash
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
