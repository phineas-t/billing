package com.saas.billing.usage;

import com.saas.billing.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Usage", description = "Real-time API usage tracking and limits")
@RestController
@RequestMapping("/usage")
@RequiredArgsConstructor
public class UsageController {

    private final UsageService usageService;

    @Operation(summary = "Get current usage",
            description = "Returns the calling organisation's API usage " +
                    "for the current billing period from Redis. " +
                    "Includes current count, plan limit, and plan code.")
    @GetMapping("/current")
    public ResponseEntity<UsageService.UsageStats> getCurrentUsage() {
        UUID orgId = requireOrgId();
        return ResponseEntity.ok(usageService.getCurrentUsage(orgId));
    }

    private UUID requireOrgId() {
        UUID orgId = TenantContext.getOrgId();
        if (orgId == null) throw new IllegalStateException("Tenant context is missing");
        return orgId;
    }
}