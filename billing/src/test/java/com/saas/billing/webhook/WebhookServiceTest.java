package com.saas.billing.webhook;

import com.saas.billing.billing.Subscription;
import com.saas.billing.billing.SubscriptionRepository;
import com.saas.billing.billing.SubscriptionStatus;
import com.saas.billing.invoice.InvoiceRepository;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookServiceTest {

    @Mock
    private StripeEventRepository stripeEventRepository;

    @Mock
    private StripeEventRecorder stripeEventRecorder;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private WebhookService webhookService;

    private Event buildMockEvent(String id, String type) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn(type);

        EventDataObjectDeserializer deserializer =
                mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.empty());
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }

    @Test
    void processEvent_alreadyProcessed_skipsProcessing() {
        Event event = buildMockEvent("evt_123", "customer.created");

        StripeEvent existingEvent = StripeEvent.builder()
                .stripeEventId("evt_123")
                .eventType("customer.created")
                .status(StripeEventStatus.PROCESSED)
                .processingAttempts(1)
                .build();

        when(stripeEventRepository.findByStripeEventId("evt_123"))
                .thenReturn(Optional.of(existingEvent));

        webhookService.processEvent(event);

        verify(stripeEventRepository, never()).save(any());
    }

    @Test
    void processEvent_newUnhandledEvent_savedAsProcessed() {
        Event event = buildMockEvent("evt_new", "customer.created");

        when(stripeEventRepository.findByStripeEventId("evt_new"))
                .thenReturn(Optional.empty());
        when(stripeEventRepository.save(any(StripeEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        webhookService.processEvent(event);

        verify(stripeEventRepository).save(
                argThat(e -> e.getStatus() == StripeEventStatus.PROCESSED
                        && e.getStripeEventId().equals("evt_new")));
    }

    @Test
    void processEvent_failedEventRetried_incrementsAttemptCount() {
        Event event = buildMockEvent("evt_retry", "customer.created");

        StripeEvent failedEvent = StripeEvent.builder()
                .stripeEventId("evt_retry")
                .eventType("customer.created")
                .status(StripeEventStatus.FAILED)
                .processingAttempts(1)
                .lastError("Previous failure")
                .build();

        when(stripeEventRepository.findByStripeEventId("evt_retry"))
                .thenReturn(Optional.of(failedEvent));
        when(stripeEventRepository.save(any(StripeEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        webhookService.processEvent(event);

        verify(stripeEventRepository).save(
                argThat(e -> e.getStatus() == StripeEventStatus.PROCESSED
                        && e.getProcessingAttempts() == 2
                        && e.getLastError() == null));
    }

    @Test
    void processEvent_subscriptionDeleted_updatesLocalStatus() {
        String stripeSubId = "sub_test_123";

        com.stripe.model.Subscription stripeSub =
                mock(com.stripe.model.Subscription.class,
                        withSettings().lenient());
        when(stripeSub.getId()).thenReturn(stripeSubId);
        when(stripeSub.getStatus()).thenReturn("canceled");
        when(stripeSub.getCancelAtPeriodEnd()).thenReturn(false);
        when(stripeSub.getCurrentPeriodStart()).thenReturn(null);
        when(stripeSub.getCurrentPeriodEnd()).thenReturn(null);

        EventDataObjectDeserializer deserializer =
                mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject())
                .thenReturn(Optional.of(stripeSub));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_sub_deleted");
        when(event.getType())
                .thenReturn("customer.subscription.deleted");
        when(event.getDataObjectDeserializer())
                .thenReturn(deserializer);

        Subscription localSub = mock(Subscription.class);
        when(subscriptionRepository
                .findByStripeSubscriptionId(stripeSubId))
                .thenReturn(Optional.of(localSub));
        when(stripeEventRepository
                .findByStripeEventId("evt_sub_deleted"))
                .thenReturn(Optional.empty());
        when(stripeEventRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        webhookService.processEvent(event);

        verify(localSub).setStatus(SubscriptionStatus.CANCELLED);
        verify(localSub).setCancelAtPeriodEnd(false);
        verify(subscriptionRepository).save(localSub);
    }
}