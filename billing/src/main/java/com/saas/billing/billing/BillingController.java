package com.saas.billing.billing;

import com.saas.billing.billing.dto.SubscribeRequest;
import com.saas.billing.billing.dto.SubscriptionResponse;
import com.saas.billing.billing.dto.UpgradeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Billing",
        description = "Subscription lifecycle — subscribe, upgrade, cancel")
@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @Operation(summary = "Subscribe to a plan",
            description = "Creates a subscription for the calling organisation. " +
                    "Free plan is local-only. Paid plans create Stripe objects.")
    @PostMapping("/subscribe")
    public ResponseEntity<SubscriptionResponse> subscribe(
            @RequestBody @Valid SubscribeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(billingService.subscribe(request));
    }

    @Operation(summary = "Upgrade or change plan",
            description = "Changes the current subscription to a different plan. " +
                    "Stripe handles proration automatically.")
    @PostMapping("/upgrade")
    public ResponseEntity<SubscriptionResponse> upgrade(
            @RequestBody @Valid UpgradeRequest request) {
        return ResponseEntity.ok(billingService.upgrade(request));
    }

    @Operation(summary = "Cancel subscription",
            description = "Schedules cancellation at the end of the current " +
                    "billing period. Access continues until period ends.")
    @PostMapping("/cancel")
    public ResponseEntity<SubscriptionResponse> cancel() {
        return ResponseEntity.ok(billingService.cancel());
    }

    @Operation(summary = "Get current subscription",
            description = "Returns the active subscription for the " +
                    "calling organisation.")
    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionResponse> getCurrentSubscription() {
        return ResponseEntity.ok(billingService.getCurrentSubscription());
    }
}