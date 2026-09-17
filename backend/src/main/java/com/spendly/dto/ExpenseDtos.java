package com.spendly.dto;

import com.spendly.validation.SupportedDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    /**
     * No currency field: see {@link com.spendly.domain.AppCurrency}.
     *
     * <p>{@code version} is the value from the last read of this expense. Send it
     * to have a concurrent edit rejected with 409 instead of silently
     * overwritten; omit it to skip the check.
     *
     * <p>The bounds on {@code amount} are what the column can hold:
     * {@code NUMERIC(19,2)}. Without them an oversized figure reached the
     * database and came back as "Conflicts with existing data" (409), and a third
     * decimal place was rounded away without anyone being told.
     */
    public record ExpenseRequest(
            @NotNull Long categoryId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @NotNull @SupportedDate LocalDate spentOn,
            @Size(max = 500) String description,
            Long version
    ) {
    }

    public record ExpenseResponse(
            Long id,
            Long categoryId,
            String categoryName,
            String categoryColor,
            BigDecimal amount,
            String currency,
            LocalDate spentOn,
            String description,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record SuggestCategoryRequest(
            @NotBlank @Size(max = 500) String description
    ) {
    }

    /**
     * source is "AI" when the suggestion came from the LLM, "HEURISTIC" for the
     * keyword fallback, and "NONE" when no confident match was found
     * (categoryId/categoryName are null in that case).
     */
    public record SuggestCategoryResponse(
            Long categoryId,
            String categoryName,
            String source
    ) {
    }

    public record AdminExpenseResponse(
            Long id,
            Long userId,
            String userEmail,
            Long categoryId,
            String categoryName,
            BigDecimal amount,
            String currency,
            LocalDate spentOn,
            String description,
            Instant createdAt
    ) {
    }
}
