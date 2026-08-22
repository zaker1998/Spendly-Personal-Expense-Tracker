package com.spendly;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Runs with the demo seeder on, which is also what gives this class an ADMIN
 * account to authenticate as.
 */
@TestPropertySource(properties = "spendly.seed-demo-data=true")
class AdminApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void adminExpensesArePaged() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/admin/expenses")
                        .header("Authorization", "Bearer " + token)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void adminUsersArePaged() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void regularUserCannotReachAdminEndpoints() throws Exception {
        String token = registerAndGetToken("itest-notadmin-" + System.currentTimeMillis() + "@spendly.app");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@spendly.app","password":"Admin123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return tokenFrom(result);
    }
}
