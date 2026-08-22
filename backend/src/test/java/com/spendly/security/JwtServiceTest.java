package com.spendly.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendly.domain.Role;
import com.spendly.domain.User;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("unit-test-secret-key-at-least-32-bytes!!", 3_600_000L);
    }

    @Test
    void generatesAndValidatesToken() {
        User user = new User();
        user.setId(42L);
        user.setEmail("demo@spendly.app");
        user.setPasswordHash("hash");
        user.setRole(Role.USER);
        UserPrincipal principal = new UserPrincipal(user);

        String token = jwtService.generateToken(principal);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("demo@spendly.app");
        assertThat(jwtService.isTokenValid(token, principal)).isTrue();
    }

    @Test
    void rejectsExpiredToken() {
        User user = new User();
        user.setId(42L);
        user.setEmail("demo@spendly.app");
        user.setPasswordHash("hash");
        user.setRole(Role.USER);
        UserPrincipal principal = new UserPrincipal(user);

        JwtService expiring = new JwtService("unit-test-secret-key-at-least-32-bytes!!", -1_000L);
        String token = expiring.generateToken(principal);

        assertThatThrownBy(() -> expiring.isTokenValid(token, principal))
                .isInstanceOf(ExpiredJwtException.class);
    }

    /**
     * A short secret used to be zero-padded up to 32 bytes, so a weak secret
     * quietly produced a weak signing key. Startup must fail instead.
     */
    @Test
    void refusesSecretShorterThanTheHashOutput() {
        assertThatThrownBy(() -> new JwtService("too-short", 3_600_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
