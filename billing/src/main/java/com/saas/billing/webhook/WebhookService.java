package com.saas.billing.webhook;

import com.saas.billing.billing.Subscription;
import com.saas.billing.billing.SubscriptionRepository;
import com.saas.billing.billing.SubscriptionStatus;
import com.saas.billing.invoice.Invoice;
import com.saas.billing.invoice.InvoiceRepository;
import com.saas.billing.invoice.InvoiceStatus;
import com.saas.billing.organization.Organization;
import com.saas.billing.organization.OrganizationRepository;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final StripeEventRepository stripeEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final OrganizationRepository orgRepository;

    @Transactional
    public void processEvent(Event event) {

        if (stripeEventRepository
                .existsByStripeEventId(event.getId())) {
            log.info("Stripe event {} already processed," +
                    " skipping", event.getId());
            return;
        }

        try {
            routeEvent(event);

            stripeEventRepository.save(
                    StripeEvent.builder()
                            .stripeEventId(event.getId())
                            .eventType(event.getType())
                            .status(StripeEventStatus.PROCESSED)
                            .processingAttempts(1)
                            .processedAt(LocalDateTime.now())
                            .build()
            );

            log.info("Processed Stripe event {} ({})",
                    event.getId(), event.getType());

        } catch (Exception e) {
            log.error("Failed to process Stripe event" +
                            " {} ({}): {}",
                    event.getId(),
                    event.getType(),
                    e.getMessage());

            stripeEventRepository.save(
                    StripeEvent.builder()
                            .stripeEventId(event.getId())
                            .eventType(event.getType())
                            .status(StripeEventStatus.FAILED)
                            .processingAttempts(1)
                            .lastError(e.getMessage())
                            .build()
            );

            throw e;
        }
    }

    private void routeEvent(Event event) {
        switch (event.getType()) {
            case "customer.subscription.updated" ->
                    handleSubscriptionUpdated(event);
            case "customer.subscription.deleted" ->
                    handleSubscriptionDeleted(event);
            case "invoice.payment_succeeded",
                 "invoice.paid" ->
                    handleInvoicePaid(event);
            case "invoice.payment_failed" ->
                    handleInvoiceFailed(event);
            case "invoice.created" ->
                    handleInvoiceCreated(event);
            default -> log.info(
                    "Unhandled Stripe event type: {}",
                    event.getType());
        }
    }

    private void handleSubscriptionUpdated(Event event) {
        com.stripe.model.Subscription stripeSub =
                deserialise(event,
                        com.stripe.model.Subscription.class);
        if (stripeSub == null) return;

        Optional<Subscription> localSubOpt =
                subscriptionRepository
                        .findByStripeSubscriptionId(
                                stripeSub.getId());

        if (localSubOpt.isEmpty()) {
            log.warn("subscription.updated: no local " +
                            "subscription found for Stripe ID {}",
                    stripeSub.getId());
            return;
        }

        Subscription sub = localSubOpt.get();
        sub.setStatus(SubscriptionStatus
                .fromStripe(stripeSub.getStatus()));
        sub.setCurrentPeriodStart(
                toLocalDateTime(
                        stripeSub.getCurrentPeriodStart()));
        sub.setCurrentPeriodEnd(
                toLocalDateTime(
                        stripeSub.getCurrentPeriodEnd()));
        sub.setCancelAtPeriodEnd(
                stripeSub.getCancelAtPeriodEnd());

        subscriptionRepository.save(sub);

        log.info("Updated subscription {} status to {}",
                sub.getId(), sub.getStatus());
    }

    private void handleSubscriptionDeleted(Event event) {
        com.stripe.model.Subscription stripeSub =
                deserialise(event,
                        com.stripe.model.Subscription.class);
        if (stripeSub == null) return;

        Optional<Subscription> localSubOpt =
                subscriptionRepository
                        .findByStripeSubscriptionId(
                                stripeSub.getId());

        if (localSubOpt.isEmpty()) {
            log.warn("subscription.deleted: no local " +
                            "subscription found for Stripe ID {}",
                    stripeSub.getId());
            return;
        }

        Subscription sub = localSubOpt.get();
        sub.setStatus(SubscriptionStatus.CANCELLED);
        sub.setCancelAtPeriodEnd(false);
        subscriptionRepository.save(sub);

        log.info("Subscription {} marked CANCELLED " +
                "via webhook", sub.getId());
    }

    private void handleInvoicePaid(Event event) {
        com.stripe.model.Invoice stripeInvoice =
                deserialise(event,
                        com.stripe.model.Invoice.class);
        if (stripeInvoice == null) return;

        updateSubscriptionStatus(
                stripeInvoice.getSubscription(),
                SubscriptionStatus.ACTIVE);

        upsertInvoice(stripeInvoice, InvoiceStatus.PAID);

        log.info("Invoice {} marked PAID",
                stripeInvoice.getId());
    }

    private void handleInvoiceFailed(Event event) {
        com.stripe.model.Invoice stripeInvoice =
                deserialise(event,
                        com.stripe.model.Invoice.class);
        if (stripeInvoice == null) return;

        updateSubscriptionStatus(
                stripeInvoice.getSubscription(),
                SubscriptionStatus.PAST_DUE);

        upsertInvoice(stripeInvoice, InvoiceStatus.FAILED);

        log.info("Invoice {} payment FAILED, " +
                        "subscription set to PAST_DUE",
                stripeInvoice.getId());
    }

    private void handleInvoiceCreated(Event event) {
        com.stripe.model.Invoice stripeInvoice =
                deserialise(event,
                        com.stripe.model.Invoice.class);
        if (stripeInvoice == null) return;

        InvoiceStatus status = InvoiceStatus
                .fromStripe(stripeInvoice.getStatus());

        upsertInvoice(stripeInvoice, status);

        log.info("Invoice {} created with status {}",
                stripeInvoice.getId(), status);
    }

    private void updateSubscriptionStatus(
            String stripeSubscriptionId,
            SubscriptionStatus newStatus) {

        if (stripeSubscriptionId == null) return;

        subscriptionRepository
                .findByStripeSubscriptionId(
                        stripeSubscriptionId)
                .ifPresentOrElse(
                        sub -> {
                            sub.setStatus(newStatus);
                            subscriptionRepository.save(sub);
                        },
                        () -> log.warn(
                                "No local subscription found " +
                                        "for Stripe ID {}",
                                stripeSubscriptionId)
                );
    }

    private void upsertInvoice(
            com.stripe.model.Invoice stripeInvoice,
            InvoiceStatus status) {

        Invoice invoice = invoiceRepository
                .findByStripeInvoiceId(stripeInvoice.getId())
                .orElse(new Invoice());

        Organization org = null;
        if (stripeInvoice.getSubscription() != null) {
            org = subscriptionRepository
                    .findByStripeSubscriptionId(
                            stripeInvoice.getSubscription())
                    .map(Subscription::getOrg)
                    .orElse(null);
        }

        Subscription sub = null;
        if (stripeInvoice.getSubscription() != null) {
            sub = subscriptionRepository
                    .findByStripeSubscriptionId(
                            stripeInvoice.getSubscription())
                    .orElse(null);
        }

        invoice.setStripeInvoiceId(
                stripeInvoice.getId());
        invoice.setAmountDueCents(
                stripeInvoice.getAmountDue() != null
                        ? stripeInvoice.getAmountDue()
                        .intValue()
                        : 0);
        invoice.setAmountPaidCents(
                stripeInvoice.getAmountPaid() != null
                        ? stripeInvoice.getAmountPaid()
                        .intValue()
                        : 0);
        invoice.setCurrency(
                stripeInvoice.getCurrency() != null
                        ? stripeInvoice.getCurrency()
                        : "usd");
        invoice.setStatus(status);
        invoice.setInvoicePdfUrl(
                stripeInvoice.getInvoicePdf());
        invoice.setHostedInvoiceUrl(
                stripeInvoice.getHostedInvoiceUrl());
        invoice.setPeriodStart(
                toLocalDateTime(
                        stripeInvoice.getPeriodStart()));
        invoice.setPeriodEnd(
                toLocalDateTime(
                        stripeInvoice.getPeriodEnd()));

        if (org != null) invoice.setOrg(org);
        if (sub != null) invoice.setSubscription(sub);

        if (invoice.getOrg() == null) {
            log.warn("Cannot save invoice {} — " +
                            "no matching org found",
                    stripeInvoice.getId());
            return;
        }

        invoiceRepository.save(invoice);
    }

    @SuppressWarnings("unchecked")
    private <T extends StripeObject> T deserialise(
            Event event, Class<T> type) {
        Optional<StripeObject> obj = event
                .getDataObjectDeserializer()
                .getObject();

        if (obj.isEmpty()) {
            log.warn("Could not deserialise event {} " +
                    "data object", event.getId());
            return null;
        }

        try {
            return (T) obj.get();
        } catch (ClassCastException e) {
            log.error("Event {} data object is not " +
                            "of expected type {}",
                    event.getId(), type.getSimpleName());
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(
            Long epochSeconds) {
        if (epochSeconds == null) return null;
        return LocalDateTime.ofInstant(
                Instant.ofEpochSecond(epochSeconds),
                ZoneOffset.UTC);
    }
}