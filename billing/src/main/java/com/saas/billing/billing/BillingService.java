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
    public SubscriptionResponse subscribe(
            SubscribeRequest request) {

        UUID orgId = TenantContext.getOrgId();

        Organization org = orgRepository
                .findById(orgId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Organization not found"));

        subscriptionRepository
                .findActiveByOrgId(orgId)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Organization already has an " +
                                    "active subscription. " +
                                    "Use upgrade to change plans.");
                });

        Plan plan = planRepository
                .findByIdAndActiveTrue(request.getPlanId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Plan not found or not available"));

        Subscription subscription;

        if (plan.isFree()) {
            subscription = createFreeSubscription(
                    org, plan);
        } else {
            subscription = createPaidSubscription(
                    org, plan);
        }

        return toResponse(subscription);
    }

    @Transactional
    public SubscriptionResponse upgrade(
            UpgradeRequest request) {

        UUID orgId = TenantContext.getOrgId();

        Subscription current = subscriptionRepository
                .findActiveByOrgId(orgId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No active subscription found. " +
                                        "Subscribe first."));

        Plan newPlan = planRepository
                .findByIdAndActiveTrue(request.getNewPlanId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Plan not found or not available"));

        if (current.getPlan().getId()
                .equals(newPlan.getId())) {
            throw new IllegalArgumentException(
                    "Already on this plan");
        }

        if (current.getPlan().isFree()
                && newPlan.isStripeBackedPlan()) {
            return upgradeFreeToPayd(
                    current, newPlan, orgId);
        }

        if (current.getPlan().isStripeBackedPlan()
                && newPlan.isStripeBackedPlan()) {
            return upgradePaidToPaid(
                    current, newPlan, orgId);
        }

        if (newPlan.isFree()) {
            return downgradeToFree(current, newPlan);
        }

        throw new IllegalStateException(
                "Unexpected plan transition");
    }

    @Transactional
    public SubscriptionResponse cancel() {

        UUID orgId = TenantContext.getOrgId();

        Subscription subscription = subscriptionRepository
                .findActiveByOrgId(orgId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No active subscription to cancel"));

        if (subscription.getCancelAtPeriodEnd()) {
            throw new IllegalStateException(
                    "Subscription is already scheduled " +
                            "for cancellation");
        }

        if (subscription.getPlan().isStripeBackedPlan()
                && subscription
                .getStripeSubscriptionId() != null) {
            stripeBillingClient.cancelAtPeriodEnd(
                    subscription.getStripeSubscriptionId(),
                    orgId.toString());
        }

        subscription.setCancelAtPeriodEnd(true);
        subscriptionRepository.save(subscription);

        log.info("Subscription {} scheduled for " +
                        "cancellation at period end for org {}",
                subscription.getId(), orgId);

        return toResponse(subscription);
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getCurrentSubscription() {

        UUID orgId = TenantContext.getOrgId();

        Subscription subscription = subscriptionRepository
                .findActiveByOrgId(orgId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No active subscription found"));

        return toResponse(subscription);
    }

    private Subscription createFreeSubscription(
            Organization org, Plan plan) {

        Subscription subscription = Subscription.builder()
                .org(org)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .cancelAtPeriodEnd(false)
                .build();

        Subscription saved = subscriptionRepository
                .save(subscription);

        log.info("Created FREE subscription {} for org {}",
                saved.getId(), org.getId());

        return saved;
    }

    private Subscription createPaidSubscription(
            Organization org, Plan plan) {

        String customerId = ensureStripeCustomer(org);

        StripeSubscriptionResult result =
                stripeBillingClient.createSubscription(
                        customerId,
                        plan.getStripePriceId(),
                        org.getId().toString(),
                        plan.getId().toString()
                );

        Subscription subscription = Subscription.builder()
                .org(org)
                .plan(plan)
                .stripeCustomerId(result.customerId())
                .stripeSubscriptionId(
                        result.subscriptionId())
                .stripeSubscriptionItemId(
                        result.subscriptionItemId())
                .status(SubscriptionStatus
                        .fromStripe(result.status()))
                .currentPeriodStart(
                        result.currentPeriodStart())
                .currentPeriodEnd(
                        result.currentPeriodEnd())
                .cancelAtPeriodEnd(false)
                .build();

        Subscription saved = subscriptionRepository
                .save(subscription);

        log.info("Created PAID subscription {} " +
                        "(Stripe: {}) for org {}",
                saved.getId(),
                result.subscriptionId(),
                org.getId());

        return saved;
    }

    private SubscriptionResponse upgradeFreeToPayd(
            Subscription current,
            Plan newPlan,
            UUID orgId) {

        Organization org = current.getOrg();
        String customerId = ensureStripeCustomer(org);

        StripeSubscriptionResult result =
                stripeBillingClient.createSubscription(
                        customerId,
                        newPlan.getStripePriceId(),
                        orgId.toString(),
                        newPlan.getId().toString()
                );

        current.setPlan(newPlan);
        current.setStripeCustomerId(result.customerId());
        current.setStripeSubscriptionId(
                result.subscriptionId());
        current.setStripeSubscriptionItemId(
                result.subscriptionItemId());
        current.setStatus(SubscriptionStatus
                .fromStripe(result.status()));
        current.setCurrentPeriodStart(
                result.currentPeriodStart());
        current.setCurrentPeriodEnd(
                result.currentPeriodEnd());

        return toResponse(
                subscriptionRepository.save(current));
    }

    private SubscriptionResponse upgradePaidToPaid(
            Subscription current,
            Plan newPlan,
            UUID orgId) {

        StripeSubscriptionResult result =
                stripeBillingClient.updateSubscription(
                        current.getStripeSubscriptionId(),
                        current.getStripeSubscriptionItemId(),
                        newPlan.getStripePriceId(),
                        orgId.toString(),
                        newPlan.getId().toString()
                );

        current.setPlan(newPlan);
        current.setStripeSubscriptionItemId(
                result.subscriptionItemId());
        current.setStatus(SubscriptionStatus
                .fromStripe(result.status()));
        current.setCurrentPeriodStart(
                result.currentPeriodStart());
        current.setCurrentPeriodEnd(
                result.currentPeriodEnd());

        return toResponse(
                subscriptionRepository.save(current));
    }

    private SubscriptionResponse downgradeToFree(
            Subscription current,
            Plan freePlan) {

        if (current.getStripeSubscriptionId() != null) {
            stripeBillingClient.cancelAtPeriodEnd(
                    current.getStripeSubscriptionId(),
                    current.getOrg().getId().toString());
        }

        current.setPlan(freePlan);
        current.setStatus(SubscriptionStatus.ACTIVE);
        current.setStripeSubscriptionId(null);
        current.setStripeSubscriptionItemId(null);
        current.setCancelAtPeriodEnd(false);

        return toResponse(
                subscriptionRepository.save(current));
    }

    private String ensureStripeCustomer(Organization org) {

        if (org.getStripeCustomerId() != null
                && !org.getStripeCustomerId().isBlank()) {
            return org.getStripeCustomerId();
        }

        StripeCustomerResult result =
                stripeBillingClient.createCustomer(
                        org.getEmail(),
                        org.getName(),
                        org.getId().toString()
                );

        org.setStripeCustomerId(result.customerId());
        orgRepository.save(org);

        log.info("Created Stripe customer {} for org {}",
                result.customerId(), org.getId());

        return result.customerId();
    }

    private SubscriptionResponse toResponse(
            Subscription s) {
        return SubscriptionResponse.builder()
                .subscriptionId(s.getId())
                .orgId(s.getOrg().getId())
                .planCode(s.getPlan().getCode().name())
                .planDisplayName(
                        s.getPlan().getDisplayName())
                .status(s.getStatus())
                .monthlyPriceCents(
                        s.getPlan().getMonthlyPriceCents())
                .limits(s.getPlan().getLimits())
                .currentPeriodStart(
                        s.getCurrentPeriodStart())
                .currentPeriodEnd(s.getCurrentPeriodEnd())
                .cancelAtPeriodEnd(
                        s.getCancelAtPeriodEnd())
                .stripeSubscriptionId(
                        s.getStripeSubscriptionId())
                .createdAt(s.getCreatedAt())
                .build();
    }
}