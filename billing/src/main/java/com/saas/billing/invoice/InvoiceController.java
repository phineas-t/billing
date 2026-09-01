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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Invoices", description = "Invoice history and billing records")
@RestController
@RequestMapping("/billing/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @Operation(summary = "Get invoice history",
            description = "Returns all invoices for the calling organisation, " +
                    "newest first. Includes Stripe hosted invoice URLs.")
    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> getInvoices() {
        UUID orgId = requireOrgId();
        return ResponseEntity.ok(invoiceService.getInvoicesForOrg(orgId));
    }

    private UUID requireOrgId() {
        UUID orgId = TenantContext.getOrgId();
        if (orgId == null) throw new IllegalStateException("Tenant context is missing");
        return orgId;
    }
}