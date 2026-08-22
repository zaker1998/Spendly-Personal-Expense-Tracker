package com.spendly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ExpenseApiIntegrationTest extends AbstractIntegrationTest {

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

    /**
     * The monthly summary is cached, and the eviction now runs after the write
     * commits rather than during it. A read straight after a write must still
     * see the new expense.
     */
    @Test
    void summaryReflectsAnExpenseCreatedAfterItWasCached() throws Exception {
        String token = registerAndGetToken("itest-cache-" + System.currentTimeMillis() + "@spendly.app");
        long categoryId = firstCategoryId(token);

        mockMvc.perform(get("/api/summary/monthly")
                        .header("Authorization", "Bearer " + token)
                        .param("year", "2026")
                        .param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(0));

        createExpense(token, categoryId, "25.00", "2026-03-04");

        mockMvc.perform(get("/api/summary/monthly")
                        .header("Authorization", "Bearer " + token)
                        .param("year", "2026")
                        .param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(25.00));
    }

    /**
     * Currency is server-controlled now; a client-supplied code must not end up
     * on the row, because every aggregate assumes a single currency.
     */
    @Test
    void currencyFromTheClientIsIgnored() throws Exception {
        String token = registerAndGetToken("itest-currency-" + System.currentTimeMillis() + "@spendly.app");
        long categoryId = firstCategoryId(token);

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "amount": 10.00, "currency": "USD", "spentOn": "2026-04-02"}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    private long firstCategoryId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(0).get("id").asLong();
    }

    private void createExpense(String token, long categoryId, String amount, String spentOn) throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "amount": %s, "spentOn": "%s"}
                                """.formatted(categoryId, amount, spentOn)))
                .andExpect(status().isCreated());
    }
}
