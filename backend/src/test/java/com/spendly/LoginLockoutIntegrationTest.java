package com.spendly;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Lockout is off in the shared test profile because its state outlives a single
 * test class; this is the one class that turns it on, so it gets its own context.
 */
@TestPropertySource(properties = {
        "spendly.login-protection.enabled=true",
        "spendly.login-protection.max-attempts=3",
        "spendly.login-protection.lockout-minutes=15"
})
class LoginLockoutIntegrationTest extends AbstractIntegrationTest {

    /**
     * The per-IP rate limit does not stop a distributed attack on one account:
     * each host gets its own bucket. Counting per account is what does.
     */
    @Test
    void repeatedFailuresLockTheAccountAndTheRightPasswordStopsWorking() throws Exception {
        String email = "lockout-" + System.nanoTime() + "@spendly.app";
        registerAndGetToken(email);

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(login(email, "WrongPassword" + attempt))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login(email, "WrongAgain1!"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // Knowing the password is no help while the lock is on — otherwise an
        // attacker who guesses correctly on attempt four still gets in.
        mockMvc.perform(login(email, "Secret123!"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void anUnrelatedAccountIsUnaffected() throws Exception {
        String victim = "victim-" + System.nanoTime() + "@spendly.app";
        String bystander = "bystander-" + System.nanoTime() + "@spendly.app";
        registerAndGetToken(victim);
        registerAndGetToken(bystander);

        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(login(victim, "Wrong" + attempt));
        }

        mockMvc.perform(login(bystander, "Secret123!"))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password));
    }
}
