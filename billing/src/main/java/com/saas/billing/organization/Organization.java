package com.saas.billing.organization;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder //to generate builder pattern Organization.builder().name("Acme").email("a@b.com").build()
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "stripe_customer_id") //no NOT NULL here deliberately. When an org first registers, they don't have a Stripe customer yet. That gets filled in later when they subscribe.
    private String stripeCustomerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrgStatus status;

    @Column(name = "created_at", updatable = false,nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = OrgStatus.ACTIVE;
        }
    }

    public enum OrgStatus {
        ACTIVE, SUSPENDED, CANCELLED
    }
}