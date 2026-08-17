package com.saas.billing.invoice;

import com.saas.billing.common.TenantContext;
import com.saas.billing.invoice.dto.InvoiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/billing/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    public ResponseEntity<List<InvoiceResponse>>
    getInvoices() {
        UUID orgId = requireOrgId();
        return ResponseEntity.ok(
                invoiceService.getInvoicesForOrg(orgId));
    }

    private UUID requireOrgId() {
        UUID orgId = TenantContext.getOrgId();
        if (orgId == null) {
            throw new IllegalStateException(
                    "Tenant context is missing");
        }
        return orgId;
    }
}