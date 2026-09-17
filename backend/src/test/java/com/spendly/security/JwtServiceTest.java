package com.spendly.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendly.domain.Role;
import com.spendly.domain.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes!!";

    private JwtService jwtService;

    private static UserPrincipal principal() {
        User user = new User();
        user.setId(42L);
        user.setEmail("demo@spendly.app");
        user.setPasswordHash("hash");
        user.setRole(Role.USER);
        return new UserPrincipal(user);
    }

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3_600_000L, "spendly");
    }

    @Test
    void generatesAndValidatesToken() {
        String token = jwtService.generateToken(principal());

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("demo@spendly.app");
        assertThat(jwtService.parse(token).get("uid", Integer.class)).isEqualTo(42);
        assertThat(jwtService.parse(token).get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void rejectsExpiredToken() {
        JwtService expiring = new JwtService(SECRET, -1_000L, "spendly");
        String token = expiring.generateToken(principal());

        assertThatThrownBy(() -> expiring.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    /**
     * A token signed with a different key must not be accepted just because it
     * is well-formed.
     */
    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String foreign = new JwtService("another-secret-key-at-least-32-bytes!!!!", 3_600_000L, "spendly")
                .generateToken(principal());

        assertThatThrownBy(() -> jwtService.parse(foreign))
                .isInstanceOf(SignatureException.class);
    }

    /**
     * The issuer claim is what keeps tokens from a neighbouring service that
     * happens to share the secret from working here.
     */
    @Test
    void rejectsTokenFromAnotherIssuer() {
        String foreign = new JwtService(SECRET, 3_600_000L, "somebody-else").generateToken(principal());

        assertThatThrownBy(() -> jwtService.parse(foreign))
                .isInstanceOf(IncorrectClaimException.class);
    }

    /**
     * A short secret used to be zero-padded up to 32 bytes, so a weak secret
     * quietly produced a weak signing key. Startup must fail instead.
     */
    @Test
    void refusesSecretShorterThanTheHashOutput() {
        assertThatThrownBy(() -> new JwtService("too-short", 3_600_000L, "spendly"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
