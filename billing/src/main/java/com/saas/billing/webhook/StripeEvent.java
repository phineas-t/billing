package com.saas.billing.webhook;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stripe_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StripeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "stripe_event_id",
            nullable = false, unique = true,
            length = 255)
    private String stripeEventId;

    @Column(name = "event_type",
            nullable = false, length = 100)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StripeEventStatus status;

    @Column(name = "processing_attempts",
            nullable = false)
    private Integer processingAttempts;

    @Column(name = "last_error",
            columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "received_at", nullable = false,
            updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        if (this.receivedAt == null)
            this.receivedAt = LocalDateTime.now();
        if (this.processingAttempts == null)
            this.processingAttempts = 1;
        if (this.status == null)
            this.status = StripeEventStatus.PROCESSED;
    }
}