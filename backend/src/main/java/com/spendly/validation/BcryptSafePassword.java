package com.spendly.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * Rejects passwords that BCrypt cannot hash in full.
 *
 * <p>BCrypt reads the first 72 bytes of its input and silently discards the
 * rest. Two passwords sharing a 72-byte prefix are therefore the same password
 * as far as authentication is concerned, and the user gets no indication that
 * the tail of what they typed was ignored.
 *
 * <p>{@code @Size} cannot express this: it counts characters, and a single
 * UTF-8 character can be four bytes. A 30-character passphrase in Cyrillic or
 * with emoji already exceeds the limit.
 */
@Documented
@Constraint(validatedBy = BcryptSafePassword.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface BcryptSafePassword {

    int MAX_BYTES = 72;

    String message() default "must be at most 72 bytes long once UTF-8 encoded";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<BcryptSafePassword, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            // Null and blank belong to @NotBlank; reporting them twice just
            // produces two messages for one mistake.
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
        }
    }
}
