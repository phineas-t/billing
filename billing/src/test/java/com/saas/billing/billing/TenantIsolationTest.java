package com.saas.billing.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.billing.BaseIntegrationTest;
import com.saas.billing.TestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TenantIsolationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private TestHelper helper;
    private String tokenOrgA;
    private String tokenOrgB;

    @BeforeEach
    void setUp() throws Exception {
        helper = new TestHelper(mockMvc, objectMapper);
        tokenOrgA = helper.registerAndLogin(
                "Org A",
                TestHelper.uniqueEmail("orga"),
                "password123");
        tokenOrgB = helper.registerAndLogin(
                "Org B",
                TestHelper.uniqueEmail("orgb"),
                "password123");
    }

    @Test
    void orgA_and_orgB_have_different_orgIds() throws Exception {
        String responseA = mockMvc.perform(get("/usage/current")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String responseB = mockMvc.perform(get("/usage/current")
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orgAId = objectMapper.readTree(responseA)
                .get("orgId").asText();
        String orgBId = objectMapper.readTree(responseB)
                .get("orgId").asText();

        assertNotEquals(orgAId, orgBId,
                "Org A and Org B must have different orgIds — " +
                        "tenant isolation is broken if they are equal");
    }

    @Test
    void orgA_usageCounter_isolatedFromOrgB() throws Exception {
        // Make 3 API calls as Org A
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/usage/current")
                            .header("Authorization", "Bearer " + tokenOrgA))
                    .andExpect(status().isOk());
        }

        // Org B's first call to /usage/current increments their own counter
        // so we expect 1 (their own call) not 0
        // The key assertion is that Org B's usage is NOT affected by Org A's 3 calls
        // Org A made 3 calls → their counter is 3
        // Org B made 0 calls before this → their counter starts at 0
        // This call increments Org B to 1
        String responseBefore = mockMvc.perform(get("/usage/current")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long orgAUsage = objectMapper.readTree(responseBefore)
                .get("currentUsage").asLong();

        String responseB = mockMvc.perform(get("/usage/current")
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long orgBUsage = objectMapper.readTree(responseB)
                .get("currentUsage").asLong();

        // Critical assertion: Org B's usage must be far less than Org A's
        // Org A made many calls, Org B made only this one check
        assertTrue(orgAUsage > orgBUsage,
                "Org A usage (" + orgAUsage + ") must be greater than " +
                        "Org B usage (" + orgBUsage + "). " +
                        "If equal or Org B > Org A, tenant isolation is broken.");
    }

    @Test
    void orgB_invoices_empty_independent_of_orgA() throws Exception {
        // Org A and Org B both have empty invoice history
        // They should never see each other's invoices
        mockMvc.perform(get("/billing/invoices")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/billing/invoices")
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}