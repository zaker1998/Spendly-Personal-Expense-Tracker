package com.spendly.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.spendly.domain.Budget;
import com.spendly.domain.Role;
import com.spendly.domain.User;
import com.spendly.dto.BudgetDtos.BudgetRequest;
import com.spendly.repository.BudgetRepository;
import com.spendly.repository.ExpenseRepository;
import com.spendly.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    private BudgetService budgetService;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(budgetRepository, userRepository, expenseRepository, null);
    }

    @Test
    void createOverallBudgetComputesProgress() {
        LocalDate today = LocalDate.now();
        User user = new User();
        user.setId(1L);
        user.setEmail("demo@spendly.app");
        user.setRole(Role.USER);

        when(budgetRepository.existsForPeriod(1L, today.getYear(), today.getMonthValue(), null, null))
                .thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> {
            Budget b = inv.getArgument(0);
            b.setId(5L);
            return b;
        });
        when(expenseRepository.totalForMonth(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("250.00"));

        var response = budgetService.create(
                1L,
                new BudgetRequest(null, new BigDecimal("1000.00"), today.getYear(), today.getMonthValue())
        );

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.categoryName()).isEqualTo("Overall");
        assertThat(response.spentAmount()).isEqualByComparingTo("250.00");
        assertThat(response.percentUsed()).isEqualTo(25);
        assertThat(response.overBudget()).isFalse();
    }
}
