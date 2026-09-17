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
import java.time.LocalDate;

/**
 * Keeps a user-supplied date inside the range the application actually reports on.
 *
 * <p>{@code spentOn} had no bound at all, so an expense could be filed against
 * the year 9999. Nothing rejected it, it never appeared in any monthly view, and
 * it quietly widened every "all time" figure. Bean Validation has no built-in
 * constraint for a literal range, hence this one; the same bounds are repeated
 * as a CHECK constraint in V4 for anything that does not come through the API.
 *
 * <p>Deliberately not {@code @PastOrPresent}: filing a bill that is already
 * dated next week is a legitimate thing to do.
 */
@Documented
@Constraint(validatedBy = SupportedDate.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SupportedDate {

    int MIN_YEAR = 2000;
    int MAX_YEAR = 2100;

    String message() default "must be between 2000 and 2100";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<SupportedDate, LocalDate> {

        @Override
        public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
            // Null belongs to @NotNull; reporting it here too gives one mistake
            // two messages.
            return value == null || (value.getYear() >= MIN_YEAR && value.getYear() <= MAX_YEAR);
        }
    }
}
