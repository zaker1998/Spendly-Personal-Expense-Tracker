package com.spendly.web;

import com.spendly.dto.ExpenseDtos.AdminExpenseResponse;
import com.spendly.dto.UserDtos.UserResponse;
import com.spendly.service.AdminService;
import com.spendly.service.ExpenseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin")
public class AdminController {

    private static final Set<String> USER_SORTABLE = Set.of("createdAt", "email", "role", "id");
    private static final Set<String> EXPENSE_SORTABLE = Set.of("spentOn", "amount", "createdAt", "id");

    private final AdminService adminService;
    private final ExpenseService expenseService;

    public AdminController(AdminService adminService, ExpenseService expenseService) {
        this.adminService = adminService;
        this.expenseService = expenseService;
    }

    @GetMapping("/users")
    public Page<UserResponse> users(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return adminService.listUsers(Sorting.validate(pageable, USER_SORTABLE));
    }

    @GetMapping("/expenses")
    public Page<AdminExpenseResponse> expenses(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "spentOn", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return expenseService.listAllForAdmin(categoryId, from, to,
                Sorting.validate(pageable, EXPENSE_SORTABLE));
    }
}
