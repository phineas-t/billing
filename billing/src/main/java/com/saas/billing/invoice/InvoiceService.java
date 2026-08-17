package com.saas.billing.invoice;

import com.saas.billing.invoice.dto.InvoiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesForOrg(
            UUID orgId) {
        return invoiceRepository
                .findAllByOrgIdOrderByCreatedAtDesc(orgId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return InvoiceResponse.builder()
                .invoiceId(invoice.getId())
                .orgId(invoice.getOrg().getId())
                .stripeInvoiceId(
                        invoice.getStripeInvoiceId())
                .amountDueCents(
                        invoice.getAmountDueCents())
                .amountPaidCents(
                        invoice.getAmountPaidCents())
                .currency(invoice.getCurrency())
                .status(invoice.getStatus())
                .invoicePdfUrl(invoice.getInvoicePdfUrl())
                .hostedInvoiceUrl(
                        invoice.getHostedInvoiceUrl())
                .periodStart(invoice.getPeriodStart())
                .periodEnd(invoice.getPeriodEnd())
                .createdAt(invoice.getCreatedAt())
                .build();
    }
}