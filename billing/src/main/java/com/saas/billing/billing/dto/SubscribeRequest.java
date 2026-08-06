package com.saas.billing.billing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
public class SubscribeRequest {

    @NotNull(message = "Plan ID is required")
    private UUID planId;
}