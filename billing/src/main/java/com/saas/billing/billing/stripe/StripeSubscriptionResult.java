package com.saas.billing.billing.stripe;

import java.time.LocalDateTime;

public record StripeSubscriptionResult(
        String subscriptionId,
        String subscriptionItemId,
        String customerId,
        String status,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd
) {}