package com.spendly.dto;

import java.math.BigDecimal;
import java.util.List;

public final class SummaryDtos {

    private SummaryDtos() {
    }

    public record CategoryTotal(
            Long categoryId,
            String categoryName,
            BigDecimal total
    ) {
    }

    public record MonthlySummaryResponse(
            int year,
            int month,
            BigDecimal totalAmount,
            String currency,
            List<CategoryTotal> byCategory
    ) {
    }
}
