package com.saas.billing.billing.stripe;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.net.RequestOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@Slf4j
public class StripeBillingClient {

    private void validateStripeKey() {
        String key = Stripe.apiKey;
        if (key == null
                || key.isBlank()
                || key.equals("sk_test_placeholder")) {
            throw new IllegalStateException(
                    "Stripe is not configured. " +
                            "Set STRIPE_SECRET_KEY environment variable " +
                            "with a valid Stripe test secret key.");
        }
    }

    public StripeCustomerResult createCustomer(
            String email,
            String name,
            String orgId) {
        validateStripeKey();
        try {
            CustomerCreateParams params =
                    CustomerCreateParams.builder()
                            .setEmail(email)
                            .setName(name)
                            .putMetadata("org_id", orgId)
                            .build();

            RequestOptions options =
                    RequestOptions.builder()
                            .setIdempotencyKey(
                                    "create-customer-" + orgId)
                            .build();

            Customer customer = Customer.create(
                    params, options);

            return new StripeCustomerResult(
                    customer.getId());

        } catch (StripeException e) {
            log.error("Failed to create Stripe customer " +
                    "for org {}: {}", orgId, e.getMessage());
            throw new RuntimeException(
                    "Payment service error: " + e.getMessage(),
                    e);
        }
    }

    public StripeSubscriptionResult createSubscription(
            String customerId,
            String stripePriceId,
            String orgId,
            String planId,
            String operationId) {
        validateStripeKey();
        try {
            SubscriptionCreateParams params =
                    SubscriptionCreateParams.builder()
                            .setCustomer(customerId)
                            .addItem(
                                    SubscriptionCreateParams.Item
                                            .builder()
                                            .setPrice(stripePriceId)
                                            .build()
                            )
                            .setPaymentBehavior(
                                    SubscriptionCreateParams
                                            .PaymentBehavior
                                            .DEFAULT_INCOMPLETE)
                            .build();

            RequestOptions options =
                    RequestOptions.builder()
                            .setIdempotencyKey(
                                    "subscribe-" + orgId
                                            + "-" + planId
                                            + "-" + operationId)
                            .build();

            Subscription subscription =
                    Subscription.create(params, options);

            SubscriptionItem item = subscription
                    .getItems().getData().get(0);

            return new StripeSubscriptionResult(
                    subscription.getId(),
                    item.getId(),
                    customerId,
                    subscription.getStatus(),
                    toLocalDateTime(
                            subscription.getCurrentPeriodStart()),
                    toLocalDateTime(
                            subscription.getCurrentPeriodEnd())
            );

        } catch (StripeException e) {
            log.error("Failed to create Stripe subscription" +
                    " for org {}: {}", orgId, e.getMessage());
            throw new RuntimeException(
                    "Payment service error: " + e.getMessage(),
                    e);
        }
    }

    public StripeSubscriptionResult updateSubscription(
            String stripeSubscriptionId,
            String stripeSubscriptionItemId,
            String newStripePriceId,
            String orgId,
            String newPlanId) {
        validateStripeKey();
        try {
            SubscriptionUpdateParams params =
                    SubscriptionUpdateParams.builder()
                            .addItem(
                                    SubscriptionUpdateParams.Item
                                            .builder()
                                            .setId(stripeSubscriptionItemId)
                                            .setPrice(newStripePriceId)
                                            .build()
                            )
                            .setProrationBehavior(
                                    SubscriptionUpdateParams
                                            .ProrationBehavior
                                            .CREATE_PRORATIONS)
                            .build();

            RequestOptions options =
                    RequestOptions.builder()
                            .setIdempotencyKey(
                                    "upgrade-" + orgId
                                            + "-" + newPlanId
                                            + "-" + stripeSubscriptionId)
                            .build();

            Subscription subscription =
                    Subscription.retrieve(
                            stripeSubscriptionId);
            subscription = subscription.update(
                    params, options);

            SubscriptionItem item = subscription
                    .getItems().getData().get(0);

            return new StripeSubscriptionResult(
                    subscription.getId(),
                    item.getId(),
                    subscription.getCustomer(),
                    subscription.getStatus(),
                    toLocalDateTime(
                            subscription.getCurrentPeriodStart()),
                    toLocalDateTime(
                            subscription.getCurrentPeriodEnd())
            );

        } catch (StripeException e) {
            log.error("Failed to update Stripe subscription" +
                    " for org {}: {}", orgId, e.getMessage());
            throw new RuntimeException(
                    "Payment service error: " + e.getMessage(),
                    e);
        }
    }

    public void cancelAtPeriodEnd(
            String stripeSubscriptionId,
            String orgId) {
        validateStripeKey();
        try {
            SubscriptionUpdateParams params =
                    SubscriptionUpdateParams.builder()
                            .setCancelAtPeriodEnd(true)
                            .build();

            RequestOptions options =
                    RequestOptions.builder()
                            .setIdempotencyKey(
                                    "cancel-" + orgId
                                            + "-" + stripeSubscriptionId)
                            .build();

            Subscription subscription =
                    Subscription.retrieve(
                            stripeSubscriptionId);
            subscription.update(params, options);

        } catch (StripeException e) {
            log.error("Failed to cancel Stripe subscription" +
                    " for org {}: {}", orgId, e.getMessage());
            throw new RuntimeException(
                    "Payment service error: " + e.getMessage(),
                    e);
        }
    }

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) return null;
        return LocalDateTime.ofInstant(
                Instant.ofEpochSecond(epochSeconds),
                ZoneOffset.UTC);
    }
}