package com.saas.billing.billing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
public class UpgradeRequest {

    @NotNull(message = "New plan ID is required")
    private UUID newPlanId;
}