package com.spendly.service;

import com.spendly.domain.Budget;
import com.spendly.domain.Category;
import com.spendly.domain.User;
import com.spendly.dto.BudgetDtos.BudgetRequest;
import com.spendly.dto.BudgetDtos.BudgetResponse;
import com.spendly.exception.ConflictException;
import com.spendly.exception.ResourceNotFoundException;
import com.spendly.repository.BudgetRepository;
import com.spendly.repository.ExpenseRepository;
import com.spendly.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final CategoryService categoryService;

    public BudgetService(
            BudgetRepository budgetRepository,
            UserRepository userRepository,
            ExpenseRepository expenseRepository,
            CategoryService categoryService
    ) {
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(Long userId, int year, int month) {
        return budgetRepository.findByUserIdAndYearAndMonthOrderByIdAsc(userId, year, month).stream()
                .map(b -> toResponse(userId, b))
                .toList();
    }

    @Transactional
    public BudgetResponse create(Long userId, BudgetRequest request) {
        Long categoryId = request.categoryId();
        if (budgetRepository.existsForPeriod(userId, request.year(), request.month(), categoryId, null)) {
            throw new ConflictException("Budget already exists for this period");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Budget budget = new Budget();
        budget.setUser(user);
        budget.setAmount(request.amount());
        budget.setYear(request.year());
        budget.setMonth(request.month());
        if (categoryId != null) {
            Category category = categoryService.getOwnedOrThrow(userId, categoryId);
            budget.setCategory(category);
        }
        return toResponse(userId, budgetRepository.save(budget));
    }

    @Transactional
    public BudgetResponse update(Long userId, Long budgetId, BudgetRequest request) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found"));

        Long categoryId = request.categoryId();
        if (budgetRepository.existsForPeriod(userId, request.year(), request.month(), categoryId, budgetId)) {
            throw new ConflictException("Budget already exists for this period");
        }

        budget.setAmount(request.amount());
        budget.setYear(request.year());
        budget.setMonth(request.month());
        if (categoryId != null) {
            budget.setCategory(categoryService.getOwnedOrThrow(userId, categoryId));
        } else {
            budget.setCategory(null);
        }
        return toResponse(userId, budget);
    }

    @Transactional
    public void delete(Long userId, Long budgetId) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found"));
        budgetRepository.delete(budget);
    }

    private BudgetResponse toResponse(Long userId, Budget budget) {
        YearMonth ym = YearMonth.of(budget.getYear(), budget.getMonth());
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        BigDecimal spent;
        String categoryName;
        Long categoryId = null;
        if (budget.getCategory() != null) {
            categoryId = budget.getCategory().getId();
            categoryName = budget.getCategory().getName();
            spent = expenseRepository.totalForCategoryMonth(userId, categoryId, from, to);
        } else {
            categoryName = "Overall";
            spent = expenseRepository.totalForMonth(userId, from, to);
        }
        if (spent == null) {
            spent = BigDecimal.ZERO;
        }

        BigDecimal limit = budget.getAmount();
        BigDecimal remaining = limit.subtract(spent);
        int percent = limit.compareTo(BigDecimal.ZERO) == 0
                ? 0
                : spent.multiply(BigDecimal.valueOf(100))
                        .divide(limit, 0, RoundingMode.HALF_UP)
                        .intValue();
        boolean over = spent.compareTo(limit) > 0;

        return new BudgetResponse(
                budget.getId(),
                categoryId,
                categoryName,
                limit,
                spent,
                remaining,
                Math.min(percent, 999),
                over,
                budget.getYear(),
                budget.getMonth(),
                "EUR"
        );
    }
}
