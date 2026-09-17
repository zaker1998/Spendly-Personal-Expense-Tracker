package com.spendly.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The optional expense filters, resolved once into the flag-plus-value pairs the
 * repository queries take.
 *
 * <p>JPQL has no way to say "ignore this predicate", so each optional filter is
 * expressed as a boolean flag and a value that is only read when the flag is
 * true. Building that by hand at every call site is where a mismatched pair
 * would hide, and the export needs exactly the same translation as the list
 * endpoint.
 */
public record ExpenseFilter(
        boolean hasCategory,
        Long categoryId,
        boolean hasFrom,
        LocalDate fromDate,
        boolean hasTo,
        LocalDate toDate,
        boolean hasMin,
        BigDecimal minAmount,
        boolean hasMax,
        BigDecimal maxAmount,
        boolean hasSearch,
        String searchPattern
) {

    public static ExpenseFilter of(
            Long categoryId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String search
    ) {
        boolean hasSearch = search != null && !search.isBlank();
        return new ExpenseFilter(
                categoryId != null, categoryId != null ? categoryId : 0L,
                fromDate != null, fromDate != null ? fromDate : LocalDate.EPOCH,
                toDate != null, toDate != null ? toDate : LocalDate.EPOCH,
                minAmount != null, minAmount != null ? minAmount : BigDecimal.ZERO,
                maxAmount != null, maxAmount != null ? maxAmount : BigDecimal.ZERO,
                hasSearch, hasSearch ? "%" + escapeLike(search.trim().toLowerCase()) + "%" : "%"
        );
    }

    /** Escapes LIKE wildcards so searching for "100%" doesn't match everything. */
    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
