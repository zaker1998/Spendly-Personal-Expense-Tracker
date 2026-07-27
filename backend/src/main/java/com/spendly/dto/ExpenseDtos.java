package com.spendly.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    public record ExpenseRequest(
            @NotNull Long categoryId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @Size(min = 3, max = 3) String currency,
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
