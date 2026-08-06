package com.saas.billing.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Query("""
        SELECT s FROM Subscription s
        WHERE s.org.id = :orgId
        AND s.status <> com.saas.billing.billing
            .SubscriptionStatus.CANCELLED
        ORDER BY s.createdAt DESC
        """)
    Optional<Subscription> findActiveByOrgId(UUID orgId);

    Optional<Subscription> findByStripeSubscriptionId( String stripeSubscriptionId);
}