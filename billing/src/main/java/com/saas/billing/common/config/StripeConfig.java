package com.saas.billing.common.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class StripeConfig {

    private final StripeProperties stripeProperties;

    @PostConstruct
    public void initStripe() {
        Stripe.apiKey = stripeProperties.getSecretKey();
        System.out.println("=== WEBHOOK SECRET LOADED: " +
                stripeProperties.getWebhookSecret() + " ===");
    }
}