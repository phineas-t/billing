package com.saas.billing.common.exception;

import lombok.Getter;

@Getter
public class UsageLimitExceededException extends RuntimeException {

    private final long currentUsage;
    private final long limit;
    private final String planCode;
    private final String metric;

    public UsageLimitExceededException(long currentUsage, long limit,
                                       String planCode, String metric) {
        super(String.format(
                "Usage limit exceeded for %s. Current: %d, Limit: %d on plan %s",
                metric, currentUsage, limit, planCode));
        this.currentUsage = currentUsage;
        this.limit = limit;
        this.planCode = planCode;
        this.metric = metric;
    }
}