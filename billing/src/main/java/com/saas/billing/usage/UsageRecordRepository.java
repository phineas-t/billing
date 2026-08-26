package com.saas.billing.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface UsageRecordRepository
        extends JpaRepository<UsageRecord, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COALESCE(SUM(ur.quantity), 0) FROM UsageRecord ur " +
            "WHERE ur.org.id = :orgId AND ur.billingPeriod = :billingPeriod " +
            "AND ur.metric = :metric")
    long sumUsageForPeriod(UUID orgId, String billingPeriod, String metric);
}