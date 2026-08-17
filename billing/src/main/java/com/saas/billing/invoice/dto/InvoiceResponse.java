package com.saas.billing.invoice.dto;

import com.saas.billing.invoice.InvoiceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class InvoiceResponse {

    private UUID invoiceId;
    private UUID orgId;
    private String stripeInvoiceId;
    private Integer amountDueCents;
    private Integer amountPaidCents;
    private String currency;
    private InvoiceStatus status;
    private String invoicePdfUrl;
    private String hostedInvoiceUrl;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private LocalDateTime createdAt;
}