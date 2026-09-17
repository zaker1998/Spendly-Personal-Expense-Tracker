package com.spendly.service;

import com.spendly.domain.AppCurrency;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final CategoryService categoryService;
    private final ApplicationEventPublisher events;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            UserRepository userRepository,
            CategoryService categoryService,
            ApplicationEventPublisher events
    ) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.categoryService = categoryService;
        this.events = events;
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
        ExpenseFilter f = ExpenseFilter.of(categoryId, fromDate, toDate, minAmount, maxAmount, search);
        return expenseRepository.findFiltered(
                        userId,
                        f.hasCategory(), f.categoryId(),
                        f.hasFrom(), f.fromDate(),
                        f.hasTo(), f.toDate(),
                        f.hasMin(), f.minAmount(),
                        f.hasMax(), f.maxAmount(),
                        f.hasSearch(), f.searchPattern(),
                        pageable)
                .map(this::toResponse);
    }

    /**
     * One keyset chunk of the same filter, ordered by id, for the CSV export.
     *
     * <p>Its own short read-only transaction per chunk: the export is paced by
     * the client's download speed, and one transaction spanning the whole write
     * would hold a pooled connection for exactly that long.
     */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> listAfterId(
            Long userId,
            long afterId,
            int limit,
            Long categoryId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String search
    ) {
        ExpenseFilter f = ExpenseFilter.of(categoryId, fromDate, toDate, minAmount, maxAmount, search);
        return expenseRepository.findFilteredAfterId(
                        userId, afterId,
                        f.hasCategory(), f.categoryId(),
                        f.hasFrom(), f.fromDate(),
                        f.hasTo(), f.toDate(),
                        f.hasMin(), f.minAmount(),
                        f.hasMax(), f.maxAmount(),
                        f.hasSearch(), f.searchPattern(),
                        Limit.of(limit))
                .stream()
                .map(this::toResponse)
                .toList();
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
        events.publishEvent(new SummaryChangedEvent(userId, request.spentOn()));
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse update(Long userId, Long expenseId, ExpenseRequest request) {
        Expense expense = expenseRepository.findByIdAndUserIdWithCategory(expenseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
        requireCurrentVersion(expense.getVersion(), request.version());
        Category category = categoryService.getOwnedOrThrow(userId, request.categoryId());
        // The expense may move between months, so both the old and new summary
        // entries must be invalidated.
        events.publishEvent(new SummaryChangedEvent(userId, expense.getSpentOn()));
        events.publishEvent(new SummaryChangedEvent(userId, request.spentOn()));
        expense.setCategory(category);
        applyRequest(expense, request);
        // Hibernate increments @Version on flush, which otherwise happens after
        // this method has already built the response — so the client was handed
        // back the version it sent, and its next save failed as "stale".
        expenseRepository.flush();
        return toResponse(expense);
    }

    @Transactional
    public void delete(Long userId, Long expenseId) {
        Expense expense = expenseRepository.findByIdAndUserIdWithCategory(expenseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
        events.publishEvent(new SummaryChangedEvent(userId, expense.getSpentOn()));
        expenseRepository.delete(expense);
    }

    @Transactional(readOnly = true)
    public Page<AdminExpenseResponse> listAllForAdmin(
            Long categoryId,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        boolean hasCategory = categoryId != null;
        boolean hasFrom = fromDate != null;
        boolean hasTo = toDate != null;
        return expenseRepository.findAllForAdmin(
                        hasCategory,
                        hasCategory ? categoryId : 0L,
                        hasFrom,
                        hasFrom ? fromDate : LocalDate.EPOCH,
                        hasTo,
                        hasTo ? toDate : LocalDate.EPOCH,
                        pageable)
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
                ));
    }

    /**
     * Rejects a write built from a copy of the row the client has since stopped
     * holding.
     *
     * <p>Hibernate's own {@code @Version} check only covers the window inside
     * one transaction. Two tabs that each load, then each save, are two separate
     * transactions and both would succeed — the second silently discarding the
     * first. Comparing the version the client sent closes that gap. The field is
     * optional so an older client, or a script, still works; it just gives up
     * the protection.
     */
    static void requireCurrentVersion(Long actual, Long submitted) {
        if (submitted != null && !submitted.equals(actual)) {
            throw new OptimisticLockingFailureException(
                    "Stale version: client had " + submitted + ", current is " + actual);
        }
    }

    private void applyRequest(Expense expense, ExpenseRequest request) {
        expense.setAmount(request.amount());
        expense.setCurrency(AppCurrency.CODE);
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
                expense.getVersion(),
                expense.getCreatedAt(),
                expense.getUpdatedAt()
        );
    }
}
