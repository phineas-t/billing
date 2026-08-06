package com.saas.billing.billing.dto;

import com.saas.billing.billing.PlanLimits;
import com.saas.billing.billing.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class SubscriptionResponse {

    private UUID subscriptionId;
    private UUID orgId;
    private String planCode;
    private String planDisplayName;
    private SubscriptionStatus status;
    private Integer monthlyPriceCents;
    private PlanLimits limits;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private Boolean cancelAtPeriodEnd;
    private String stripeSubscriptionId;
    private LocalDateTime createdAt;
}