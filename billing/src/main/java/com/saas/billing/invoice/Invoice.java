package com.saas.billing.invoice;

import com.saas.billing.billing.Subscription;
import com.saas.billing.organization.Organization;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

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

    @Column(name = "stripe_invoice_id",
            nullable = false, unique = true,
            length = 255)
    private String stripeInvoiceId;

    @Column(name = "amount_due_cents",
            nullable = false)
    private Integer amountDueCents;

    @Column(name = "amount_paid_cents",
            nullable = false)
    private Integer amountPaidCents;

    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InvoiceStatus status;

    @Column(name = "invoice_pdf_url", length = 500)
    private String invoicePdfUrl;

    @Column(name = "hosted_invoice_url", length = 500)
    private String hostedInvoiceUrl;

    @Column(name = "period_start")
    private LocalDateTime periodStart;

    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    @Column(name = "created_at",
            nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.currency == null)
            this.currency = "usd";
        if (this.amountDueCents == null)
            this.amountDueCents = 0;
        if (this.amountPaidCents == null)
            this.amountPaidCents = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}