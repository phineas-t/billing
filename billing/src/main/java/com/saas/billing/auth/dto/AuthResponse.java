package com.saas.billing.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AuthResponse {
    //one response class for both register and login
    private UUID orgId;
    private String email;
    private String message;
}