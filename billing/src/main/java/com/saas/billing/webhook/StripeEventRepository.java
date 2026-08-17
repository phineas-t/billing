package com.saas.billing.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StripeEventRepository
        extends JpaRepository<StripeEvent, UUID> {

    boolean existsByStripeEventId(String stripeEventId);

    Optional<StripeEvent> findByStripeEventId(
            String stripeEventId);
}