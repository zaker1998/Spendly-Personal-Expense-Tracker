package com.spendly;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * What is reachable without credentials, and what the API says when a request is
 * wrong. Each of these answers a specific way the perimeter used to leak.
 */
class PerimeterIntegrationTest extends AbstractIntegrationTest {

    /**
     * /actuator/prometheus used to fall through to the catch-all permitAll rule
     * and hand anyone the JVM, pool and cache metrics.
     */
    @Test
    void metricsAreNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndProbesStayPublicSoTheProxyCanRouteTraffic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void aSignedInUserWhoIsNotAnAdminStillCannotReadMetrics() throws Exception {
        String token = registerAndGetToken("metrics-" + System.nanoTime() + "@spendly.app");

        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /**
     * An unknown API path must answer as an API. With the SPA fallback mapped on
     * {@code /**} it used to return index.html with status 200, so a client
     * waiting for JSON got a page of HTML and no sign that the path was wrong.
     */
    @Test
    void anUnknownApiPathIsAJsonNotFound() throws Exception {
        mockMvc.perform(get("/api/does-not-exist")
                        .header("Authorization", "Bearer " + registerAndGetToken(
                                "notfound-" + System.nanoTime() + "@spendly.app")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("No endpoint for this path"));
    }

    @Test
    void responsesCarryTheSecurityHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().exists("Permissions-Policy"));
    }

    /**
     * An unknown sort property is a client mistake. It used to reach the
     * catch-all handler and be answered with 500 plus a stack trace in the log,
     * so ?sort=anything was a cheap way to fill the error log.
     */
    @Test
    void anUnknownSortPropertyIsABadRequestNotAServerError() throws Exception {
        String token = registerAndGetToken("sort-" + System.nanoTime() + "@spendly.app");

        mockMvc.perform(get("/api/expenses?sort=nosuchfield,desc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("nosuchfield")));
    }

    /** Spring's default cap is 2000 rows; ?size=5000 must not be honoured. */
    @Test
    void pageSizeIsCapped() throws Exception {
        String token = registerAndGetToken("pagesize-" + System.nanoTime() + "@spendly.app");

        mockMvc.perform(get("/api/expenses?size=5000").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    /**
     * NUMERIC(19,2) is the real limit; before the DTO said so, an oversized
     * amount reached the database and came back as 409 "Conflicts with existing
     * data", and a third decimal was silently rounded away.
     */
    @Test
    void anAmountTheColumnCannotHoldIsRejectedAsABadRequest() throws Exception {
        String token = registerAndGetToken("amount-" + System.nanoTime() + "@spendly.app");
        long categoryId = objectMapper.readTree(
                        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getContentAsString())
                .get(0).get("id").asLong();

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"amount":123456789012345678901.00,"spentOn":"2026-03-14"}
                                """.formatted(categoryId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").isNotEmpty());

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"amount":10.999,"spentOn":"2026-03-14"}
                                """.formatted(categoryId)))
                .andExpect(status().isBadRequest());
    }

    /** An expense filed against the year 9999 never appeared in any monthly view. */
    @Test
    void aDateOutsideTheSupportedRangeIsRejected() throws Exception {
        String token = registerAndGetToken("date-" + System.nanoTime() + "@spendly.app");
        long categoryId = objectMapper.readTree(
                        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getContentAsString())
                .get(0).get("id").asLong();

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"amount":5.00,"spentOn":"9999-01-01"}
                                """.formatted(categoryId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.spentOn").isNotEmpty());
    }

    /** The colour is rendered into a style binding, so only a hex colour is accepted. */
    @Test
    void aCategoryColourMustBeHex() throws Exception {
        String token = registerAndGetToken("colour-" + System.nanoTime() + "@spendly.app");

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Odd","color":"url(javascript)"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.color").isNotEmpty());
    }

    /**
     * Two tabs editing the same expense used to overwrite one another without
     * either of them being told.
     */
    @Test
    void aWriteBuiltOnAStaleCopyIsRejected() throws Exception {
        String token = registerAndGetToken("version-" + System.nanoTime() + "@spendly.app");
        long categoryId = objectMapper.readTree(
                        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getContentAsString())
                .get(0).get("id").asLong();

        String created = mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"amount":10.00,"spentOn":"2026-03-14"}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        // First tab saves.
        mockMvc.perform(put("/api/expenses/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"amount":20.00,"spentOn":"2026-03-14","version":0}
                                """.formatted(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // Second tab still holds version 0.
        mockMvc.perform(put("/api/expenses/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"amount":30.00,"spentOn":"2026-03-14","version":0}
                                """.formatted(categoryId)))
                .andExpect(status().isConflict());
    }

    /**
     * The cached monthly summary carries category names, and a rename is the one
     * write that changes them without touching an expense.
     */
    @Test
    void renamingACategoryRefreshesTheCachedSummary() throws Exception {
        String token = registerAndGetToken("rename-" + System.nanoTime() + "@spendly.app");
        long categoryId = objectMapper.readTree(
                        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getContentAsString())
                .get(0).get("id").asLong();

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"amount":10.00,"spentOn":"2026-05-10"}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated());

        // Warm the cache.
        mockMvc.perform(get("/api/summary/monthly?year=2026&month=5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/categories/" + categoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed","color":"#2A9D8F"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/summary/monthly?year=2026&month=5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byCategory[0].categoryName").value("Renamed"));
    }
}
