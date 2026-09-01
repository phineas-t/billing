package com.saas.billing.auth;

import com.saas.billing.BaseIntegrationTest;
import com.saas.billing.TestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ValidationTest extends BaseIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "notanemail",
            "missing@",
            "@nodomain.com"
    })
    void register_withInvalidEmail_returns400(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Test Corp",
                                  "email": "%s",
                                  "password": "securepass123"
                                }
                                """.formatted(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").isNotEmpty());
    }

    @Test
    void register_withBlankCompanyName_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "",
                                  "email": "%s",
                                  "password": "securepass123"
                                }
                                """.formatted(TestHelper.uniqueEmail("blank"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.companyName").isNotEmpty());
    }

    @Test
    void register_withShortPassword_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Test Corp",
                                  "email": "%s",
                                  "password": "short"
                                }
                                """.formatted(TestHelper.uniqueEmail("shortpass"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").isNotEmpty());
    }

    @Test
    void register_withMissingBody_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}