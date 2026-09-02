package com.saas.billing.usage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.billing.BaseIntegrationTest;
import com.saas.billing.TestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UsageLimitTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private TestHelper helper;
    private String token;
    private String orgId;

    @BeforeEach
    void setUp() throws Exception {
        helper = new TestHelper(mockMvc, objectMapper);
        String email = TestHelper.uniqueEmail("usagelimit");
        token = helper.registerAndLogin(
                "Usage Corp", email, "securepass123");

        // Get orgId from usage/current
        String response = mockMvc.perform(get("/usage/current")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        orgId = objectMapper.readTree(response)
                .get("orgId").asText();
    }

    @Test
    void underLimit_requestSucceeds() throws Exception {
        mockMvc.perform(get("/usage/current")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUsage")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)));
    }

    @Test
    void atLimit_returns402WithUsageDetails() throws Exception {
        String billingPeriod = YearMonth.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String usageKey = "usage:" + orgId + ":" + billingPeriod;

        // Set counter to exactly the Free plan limit (1000)
        redisTemplate.opsForValue().set(usageKey, "1000");

        mockMvc.perform(get("/billing/subscription")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.error")
                        .value("Usage limit exceeded"))
                .andExpect(jsonPath("$.currentUsage").value(1000))
                .andExpect(jsonPath("$.limit").value(1000))
                .andExpect(jsonPath("$.planCode").isNotEmpty())
                .andExpect(jsonPath("$.upgradeUrl")
                        .value("/billing/upgrade"));
    }

    @Test
    void idempotentRequest_countedOnlyOnce() throws Exception {
        String billingPeriod = YearMonth.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String usageKey = "usage:" + orgId + ":" + billingPeriod;

        // Clear counter
        redisTemplate.delete(usageKey);

        String idempotencyKey = "test-idem-" + orgId;

        // Send same request twice with same idempotency key
        mockMvc.perform(get("/usage/current")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk());

        mockMvc.perform(get("/usage/current")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk());

        // Counter should be 1, not 2
        String counterValue = redisTemplate.opsForValue().get(usageKey);
        long count = counterValue != null ? Long.parseLong(counterValue) : 0L;

        org.junit.jupiter.api.Assertions.assertEquals(1L, count,
                "Idempotent requests must only increment counter once. " +
                        "Counter is " + count + " — duplicate counting detected.");
    }
}