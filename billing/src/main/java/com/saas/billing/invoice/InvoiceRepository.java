package com.saas.billing.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository
        extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByStripeInvoiceId(
            String stripeInvoiceId);

    List<Invoice> findAllByOrgIdOrderByCreatedAtDesc(
            UUID orgId);
}