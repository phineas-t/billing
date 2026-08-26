package com.saas.billing.usage;

import com.saas.billing.billing.Plan;
import com.saas.billing.billing.PlanLimits;
import com.saas.billing.billing.Subscription;
import com.saas.billing.billing.SubscriptionRepository;
import com.saas.billing.billing.SubscriptionStatus;
import com.saas.billing.common.TenantContext;
import com.saas.billing.common.exception.UsageLimitExceededException;
import com.saas.billing.organization.Organization;
import com.saas.billing.organization.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsageService {

    private static final String METRIC_API_CALLS = "api_calls";
    private static final String USAGE_KEY_PREFIX = "usage:";
    private static final String IDEMPOTENCY_KEY_PREFIX = "idem:";
    private static final DateTimeFormatter PERIOD_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM");

    private final RedisTemplate<String, String> redisTemplate;
    private final UsageRecordRepository usageRecordRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OrganizationRepository orgRepository;

    /**
     * Core usage check and increment method.
     * Called by UsageInterceptor on every authenticated request.
     *
     * Flow:
     * 1. Resolve orgId from TenantContext
     * 2. Check idempotency key via Redis SETNX — skip if duplicate
     * 3. Get current usage counter from Redis
     * 4. Get plan limit from Redis cache (or DB if cache miss)
     * 5. If at limit → throw UsageLimitExceededException (402)
     * 6. Increment Redis counter atomically
     * 7. Persist UsageRecord to PostgreSQL asynchronously
     */
    public void checkAndIncrementUsage(String idempotencyKey) {
        UUID orgId = TenantContext.getOrgId();
        if (orgId == null) return;

        String billingPeriod = YearMonth.now().format(PERIOD_FORMATTER);
        String usageKey = USAGE_KEY_PREFIX + orgId + ":" + billingPeriod;
        String idemKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        // Idempotency check — skip duplicate requests
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(idemKey, "1", 24, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Duplicate request detected for idempotency key: {}", idempotencyKey);
                return;
            }
        }

        // Get current usage
        String currentValueStr = redisTemplate.opsForValue().get(usageKey);
        long currentUsage = currentValueStr != null ? Long.parseLong(currentValueStr) : 0L;

        // Get plan limit
        long limit = getPlanLimit(orgId, billingPeriod);

        // Enforce limit
        if (currentUsage >= limit) {
            String planCode = getPlanCode(orgId);
            throw new UsageLimitExceededException(currentUsage, limit, planCode, METRIC_API_CALLS);
        }

        // Increment atomically — set expiry to end of current month + 1 day buffer
        Long newCount = redisTemplate.opsForValue().increment(usageKey);
        redisTemplate.expire(usageKey, 32, TimeUnit.DAYS);

        log.debug("Usage incremented for org {}: {}/{}", orgId, newCount, limit);

        // Persist to PostgreSQL asynchronously
        persistUsageRecord(orgId, idempotencyKey, billingPeriod);
    }

    private long getPlanLimit(UUID orgId, String billingPeriod) {
        String limitKey = "limit:" + orgId;
        String cachedLimit = redisTemplate.opsForValue().get(limitKey);

        if (cachedLimit != null) {
            return Long.parseLong(cachedLimit);
        }

        // Cache miss — load from DB
        long limit = subscriptionRepository.findActiveByOrgId(orgId)
                .map(sub -> {
                    PlanLimits limits = sub.getPlan().getLimits();
                    return limits != null ? (long) limits.apiCallsPerMonth() : 1000L;
                })
                .orElse(1000L);

        // Cache for 60 seconds — short enough to reflect upgrades quickly
        redisTemplate.opsForValue().set(limitKey, String.valueOf(limit), 60, TimeUnit.SECONDS);
        return limit;
    }

    private String getPlanCode(UUID orgId) {
        return subscriptionRepository.findActiveByOrgId(orgId)
                .map(sub -> sub.getPlan().getCode().name())
                .orElse("FREE");
    }

    @Transactional
    public void persistUsageRecord(UUID orgId, String idempotencyKey, String billingPeriod) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;

        // DB-level idempotency guard
        if (usageRecordRepository.existsByIdempotencyKey(idempotencyKey)) return;

        Organization org = orgRepository.findById(orgId).orElse(null);
        if (org == null) return;

        Subscription sub = subscriptionRepository.findActiveByOrgId(orgId).orElse(null);

        usageRecordRepository.save(UsageRecord.builder()
                .org(org)
                .subscription(sub)
                .idempotencyKey(idempotencyKey)
                .metric(METRIC_API_CALLS)
                .quantity(1)
                .billingPeriod(billingPeriod)
                .build());
    }

    /**
     * Returns current usage stats for the calling org.
     * Used by GET /usage endpoint.
     */
    public UsageStats getCurrentUsage(UUID orgId) {
        String billingPeriod = YearMonth.now().format(PERIOD_FORMATTER);
        String usageKey = USAGE_KEY_PREFIX + orgId + ":" + billingPeriod;

        String currentValueStr = redisTemplate.opsForValue().get(usageKey);
        long currentUsage = currentValueStr != null ? Long.parseLong(currentValueStr) : 0L;
        long limit = getPlanLimit(orgId, billingPeriod);
        String planCode = getPlanCode(orgId);

        return new UsageStats(orgId, billingPeriod, currentUsage, limit, planCode);
    }

    /**
     * Nightly job — reports metered usage to Stripe.
     * Runs at 2 AM every day.
     * For metered plans, this tells Stripe how much to charge on next invoice.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void reportUsageToStripe() {
        log.info("Starting nightly usage reporting to Stripe");
        String billingPeriod = YearMonth.now().format(PERIOD_FORMATTER);

        subscriptionRepository.findAll().stream()
                .filter(sub -> sub.getStatus() == SubscriptionStatus.ACTIVE)
                .filter(sub -> sub.getStripeSubscriptionId() != null)
                .forEach(sub -> {
                    try {
                        long usage = usageRecordRepository.sumUsageForPeriod(
                                sub.getOrg().getId(), billingPeriod, METRIC_API_CALLS);
                        log.info("Org {} used {} API calls in period {}",
                                sub.getOrg().getId(), usage, billingPeriod);
                        // Stripe usage reporting would go here for metered billing plans
                        // stripe.subscriptionItems.createUsageRecord(itemId, usage, timestamp)
                    } catch (Exception e) {
                        log.error("Failed to report usage for org {}: {}",
                                sub.getOrg().getId(), e.getMessage());
                    }
                });

        log.info("Nightly usage reporting complete");
    }

    public record UsageStats(
            UUID orgId,
            String billingPeriod,
            long currentUsage,
            long limit,
            String planCode) {}
}