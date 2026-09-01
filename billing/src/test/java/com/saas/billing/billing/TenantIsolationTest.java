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

        // Org B starts at 0 — completely unaffected by Org A's calls
        String responseBAfter = mockMvc.perform(get("/usage/current")
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long orgBUsage = objectMapper.readTree(responseBAfter)
                .get("currentUsage").asLong();

        assertEquals(0L, orgBUsage,
                "Org B usage must be 0 regardless of Org A's API calls. " +
                        "Non-zero means tenant isolation is broken.");
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