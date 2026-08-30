package com.saas.billing.usage;

import com.saas.billing.common.TenantContext;
import com.saas.billing.common.exception.UsageLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UsageInterceptor implements HandlerInterceptor {

    private final UsageService usageService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        UUID orgId = TenantContext.getOrgId();
        if (orgId == null) return true;

        String idempotencyKey = request.getHeader("X-Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = UUID.randomUUID().toString();
        }

        try {
            usageService.checkAndIncrementUsage(orgId, idempotencyKey);
        } catch (UsageLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.error("UsageInterceptor error for org {}: {}",
                    orgId, e.getMessage(), e);
            return true;
        }

        return true;
    }
}