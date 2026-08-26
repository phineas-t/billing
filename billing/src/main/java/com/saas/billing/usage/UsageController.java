package com.saas.billing.usage;

import com.saas.billing.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/usage")
@RequiredArgsConstructor
public class UsageController {

    private final UsageService usageService;

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