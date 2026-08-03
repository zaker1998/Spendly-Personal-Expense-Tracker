package com.spendly.service;

import com.spendly.dto.ExpenseDtos.ExpenseResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseExportService {

    private final ExpenseService expenseService;

    public ExpenseExportService(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @Transactional(readOnly = true)
    public String toCsv(
            Long userId,
            Long categoryId,
            LocalDate from,
            LocalDate to,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String search
    ) {
        List<ExpenseResponse> expenses = expenseService.list(
                        userId, categoryId, from, to, minAmount, maxAmount, search, Pageable.unpaged())
                .getContent();

        StringBuilder sb = new StringBuilder();
        sb.append("id,spentOn,category,amount,currency,description\n");
        for (ExpenseResponse e : expenses) {
            sb.append(e.id()).append(',')
                    .append(e.spentOn()).append(',')
                    .append(escape(e.categoryName())).append(',')
                    .append(e.amount()).append(',')
                    .append(e.currency()).append(',')
                    .append(escape(e.description()))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
