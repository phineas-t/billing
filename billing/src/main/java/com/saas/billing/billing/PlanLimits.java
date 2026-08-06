package com.saas.billing.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanLimits( int apiCallsPerMonth, int seats, int storageMb ) {
    public static PlanLimits defaults() {
        return new PlanLimits(1000, 1, 512);
    }
}