package com.saas.billing.usage;

import com.saas.billing.billing.PlanLimits;
import com.saas.billing.billing.Subscription;
import com.saas.billing.billing.SubscriptionRepository;
import com.saas.billing.billing.SubscriptionStatus;
import com.saas.billing.common.exception.UsageLimitExceededException;
import com.saas.billing.organization.Organization;
import com.saas.billing.organization.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsageService {

    private static final String METRIC_API_CALLS = "api_calls";
    private static final String USAGE_KEY_PREFIX = "usage:";
    private static final String IDEMPOTENCY_KEY_PREFIX = "idem:";
    private static final String LIMIT_KEY_PREFIX = "limit:";
    private static final DateTimeFormatter PERIOD_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM");

    private final RedisTemplate<String, String> redisTemplate;
    private final UsageRecordRepository usageRecordRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OrganizationRepository orgRepository;

    /**
     * Core usage check and increment.
     * orgId is passed explicitly — never read from TenantContext here.
     * This makes the method testable and callable from any context.
     *
     * Flow:
     * 1. Validate orgId is not null
     * 2. Redis SETNX idempotency check — skip if duplicate request
     * 3. Get current counter from Redis
     * 4. Get plan limit from Redis cache (60s TTL), fallback to DB
     * 5. If at limit — throw UsageLimitExceededException (402)
     * 6. Increment counter atomically (Redis INCR)
     * 7. Persist UsageRecord to PostgreSQL asynchronously
     */
    public void checkAndIncrementUsage(UUID orgId, String idempotencyKey) {
        if (orgId == null) {
            throw new IllegalStateException(
                    "orgId must not be null in checkAndIncrementUsage");
        }

        String billingPeriod = YearMonth.now().format(PERIOD_FORMATTER);
        String usageKey = USAGE_KEY_PREFIX + orgId + ":" + billingPeriod;
        String idemKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        // Idempotency check — skip duplicate requests
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(idemKey, "1", 24, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Duplicate request skipped for idempotency key: {}",
                        idempotencyKey);
                return;
            }
        }

        // Get current usage
        String currentValueStr = redisTemplate.opsForValue().get(usageKey);
        long currentUsage = currentValueStr != null
                ? Long.parseLong(currentValueStr) : 0L;

        // Get plan limit — Redis cache first, DB fallback
        long limit = getPlanLimit(orgId);

        // Enforce limit
        if (currentUsage >= limit) {
            String planCode = getPlanCode(orgId);
            log.info("Usage limit reached for org {} on plan {}: {}/{}",
                    orgId, planCode, currentUsage, limit);
            throw new UsageLimitExceededException(
                    currentUsage, limit, planCode, METRIC_API_CALLS);
        }

        // Atomic increment
        Long newCount = redisTemplate.opsForValue().increment(usageKey);
        redisTemplate.expire(usageKey, 32, TimeUnit.DAYS);
        log.debug("Usage incremented for org {}: {}/{}", orgId, newCount, limit);

        // Persist to PostgreSQL asynchronously — does not block request thread
        persistUsageRecord(orgId, idempotencyKey, billingPeriod);
    }

    private long getPlanLimit(UUID orgId) {
        String limitKey = LIMIT_KEY_PREFIX + orgId;
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
                .orElseGet(() -> {
                    log.warn("No active subscription found for org {} " +
                            "— defaulting to Free plan limit of 1000", orgId);
                    return 1000L;
                });

        // Cache for 60 seconds — short enough to reflect upgrades promptly
        redisTemplate.opsForValue().set(limitKey, String.valueOf(limit),
                60, TimeUnit.SECONDS);
        return limit;
    }

    private String getPlanCode(UUID orgId) {
        return subscriptionRepository.findActiveByOrgId(orgId)
                .map(sub -> sub.getPlan().getCode().name())
                .orElse("FREE");
    }

    /**
     * Persists usage record to PostgreSQL.
     * @Async runs in a separate thread pool — does not block the request thread.
     * Requires @EnableAsync on BillingApplication.
     */
    @Async
    @Transactional
    public void persistUsageRecord(UUID orgId, String idempotencyKey,
                                   String billingPeriod) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;

        // DB-level idempotency guard — belt and braces after Redis SETNX
        if (usageRecordRepository.existsByIdempotencyKey(idempotencyKey)) return;

        Organization org = orgRepository.findById(orgId).orElse(null);
        if (org == null) {
            log.warn("Cannot persist usage record — org {} not found", orgId);
            return;
        }

        Subscription sub = subscriptionRepository
                .findActiveByOrgId(orgId).orElse(null);

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
     * Returns current usage stats for the given org.
     * Used by GET /usage/current.
     */
    public UsageStats getCurrentUsage(UUID orgId) {
        String billingPeriod = YearMonth.now().format(PERIOD_FORMATTER);
        String usageKey = USAGE_KEY_PREFIX + orgId + ":" + billingPeriod;

        String currentValueStr = redisTemplate.opsForValue().get(usageKey);
        long currentUsage = currentValueStr != null
                ? Long.parseLong(currentValueStr) : 0L;
        long limit = getPlanLimit(orgId);
        String planCode = getPlanCode(orgId);

        return new UsageStats(orgId, billingPeriod, currentUsage, limit, planCode);
    }

    /**
     * Nightly job — reports metered usage to Stripe.
     * Uses targeted query instead of findAll() to avoid loading
     * cancelled/free subscriptions into memory.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void reportUsageToStripe() {
        log.info("Starting nightly usage reporting to Stripe");
        String billingPeriod = YearMonth.now().format(PERIOD_FORMATTER);

        subscriptionRepository.findAllActiveWithStripeSubscription()
                .forEach(sub -> {
                    try {
                        long usage = usageRecordRepository.sumUsageForPeriod(
                                sub.getOrg().getId(),
                                billingPeriod,
                                METRIC_API_CALLS);
                        log.info("Org {} used {} API calls in period {}",
                                sub.getOrg().getId(), usage, billingPeriod);
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