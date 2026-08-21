package com.spendly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ExpenseApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("spendly")
            .withUsername("spendly")
            .withPassword("spendly");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginCreateExpenseAndSummarize() throws Exception {
        String email = "itest-" + System.currentTimeMillis() + "@spendly.app";

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        MvcResult categoriesResult = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode categories = objectMapper.readTree(categoriesResult.getResponse().getContentAsString());
        assertThat(categories.isArray()).isTrue();
        assertThat(categories.size()).isGreaterThanOrEqualTo(5);
        long categoryId = categories.get(0).get("id").asLong();

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "amount": 19.99,
                                  "currency": "EUR",
                                  "spentOn": "2026-07-15",
                                  "description": "Test lunch"
                                }
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryName").isNotEmpty())
                .andExpect(jsonPath("$.amount").value(19.99));

        mockMvc.perform(get("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .param("search", "lunch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("Test lunch"));

        mockMvc.perform(get("/api/expenses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/summary/monthly")
                        .header("Authorization", "Bearer " + token)
                        .param("year", "2026")
                        .param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(19.99));
    }

    @Test
    void invalidMonthReturns400InsteadOf500() throws Exception {
        String email = "itest-badmonth-" + System.currentTimeMillis() + "@spendly.app";
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/summary/monthly")
                        .header("Authorization", "Bearer " + token)
                        .param("year", "2026")
                        .param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.month").isNotEmpty());

        mockMvc.perform(get("/api/summary/monthly")
                        .header("Authorization", "Bearer " + token)
                        .param("year", "2026")
                        .param("month", "not-a-number"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestReturns401Json() throws Exception {
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void otherUsersExpenseIsNotAccessible() throws Exception {
        long now = System.currentTimeMillis();
        String tokenA = registerAndGetToken("itest-owner-" + now + "@spendly.app");
        String tokenB = registerAndGetToken("itest-intruder-" + now + "@spendly.app");

        MvcResult categoriesResult = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        long categoryId = objectMapper.readTree(categoriesResult.getResponse().getContentAsString())
                .get(0).get("id").asLong();

        MvcResult createResult = mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "amount": 42.00, "spentOn": "2026-07-01", "description": "Private"}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andReturn();
        long expenseId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(get("/api/expenses/" + expenseId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
