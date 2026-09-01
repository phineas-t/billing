package com.saas.billing.webhook;

import com.saas.billing.common.config.StripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Webhooks", description = "Stripe webhook event receiver")
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;
    private final StripeProperties stripeProperties;

    @Operation(summary = "Stripe webhook endpoint",
            description = "Receives Stripe events. Verifies HMAC-SHA256 " +
                    "signature before processing. Idempotent — " +
                    "duplicate events are safely ignored.")
    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody byte[] payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(
                    new String(payload, StandardCharsets.UTF_8),
                    sigHeader,
                    stripeProperties.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (Exception e) {
            log.error("Failed to parse Stripe webhook: {}", e.getMessage());
            return ResponseEntity.ok("Received");
        }

        webhookService.processEvent(event);
        return ResponseEntity.ok("Received");
    }
}