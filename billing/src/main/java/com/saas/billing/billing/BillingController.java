package com.saas.billing.billing;

import com.saas.billing.billing.dto.SubscribeRequest;
import com.saas.billing.billing.dto.SubscriptionResponse;
import com.saas.billing.billing.dto.UpgradeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/subscribe")
    public ResponseEntity<SubscriptionResponse> subscribe(
            @RequestBody @Valid SubscribeRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(billingService.subscribe(request));
    }

    @PostMapping("/upgrade")
    public ResponseEntity<SubscriptionResponse> upgrade(
            @RequestBody @Valid UpgradeRequest request) {
        return ResponseEntity
                .ok(billingService.upgrade(request));
    }

    @PostMapping("/cancel")
    public ResponseEntity<SubscriptionResponse> cancel() {
        return ResponseEntity
                .ok(billingService.cancel());
    }

    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionResponse>
    getCurrentSubscription() {
        return ResponseEntity
                .ok(billingService
                        .getCurrentSubscription());
    }
}