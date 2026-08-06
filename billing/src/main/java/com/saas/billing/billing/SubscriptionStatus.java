package com.saas.billing.billing;

public enum SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELLED,
    INCOMPLETE,
    INCOMPLETE_EXPIRED;

    public static SubscriptionStatus fromStripe(
            String stripeStatus) {
        return switch (stripeStatus.toLowerCase()) {
            case "trialing"            -> TRIALING;
            case "active"              -> ACTIVE;
            case "past_due"            -> PAST_DUE;
            case "canceled"            -> CANCELLED;
            case "incomplete"          -> INCOMPLETE;
            case "incomplete_expired"  -> INCOMPLETE_EXPIRED;
            default -> throw new IllegalArgumentException(
                    "Unknown Stripe subscription status: "
                            + stripeStatus);
        };
    }

    public boolean isAccessible() {
        return this == ACTIVE
                || this == TRIALING
                || this == PAST_DUE;
    }
}