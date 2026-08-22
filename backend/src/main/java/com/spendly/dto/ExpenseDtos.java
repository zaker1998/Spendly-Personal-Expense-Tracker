package com.spendly.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    /** No currency field: see {@link com.spendly.domain.AppCurrency}. */
    public record ExpenseRequest(
            @NotNull Long categoryId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotNull LocalDate spentOn,
            @Size(max = 500) String description
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
