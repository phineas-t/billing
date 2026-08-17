package com.saas.billing.billing;

import com.saas.billing.billing.dto.SubscribeRequest;
import com.saas.billing.billing.dto.SubscriptionResponse;
import com.saas.billing.billing.dto.UpgradeRequest;
import com.saas.billing.billing.stripe.StripeBillingClient;
import com.saas.billing.billing.stripe.StripeCustomerResult;
import com.saas.billing.billing.stripe.StripeSubscriptionResult;
import com.saas.billing.common.TenantContext;
import com.saas.billing.organization.Organization;
import com.saas.billing.organization.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final OrganizationRepository orgRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StripeBillingClient stripeBillingClient;

    @Transactional
    public SubscriptionResponse subscribe(SubscribeRequest request) {

        UUID orgId = requireOrgId();

        Organization org = orgRepository.findById(orgId).orElseThrow(() ->
                new IllegalStateException("Organization not found"));

        subscriptionRepository.findActiveByOrgId(orgId).ifPresent(existing -> {
            throw new IllegalStateException("Organization already has an " + "active subscription. " + "Use upgrade to change plans.");
        });

        Plan plan = planRepository.findByIdAndActiveTrue(request.getPlanId()).orElseThrow(() ->
                new IllegalArgumentException("Plan not found or not available"));

        Subscription subscription;

        if (plan.isFree()) {
            subscription = createFreeSubscription(org, plan);
        } else {
            subscription = createPaidSubscription(org, plan);
        }

        return toResponse(subscription);
    }

    @Transactional
    public SubscriptionResponse upgrade(UpgradeRequest request) {

        UUID orgId = requireOrgId();

        Subscription current = subscriptionRepository.findActiveByOrgId(orgId).orElseThrow(() ->
                new IllegalStateException("No active subscription found. " + "Subscribe first."));

        Plan newPlan = planRepository.findByIdAndActiveTrue(request.getNewPlanId()).orElseThrow(() ->
                new IllegalArgumentException("Plan not found or not available"));

        if (current.getPlan().getId().equals(newPlan.getId())) {
            throw new IllegalArgumentException("Already on this plan");
        }

        if (current.getPlan().isFree() && newPlan.isStripeBackedPlan()) {
            return upgradeFreeToPaid(current, newPlan, orgId);
        }

        if (current.getPlan().isStripeBackedPlan() && newPlan.isStripeBackedPlan()) {
            return upgradePaidToPaid(current, newPlan, orgId);
        }

        if (newPlan.isFree()) {
            return downgradeToFree(current);
        }

        throw new IllegalStateException("Unexpected plan transition");
    }

    @Transactional
    public SubscriptionResponse cancel() {

        UUID orgId = requireOrgId();

        Subscription subscription = subscriptionRepository.findActiveByOrgId(orgId).orElseThrow(() ->
                new IllegalStateException("No active subscription to cancel"));

        if (subscription.getCancelAtPeriodEnd()) {
            throw new IllegalStateException("Subscription is already scheduled " + "for cancellation");
        }

        if (subscription.getPlan().isFree()) {
            subscription.setStatus(SubscriptionStatus.CANCELLED);
            subscription.setCancelAtPeriodEnd(false);
            subscriptionRepository.save(subscription);

            log.info("Cancelled FREE subscription {} " + "for org {}", subscription.getId(), orgId);

            return toResponse(subscription);
        }

        stripeBillingClient.cancelAtPeriodEnd(subscription.getStripeSubscriptionId(), orgId.toString());

        subscription.setCancelAtPeriodEnd(true);
        subscriptionRepository.save(subscription);

        log.info("Paid subscription {} scheduled for " + "cancellation at period end for org {}", subscription.getId(), orgId);

        return toResponse(subscription);
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getCurrentSubscription() {

        UUID orgId = requireOrgId();

        Subscription subscription = subscriptionRepository.findActiveByOrgId(orgId).orElseThrow(() ->
                new IllegalStateException("No active subscription found"));

        return toResponse(subscription);
    }

    private Subscription createFreeSubscription(Organization org, Plan plan) {

        Subscription subscription = Subscription.builder().
                org(org).
                plan(plan).
                status(SubscriptionStatus.ACTIVE).
                cancelAtPeriodEnd(false).build();

        Subscription saved = subscriptionRepository.save(subscription);

        log.info("Created FREE subscription {} for org {}", saved.getId(), org.getId());

        return saved;
    }

    private Subscription createPaidSubscription(Organization org, Plan plan) {

        String customerId = ensureStripeCustomer(org);

        /**
         * operationId makes the Stripe idempotency key unique per attempt.
         *
         * Trade-off: generating a new UUID on every call means client retries
         * produce different idempotency keys, so Stripe treats each retry as
         * a new operation. This is safer for the resubscribe-within-24-hours
         * scenario (same org, same plan, within Stripe's idempotency window)
         * but is NOT true retry-safe idempotency.
         *
         * Known limitation: if the Stripe call succeeds but the DB save fails,
         * a client retry could create a second Stripe subscription. The local
         * partial unique index protects against duplicate local records, but
         * not against orphaned Stripe subscriptions.
         *
         * Production fix: use client-provided idempotency keys passed via
         * request header (e.g. Idempotency-Key: <uuid>) so retries of the
         * same client request always use the same key.
         */
        String operationId = UUID.randomUUID().toString();

        StripeSubscriptionResult result = stripeBillingClient.
                createSubscription(customerId, plan.getStripePriceId(), org.getId().toString(), plan.getId().toString(), operationId);

        Subscription subscription = Subscription.builder().
                org(org).
                plan(plan).
                stripeCustomerId(result.customerId()).
                stripeSubscriptionId(result.subscriptionId()).
                stripeSubscriptionItemId(result.subscriptionItemId()).
                status(SubscriptionStatus.fromStripe(result.status())).
                currentPeriodStart(result.currentPeriodStart()).
                currentPeriodEnd(result.currentPeriodEnd()).
                cancelAtPeriodEnd(false).
                build();

        Subscription saved = subscriptionRepository.save(subscription);

        log.info("Created PAID subscription {} " + "(Stripe: {}) for org {}", saved.getId(), result.subscriptionId(), org.getId());

        return saved;
    }

    private SubscriptionResponse upgradeFreeToPaid(Subscription current, Plan newPlan, UUID orgId) {

        Organization org = current.getOrg();
        String customerId = ensureStripeCustomer(org);
        String operationId = UUID.randomUUID().toString();

        StripeSubscriptionResult result = stripeBillingClient.createSubscription(customerId, newPlan.getStripePriceId(), orgId.toString(), newPlan.getId().toString(), operationId);

        current.setPlan(newPlan);
        current.setStripeCustomerId(result.customerId());
        current.setStripeSubscriptionId(result.subscriptionId());
        current.setStripeSubscriptionItemId(result.subscriptionItemId());
        current.setStatus(SubscriptionStatus.fromStripe(result.status()));
        current.setCurrentPeriodStart(result.currentPeriodStart());
        current.setCurrentPeriodEnd(result.currentPeriodEnd());

        return toResponse(subscriptionRepository.save(current));
    }

    private SubscriptionResponse upgradePaidToPaid(Subscription current, Plan newPlan, UUID orgId) {

        StripeSubscriptionResult result = stripeBillingClient.updateSubscription(current.getStripeSubscriptionId(), current.getStripeSubscriptionItemId(), newPlan.getStripePriceId(), orgId.toString(), newPlan.getId().toString());

        current.setPlan(newPlan);
        current.setStripeSubscriptionItemId(result.subscriptionItemId());
        current.setStatus(SubscriptionStatus.fromStripe(result.status()));
        current.setCurrentPeriodStart(result.currentPeriodStart());
        current.setCurrentPeriodEnd(result.currentPeriodEnd());

        return toResponse(subscriptionRepository.save(current));
    }

    /**
     * Paid → Free downgrade means: schedule cancellation at period end.
     * The org keeps paid-plan access until currentPeriodEnd.
     * Stripe fires customer.subscription.deleted when the period ends.
     * Slice 5 webhook catches that event and marks this subscription CANCELLED.
     * After that the org can manually subscribe to the Free plan.
     * <p>
     * Known limitation: this does not automatically apply Free after cancellation.
     * That would require a pending_plan_id field and Slice 5 webhook support.
     */
    private SubscriptionResponse downgradeToFree(Subscription current) {

        if (current.getCancelAtPeriodEnd()) {
            throw new IllegalStateException("Subscription is already scheduled " + "for cancellation");
        }

        if (current.getStripeSubscriptionId() != null) {
            stripeBillingClient.cancelAtPeriodEnd(current.getStripeSubscriptionId(), current.getOrg().getId().toString());
        }

        current.setCancelAtPeriodEnd(true);

        Subscription saved = subscriptionRepository.save(current);

        log.info("Paid subscription {} scheduled for " + "cancellation at period end (paid -> Free " + "downgrade) for org {}", saved.getId(), current.getOrg().getId());

        return toResponse(saved);
    }

    private String ensureStripeCustomer(Organization org) {

        if (org.getStripeCustomerId() != null && !org.getStripeCustomerId().isBlank()) {
            return org.getStripeCustomerId();
        }

        StripeCustomerResult result = stripeBillingClient.createCustomer(org.getEmail(), org.getName(), org.getId().toString());

        org.setStripeCustomerId(result.customerId());
        orgRepository.save(org);

        log.info("Created Stripe customer {} for org {}", result.customerId(), org.getId());

        return result.customerId();
    }

    private SubscriptionResponse toResponse(Subscription s) {
        return SubscriptionResponse.builder().subscriptionId(s.getId()).orgId(s.getOrg().getId()).planCode(s.getPlan().getCode().name()).planDisplayName(s.getPlan().getDisplayName()).status(s.getStatus()).monthlyPriceCents(s.getPlan().getMonthlyPriceCents()).limits(s.getPlan().getLimits()).currentPeriodStart(s.getCurrentPeriodStart()).currentPeriodEnd(s.getCurrentPeriodEnd()).cancelAtPeriodEnd(s.getCancelAtPeriodEnd()).stripeSubscriptionId(s.getStripeSubscriptionId()).createdAt(s.getCreatedAt()).build();
    }

    private UUID requireOrgId() {
        UUID orgId = TenantContext.getOrgId();
        if (orgId == null) {
            throw new IllegalStateException("Tenant context is missing. " + "This operation requires authentication.");
        }
        return orgId;
    }
}