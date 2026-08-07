package com.spendly.service;

import com.spendly.config.CacheConfig;
import com.spendly.dto.SummaryDtos.CategoryTotal;
import com.spendly.dto.SummaryDtos.MonthlySummaryResponse;
import com.spendly.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SummaryService {

    private final ExpenseRepository expenseRepository;

    public SummaryService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.MONTHLY_SUMMARY_CACHE, key = "#userId + ':' + #year + ':' + #month")
    public MonthlySummaryResponse monthly(Long userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        BigDecimal total = expenseRepository.totalForMonth(userId, from, to);
        List<CategoryTotal> byCategory = expenseRepository.sumByCategoryForMonth(userId, from, to).stream()
                .map(row -> new CategoryTotal(
                        (Long) row[0],
                        (String) row[1],
                        (BigDecimal) row[2]
                ))
                .toList();

        return new MonthlySummaryResponse(year, month, total, "EUR", byCategory);
    }
}
