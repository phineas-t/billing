package com.saas.billing.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SaaS Billing System API")
                        .description("""
                                Multi-tenant SaaS billing backend built with Java 17 and Spring Boot 3.
                                
                                Features: JWT authentication with refresh token rotation,
                                Stripe subscription management, Redis usage tracking,
                                webhook processing with idempotency, and invoice management.
                                
                                Getting started:
                                1. POST /auth/register to create an account
                                2. POST /auth/login to get a Bearer token
                                3. Click Authorize and paste your accessToken
                                4. Explore all protected endpoints
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Phineas")
                                .email("tanisha.gusain0804@gmail.com")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                "Paste your accessToken from POST /auth/login")));
    }
}