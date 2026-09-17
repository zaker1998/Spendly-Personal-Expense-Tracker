package com.spendly;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Nothing in the codebase enforces "this row belongs to the caller" centrally —
 * every service method takes the user id and every query repeats the predicate.
 * That works exactly as long as nobody forgets, so each endpoint that can reach
 * another user's row gets a test that proves it does not.
 */
class OwnershipIntegrationTest extends AbstractIntegrationTest {

    private String ownerToken;
    private String intruderToken;
    private long ownerCategoryId;
    private long ownerExpenseId;
    private long ownerBudgetId;

    @BeforeEach
    void createTwoAccountsWithData() throws Exception {
        long stamp = System.nanoTime();
        ownerToken = registerAndGetToken("owner-" + stamp + "@spendly.app");
        intruderToken = registerAndGetToken("intruder-" + stamp + "@spendly.app");

        ownerCategoryId = firstCategoryId(ownerToken);

        ownerExpenseId = idFrom(mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"amount":12.50,"spentOn":"2026-03-14","description":"Private"}
                                """.formatted(ownerCategoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        ownerBudgetId = idFrom(mockMvc.perform(post("/api/budgets")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"amount":500.00,"year":2026,"month":3}
                                """.formatted(ownerCategoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void anotherUsersExpenseIsInvisible() throws Exception {
        mockMvc.perform(get("/api/expenses/" + ownerExpenseId)
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersExpenseCannotBeEditedOrDeleted() throws Exception {
        mockMvc.perform(put("/api/expenses/" + ownerExpenseId)
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"amount":1.00,"spentOn":"2026-03-14"}
                                """.formatted(firstCategoryId(intruderToken))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/expenses/" + ownerExpenseId)
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersCategoryCannotBeEditedOrDeleted() throws Exception {
        mockMvc.perform(put("/api/categories/" + ownerCategoryId)
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hijacked","color":"#112233"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/categories/" + ownerCategoryId)
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersBudgetCannotBeEditedOrDeleted() throws Exception {
        mockMvc.perform(put("/api/budgets/" + ownerBudgetId)
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":9999.00,"year":2026,"month":3}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/budgets/" + ownerBudgetId)
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    /**
     * The category id travels in the request body, so an expense is the obvious
     * place to try to attach yourself to somebody else's category.
     */
    @Test
    void anExpenseCannotBeFiledAgainstAnotherUsersCategory() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"amount":5.00,"spentOn":"2026-03-14"}
                                """.formatted(ownerCategoryId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aBudgetCannotBeFiledAgainstAnotherUsersCategory() throws Exception {
        mockMvc.perform(post("/api/budgets")
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"amount":100.00,"year":2026,"month":4}
                                """.formatted(ownerCategoryId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsAndSummariesOnlyShowTheCallersOwnRows() throws Exception {
        mockMvc.perform(get("/api/expenses").header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/summary/monthly?year=2026&month=3")
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(0))
                .andExpect(jsonPath("$.byCategory").isEmpty());

        mockMvc.perform(get("/api/budgets?year=2026&month=3")
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private long firstCategoryId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get(0).get("id").asLong();
    }

    private long idFrom(String json) throws Exception {
        return objectMapper.readTree(json).get("id").asLong();
    }
}
