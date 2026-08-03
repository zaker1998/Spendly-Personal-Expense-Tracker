package com.spendly.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class BudgetDtos {

    private BudgetDtos() {
    }

    public record BudgetRequest(
            Long categoryId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull @Min(2000) @Max(2100) Integer year,
            @NotNull @Min(1) @Max(12) Integer month
    ) {
    }

    public record BudgetResponse(
            Long id,
            Long categoryId,
            String categoryName,
            BigDecimal limitAmount,
            BigDecimal spentAmount,
            BigDecimal remainingAmount,
            int percentUsed,
            boolean overBudget,
            int year,
            int month,
            String currency
    ) {
    }
}
