package com.saas.billing.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeEventRecorder {

    private final StripeEventRepository stripeEventRepository;

    /**
     * Persists a failed event record in its own independent transaction.
     * REQUIRES_NEW suspends the outer transaction so this commit succeeds
     * even when the caller rethrows and rolls back the outer transaction.
     * Called from a separate bean (WebhookService) so Spring proxy applies correctly.
     * Passes only primitive values — no managed entities cross transaction boundaries.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailedEvent(String eventId, String eventType, String errorMessage) {
        try {
            StripeEvent eventRecord = stripeEventRepository
                    .findByStripeEventId(eventId)
                    .orElse(new StripeEvent());

            eventRecord.setStripeEventId(eventId);
            eventRecord.setEventType(eventType);
            eventRecord.setStatus(StripeEventStatus.FAILED);
            eventRecord.setProcessingAttempts(
                    eventRecord.getProcessingAttempts() == null
                            ? 1
                            : eventRecord.getProcessingAttempts() + 1);
            eventRecord.setLastError(errorMessage);
            eventRecord.setProcessedAt(null);

            stripeEventRepository.save(eventRecord);
        } catch (Exception ex) {
            log.error("Failed to persist FAILED event record for {}: {}",
                    eventId, ex.getMessage());
        }
    }
}