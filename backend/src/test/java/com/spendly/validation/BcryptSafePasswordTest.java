package com.spendly.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendly.dto.AuthDtos.RegisterRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BcryptSafePasswordTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static boolean accepts(String password) {
        return validator.validate(new RegisterRequest("user@spendly.app", password)).isEmpty();
    }

    @Test
    void acceptsAnOrdinaryPassword() {
        assertThat(accepts("Secret123!")).isTrue();
    }

    @Test
    void acceptsExactlySeventyTwoBytes() {
        assertThat(accepts("a".repeat(72))).isTrue();
    }

    @Test
    void rejectsSeventyThreeBytes() {
        assertThat(accepts("a".repeat(73))).isFalse();
    }

    /**
     * The reason {@code @Size} cannot do this job. This passphrase is 42
     * characters but 84 bytes, so BCrypt would read the first 36 characters and
     * silently ignore the rest — and a character-counting constraint would wave
     * it through.
     */
    @Test
    void countsBytesRatherThanCharacters() {
        String cyrillic = "пароль".repeat(7);

        assertThat(cyrillic).hasSize(42);
        assertThat(accepts(cyrillic)).isFalse();
    }
}
