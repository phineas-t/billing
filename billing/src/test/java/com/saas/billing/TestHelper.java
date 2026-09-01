package com.saas.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TestHelper {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public TestHelper(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a unique email per test run to avoid duplicate email conflicts.
     * Uses UUID suffix so every test gets a fresh email regardless of
     * database state.
     */
    public static String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID()
                .toString().substring(0, 8) + "@test.com";
    }

    public String registerAndLogin(String companyName,
                                   String email,
                                   String password) throws Exception {
        String registerBody = """
                {
                  "companyName": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(companyName, email, password);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        String loginBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(
                        result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    public String[] registerAndLoginWithRefreshToken(
            String companyName,
            String email,
            String password) throws Exception {

        String registerBody = """
                {
                  "companyName": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(companyName, email, password);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        String loginBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(json)
                .get("accessToken").asText();
        String refreshToken = objectMapper.readTree(json)
                .get("refreshToken").asText();

        return new String[]{accessToken, refreshToken};
    }
}