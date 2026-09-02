package com.saas.billing.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.billing.BaseIntegrationTest;
import com.saas.billing.TestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private TestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new TestHelper(mockMvc, objectMapper);
    }

    @Test
    void register_withValidData_returns201AndTokens() throws Exception {
        String email = TestHelper.uniqueEmail("register");

        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Test Corp",
                                  "email": "%s",
                                  "password": "securepass123"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accessToken = objectMapper.readTree(response)
                .get("accessToken").asText();
        assertNotNull(accessToken);
        assertFalse(accessToken.isBlank());
    }

    @Test
    void register_withInvalidEmail_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Test Corp",
                                  "email": "notanemail",
                                  "password": "securepass123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").isNotEmpty());
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
                                """.formatted(TestHelper.uniqueEmail("short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").isNotEmpty());
    }

    @Test
    void register_withDuplicateEmail_returns400() throws Exception {
        String email = TestHelper.uniqueEmail("duplicate");
        String body = """
                {
                  "companyName": "Dupe Corp",
                  "email": "%s",
                  "password": "securepass123"
                }
                """.formatted(email);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("An account with this email already exists"));
    }

    @Test
    void login_withWrongPassword_returns400() throws Exception {
        String email = TestHelper.uniqueEmail("wrongpass");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Wrong Pass Corp",
                                  "email": "%s",
                                  "password": "securepass123"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "wrongpassword"
                                }
                                """.formatted(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Invalid email or password"));
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/billing/subscription"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidToken_returns200() throws Exception {
        String token = helper.registerAndLogin(
                "Token Corp",
                TestHelper.uniqueEmail("tokentest"),
                "securepass123");

        mockMvc.perform(get("/usage/current")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgId").isNotEmpty())
                .andExpect(jsonPath("$.currentUsage")
                        .value(org.hamcrest.Matchers
                                .greaterThanOrEqualTo(0)));
    }

    @Test
    void emailNormalisation_loginWithUppercase_succeeds() throws Exception {
        String email = TestHelper.uniqueEmail("normalise");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Normalise Corp",
                                  "email": "%s",
                                  "password": "securepass123"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "securepass123"
                                }
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }
}