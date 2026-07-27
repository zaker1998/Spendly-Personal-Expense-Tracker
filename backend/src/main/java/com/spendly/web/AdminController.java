package com.spendly.web;

import com.spendly.dto.ExpenseDtos.AdminExpenseResponse;
import com.spendly.dto.UserDtos.UserResponse;
import com.spendly.service.AdminService;
import com.spendly.service.ExpenseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin")
public class AdminController {

    private final AdminService adminService;
    private final ExpenseService expenseService;

    public AdminController(AdminService adminService, ExpenseService expenseService) {
        this.adminService = adminService;
        this.expenseService = expenseService;
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        return adminService.listUsers();
    }

    @GetMapping("/expenses")
    public List<AdminExpenseResponse> expenses(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return expenseService.listAllForAdmin(categoryId, from, to);
    }
}
