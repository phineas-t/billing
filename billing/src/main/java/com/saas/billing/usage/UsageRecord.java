package com.saas.billing.usage;

import com.saas.billing.billing.Subscription;
import com.saas.billing.organization.Organization;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "usage_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization org;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(nullable = false, length = 100)
    private String metric;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @Column(name = "billing_period", nullable = false, length = 20)
    private String billingPeriod;

    @PrePersist
    protected void onCreate() {
        if (this.recordedAt == null) this.recordedAt = LocalDateTime.now();
        if (this.metric == null) this.metric = "api_calls";
        if (this.quantity == null) this.quantity = 1;
    }
}