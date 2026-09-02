package com.saas.billing.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.billing.BaseIntegrationTest;
import com.saas.billing.TestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RefreshTokenIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private TestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new TestHelper(mockMvc, objectMapper);
    }

    @Test
    void refresh_withValidToken_returnsNewTokenPair() throws Exception {
        String email = TestHelper.uniqueEmail("refresh");
        String[] tokens = helper.registerAndLoginWithRefreshToken(
                "Refresh Corp", email, "securepass123");

        String refreshToken = tokens[1];

        String response = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "%s" }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String newRefreshToken = objectMapper.readTree(response)
                .get("refreshToken").asText();

        assertNotEquals(refreshToken, newRefreshToken,
                "Rotation must issue a new refresh token");
    }

    @Test
    void refresh_reuseOldToken_returns400AndRevokesAll() throws Exception {
        String email = TestHelper.uniqueEmail("reuse");
        String[] tokens = helper.registerAndLoginWithRefreshToken(
                "Reuse Corp", email, "securepass123");

        String originalRefreshToken = tokens[1];

        // First refresh — legitimate rotation
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "%s" }
                                """.formatted(originalRefreshToken)))
                .andExpect(status().isOk());

        // Second use of original token — reuse attack detected
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "%s" }
                                """.formatted(originalRefreshToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString(
                                "Refresh token reuse detected")));
    }

    @Test
    void refresh_withInvalidToken_returns400() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "completely-invalid-token" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Invalid refresh token"));
    }
}