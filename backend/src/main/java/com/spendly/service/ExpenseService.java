package com.spendly.service;

import com.spendly.domain.Category;
import com.spendly.domain.Expense;
import com.spendly.domain.User;
import com.spendly.dto.ExpenseDtos.AdminExpenseResponse;
import com.spendly.dto.ExpenseDtos.ExpenseRequest;
import com.spendly.dto.ExpenseDtos.ExpenseResponse;
import com.spendly.exception.ResourceNotFoundException;
import com.spendly.repository.ExpenseRepository;
import com.spendly.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final CategoryService categoryService;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            UserRepository userRepository,
            CategoryService categoryService
    ) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> list(
            Long userId,
            Long categoryId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String search,
            Pageable pageable
    ) {
        boolean hasCategory = categoryId != null;
        boolean hasFrom = fromDate != null;
        boolean hasTo = toDate != null;
        boolean hasMin = minAmount != null;
        boolean hasMax = maxAmount != null;
        boolean hasSearch = search != null && !search.isBlank();
        String searchPattern = hasSearch ? "%" + search.trim().toLowerCase() + "%" : "%";
        return expenseRepository.findFiltered(
                        userId,
                        hasCategory,
                        hasCategory ? categoryId : 0L,
                        hasFrom,
                        hasFrom ? fromDate : LocalDate.EPOCH,
                        hasTo,
                        hasTo ? toDate : LocalDate.EPOCH,
                        hasMin,
                        hasMin ? minAmount : BigDecimal.ZERO,
                        hasMax,
                        hasMax ? maxAmount : BigDecimal.ZERO,
                        hasSearch,
                        searchPattern,
                        pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(Long userId, Long expenseId) {
        return toResponse(expenseRepository.findByIdAndUserIdWithCategory(expenseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found")));
    }

    @Transactional
    public ExpenseResponse create(Long userId, ExpenseRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Category category = categoryService.getOwnedOrThrow(userId, request.categoryId());

        Expense expense = new Expense();
        expense.setUser(user);
        expense.setCategory(category);
        applyRequest(expense, request);
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse update(Long userId, Long expenseId, ExpenseRequest request) {
        Expense expense = expenseRepository.findByIdAndUserIdWithCategory(expenseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
        Category category = categoryService.getOwnedOrThrow(userId, request.categoryId());
        expense.setCategory(category);
        applyRequest(expense, request);
        return toResponse(expense);
    }

    @Transactional
    public void delete(Long userId, Long expenseId) {
        Expense expense = expenseRepository.findByIdAndUserIdWithCategory(expenseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
        expenseRepository.delete(expense);
    }

    @Transactional(readOnly = true)
    public List<AdminExpenseResponse> listAllForAdmin(Long categoryId, LocalDate fromDate, LocalDate toDate) {
        boolean hasCategory = categoryId != null;
        boolean hasFrom = fromDate != null;
        boolean hasTo = toDate != null;
        return expenseRepository.findAllForAdmin(
                        hasCategory,
                        hasCategory ? categoryId : 0L,
                        hasFrom,
                        hasFrom ? fromDate : LocalDate.EPOCH,
                        hasTo,
                        hasTo ? toDate : LocalDate.EPOCH)
                .stream()
                .map(e -> new AdminExpenseResponse(
                        e.getId(),
                        e.getUser().getId(),
                        e.getUser().getEmail(),
                        e.getCategory().getId(),
                        e.getCategory().getName(),
                        e.getAmount(),
                        e.getCurrency(),
                        e.getSpentOn(),
                        e.getDescription(),
                        e.getCreatedAt()
                ))
                .toList();
    }

    private void applyRequest(Expense expense, ExpenseRequest request) {
        expense.setAmount(request.amount());
        expense.setCurrency(request.currency() == null || request.currency().isBlank()
                ? "EUR"
                : request.currency().toUpperCase());
        expense.setSpentOn(request.spentOn());
        expense.setDescription(request.description());
    }

    private ExpenseResponse toResponse(Expense expense) {
        Category category = expense.getCategory();
        return new ExpenseResponse(
                expense.getId(),
                category.getId(),
                category.getName(),
                category.getColor(),
                expense.getAmount(),
                expense.getCurrency(),
                expense.getSpentOn(),
                expense.getDescription(),
                expense.getCreatedAt(),
                expense.getUpdatedAt()
        );
    }
}
