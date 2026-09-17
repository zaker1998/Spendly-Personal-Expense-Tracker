package com.spendly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The session lifecycle: the access token is short-lived and unrevokable, so
 * everything that makes "sign out" mean something lives on the refresh token.
 */
class AuthApiIntegrationTest extends AbstractIntegrationTest {

    private static final String COOKIE = "spendly_refresh";

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@spendly.app";
    }

    private MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static Cookie refreshCookieOf(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).as("refresh cookie").isNotNull();
        return cookie;
    }

    /**
     * The refresh token must never be readable by script in the page — that is
     * the entire reason it is not in the JSON body next to the access token.
     */
    @Test
    void registrationSetsAnHttpOnlyRefreshCookieAndKeepsItOutOfTheBody() throws Exception {
        MvcResult result = register(uniqueEmail("cookie"));

        assertThat(refreshCookieOf(result).isHttpOnly()).isTrue();
        assertThat(refreshCookieOf(result).getPath()).isEqualTo("/api/auth");
        // Lax keeps a third-party page from sending it along with a forged POST.
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("SameSite=Lax");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(refreshCookieOf(result).getValue());
    }

    @Test
    void accessTokenCarriesItsLifetimeSoTheClientCanRefreshBeforeItFails() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(uniqueEmail("expiry"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void refreshExchangesTheCookieForAWorkingAccessToken() throws Exception {
        MvcResult registered = register(uniqueEmail("refresh"));

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookieOf(registered)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(COOKIE))
                .andReturn();

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + tokenFrom(refreshed)))
                .andExpect(status().isOk());
    }

    /**
     * Rotation is what limits the damage of a stolen refresh token: whoever uses
     * it second is rejected.
     */
    @Test
    void aRefreshTokenCannotBeUsedTwice() throws Exception {
        MvcResult registered = register(uniqueEmail("rotate"));
        Cookie original = refreshCookieOf(registered);

        mockMvc.perform(post("/api/auth/refresh").cookie(original)).andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").cookie(original))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshTokenOnTheServer() throws Exception {
        MvcResult registered = register(uniqueEmail("logout"));
        Cookie cookie = refreshCookieOf(registered);

        mockMvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE, 0));

        // Clearing the browser's cookie is not enough on its own; the row has to
        // be dead too, or a copy of the value still works.
        mockMvc.perform(post("/api/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutACookieIsRejectedRatherThanFailing() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    /**
     * Changing the password is how a user evicts someone who already has their
     * session; leaving other refresh tokens alive would defeat the point.
     */
    @Test
    void changingThePasswordEndsEveryOtherSession() throws Exception {
        String email = uniqueEmail("pwchange");
        MvcResult first = register(email);
        Cookie otherDevice = refreshCookieOf(first);

        MvcResult secondLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + tokenFrom(secondLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Secret123!","newPassword":"BrandNew456!"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").cookie(otherDevice))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"BrandNew456!"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void changingThePasswordNeedsTheCurrentOne() throws Exception {
        String email = uniqueEmail("pwwrong");
        MvcResult registered = register(email);

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + tokenFrom(registered))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"NotMyPassword1!","newPassword":"BrandNew456!"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    /** /api/auth/** is otherwise open, so this one needs its own rule. */
    @Test
    void changingThePasswordRequiresASession() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Secret123!","newPassword":"BrandNew456!"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    /** Every response carries an id that ties the user's error to a log line. */
    @Test
    void responsesCarryARequestId() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
