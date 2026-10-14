package com.spendly.repository;

import java.math.BigDecimal;

/**
 * One row of the per-category monthly totals.
 *
 * <p>A record rather than {@code Object[]}: two services read this query, and
 * with positional columns adding one (the colour) silently shifted the index
 * the other one was reading the sum from.
 */
public record CategorySpend(Long categoryId, String categoryName, String categoryColor, BigDecimal total) {
}
