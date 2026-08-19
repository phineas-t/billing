package com.saas.billing.webhook;

import com.saas.billing.billing.Subscription;
import com.saas.billing.billing.SubscriptionRepository;
import com.saas.billing.billing.SubscriptionStatus;
import com.saas.billing.invoice.Invoice;
import com.saas.billing.invoice.InvoiceRepository;
import com.saas.billing.invoice.InvoiceStatus;
import com.saas.billing.organization.Organization;
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
    private final StripeEventRecorder stripeEventRecorder;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;

    @Transactional
    public void processEvent(Event event) {
        Optional<StripeEvent> existing = stripeEventRepository
                .findByStripeEventId(event.getId());

        if (existing.isPresent()
                && existing.get().getStatus() == StripeEventStatus.PROCESSED) {
            log.info("Stripe event {} already processed, skipping", event.getId());
            return;
        }

        if (existing.isPresent()
                && existing.get().getStatus() == StripeEventStatus.FAILED) {
            log.info("Retrying previously failed Stripe event {} ({})",
                    event.getId(), event.getType());
        }

        try {
            routeEvent(event);

            StripeEvent eventRecord = existing.orElse(new StripeEvent());
            eventRecord.setStripeEventId(event.getId());
            eventRecord.setEventType(event.getType());
            eventRecord.setStatus(StripeEventStatus.PROCESSED);
            eventRecord.setProcessingAttempts(
                    existing.map(e -> e.getProcessingAttempts() == null
                                    ? 1 : e.getProcessingAttempts() + 1)
                            .orElse(1));
            eventRecord.setLastError(null);
            eventRecord.setProcessedAt(LocalDateTime.now());
            stripeEventRepository.save(eventRecord);

            log.info("Processed Stripe event {} ({})", event.getId(), event.getType());

        } catch (Exception e) {
            log.error("Failed to process Stripe event {} ({}): {}",
                    event.getId(), event.getType(), e.getMessage());
            stripeEventRecorder.saveFailedEvent(
                    event.getId(), event.getType(), e.getMessage());
            throw e;
        }
    }

    private void routeEvent(Event event) {
        switch (event.getType()) {
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "invoice.payment_succeeded", "invoice.paid" -> handleInvoicePaid(event);
            case "invoice.payment_failed" -> handleInvoiceFailed(event);
            case "invoice.created" -> handleInvoiceCreated(event);
            default -> log.info("Unhandled Stripe event type: {}", event.getType());
        }
    }

    private void handleSubscriptionUpdated(Event event) {
        com.stripe.model.Subscription stripeSub =
                deserialise(event, com.stripe.model.Subscription.class);
        subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId())
                .ifPresentOrElse(sub -> {
                    sub.setStatus(SubscriptionStatus.fromStripe(stripeSub.getStatus()));
                    sub.setCurrentPeriodStart(toLocalDateTime(stripeSub.getCurrentPeriodStart()));
                    sub.setCurrentPeriodEnd(toLocalDateTime(stripeSub.getCurrentPeriodEnd()));
                    sub.setCancelAtPeriodEnd(stripeSub.getCancelAtPeriodEnd());
                    subscriptionRepository.save(sub);
                    log.info("Updated subscription {} status to {}", sub.getId(), sub.getStatus());
                }, () -> log.warn("subscription.updated: no local subscription found for Stripe ID {}",
                        stripeSub.getId()));
    }

    private void handleSubscriptionDeleted(Event event) {
        com.stripe.model.Subscription stripeSub =
                deserialise(event, com.stripe.model.Subscription.class);
        subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId())
                .ifPresentOrElse(sub -> {
                    sub.setStatus(SubscriptionStatus.CANCELLED);
                    sub.setCancelAtPeriodEnd(false);
                    subscriptionRepository.save(sub);
                    log.info("Subscription {} marked CANCELLED via webhook", sub.getId());
                }, () -> log.warn("subscription.deleted: no local subscription found for Stripe ID {}",
                        stripeSub.getId()));
    }

    private void handleInvoicePaid(Event event) {
        com.stripe.model.Invoice stripeInvoice =
                deserialise(event, com.stripe.model.Invoice.class);
        updateSubscriptionStatus(stripeInvoice.getSubscription(), SubscriptionStatus.ACTIVE);
        upsertInvoice(stripeInvoice, InvoiceStatus.PAID);
        log.info("Invoice {} marked PAID", stripeInvoice.getId());
    }

    private void handleInvoiceFailed(Event event) {
        com.stripe.model.Invoice stripeInvoice =
                deserialise(event, com.stripe.model.Invoice.class);
        updateSubscriptionStatus(stripeInvoice.getSubscription(), SubscriptionStatus.PAST_DUE);
        upsertInvoice(stripeInvoice, InvoiceStatus.FAILED);
        log.info("Invoice {} payment FAILED, subscription set to PAST_DUE",
                stripeInvoice.getId());
    }

    private void handleInvoiceCreated(Event event) {
        com.stripe.model.Invoice stripeInvoice =
                deserialise(event, com.stripe.model.Invoice.class);
        upsertInvoice(stripeInvoice, InvoiceStatus.fromStripe(stripeInvoice.getStatus()));
        log.info("Invoice {} created with status {}",
                stripeInvoice.getId(), InvoiceStatus.fromStripe(stripeInvoice.getStatus()));
    }

    private void updateSubscriptionStatus(String stripeSubscriptionId,
                                          SubscriptionStatus newStatus) {
        if (stripeSubscriptionId == null) return;
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .ifPresentOrElse(sub -> {
                    sub.setStatus(newStatus);
                    subscriptionRepository.save(sub);
                }, () -> log.warn("No local subscription found for Stripe ID {}",
                        stripeSubscriptionId));
    }

    private void upsertInvoice(com.stripe.model.Invoice stripeInvoice,
                               InvoiceStatus status) {
        Invoice invoice = invoiceRepository
                .findByStripeInvoiceId(stripeInvoice.getId())
                .orElse(new Invoice());

        // Single lookup — org derived from subscription to avoid duplicate DB call
        Subscription sub = stripeInvoice.getSubscription() != null
                ? subscriptionRepository.findByStripeSubscriptionId(
                stripeInvoice.getSubscription()).orElse(null)
                : null;

        Organization org = sub != null ? sub.getOrg() : null;

        if (org == null) {
            log.warn("Cannot save invoice {} — no matching org found",
                    stripeInvoice.getId());
            return;
        }

        invoice.setOrg(org);
        invoice.setSubscription(sub);
        invoice.setStripeInvoiceId(stripeInvoice.getId());
        invoice.setAmountDueCents(stripeInvoice.getAmountDue() != null
                ? stripeInvoice.getAmountDue().intValue() : 0);
        invoice.setAmountPaidCents(stripeInvoice.getAmountPaid() != null
                ? stripeInvoice.getAmountPaid().intValue() : 0);
        invoice.setCurrency(stripeInvoice.getCurrency() != null
                ? stripeInvoice.getCurrency() : "usd");
        invoice.setStatus(status);
        invoice.setInvoicePdfUrl(stripeInvoice.getInvoicePdf());
        invoice.setHostedInvoiceUrl(stripeInvoice.getHostedInvoiceUrl());
        invoice.setPeriodStart(toLocalDateTime(stripeInvoice.getPeriodStart()));
        invoice.setPeriodEnd(toLocalDateTime(stripeInvoice.getPeriodEnd()));

        invoiceRepository.save(invoice);
    }

    /**
     * Deserialises the Stripe event data object into the expected type.
     * Throws IllegalArgumentException if deserialisation fails or type mismatches.
     * This ensures failures trigger a Stripe retry (500 response) rather than
     * being silently marked PROCESSED without applying the state change.
     */
    @SuppressWarnings("unchecked")
    private <T extends StripeObject> T deserialise(Event event, Class<T> type) {
        StripeObject obj = event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unable to deserialise Stripe event object for event: "
                                + event.getId()));

        if (!type.isInstance(obj)) {
            throw new IllegalArgumentException(
                    "Unexpected Stripe object type for event " + event.getId()
                            + ". Expected: " + type.getSimpleName()
                            + ", got: " + obj.getClass().getSimpleName());
        }

        return type.cast(obj);
    }

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) return null;
        return LocalDateTime.ofInstant(
                Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }
}