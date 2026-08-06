package com.saas.billing.billing;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50)
    private PlanCode code;

    @Column(name = "display_name", nullable = false,
            length = 100)
    private String displayName;

    @Column(name = "stripe_price_id", length = 255)
    private String stripePriceId;

    @Column(name = "monthly_price_cents", nullable = false)
    private Integer monthlyPriceCents;

    @Column(name = "billing_interval",
            nullable = false, length = 20)
    private String billingInterval;

    @Convert(converter = PlanLimitsConverter.class)
    @Column(columnDefinition = "jsonb")
    private PlanLimits limits;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false,
            updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.active == null) this.active = true;
        if (this.billingInterval == null)
            this.billingInterval = "MONTHLY";
    }

    public boolean isFree() {
        return this.code == PlanCode.FREE;
    }

    public boolean isStripeBackedPlan() {
        return this.stripePriceId != null
                && !this.stripePriceId.isBlank();
    }
}