package com.spendly.config;

import com.spendly.domain.Category;
import com.spendly.domain.Expense;
import com.spendly.domain.Role;
import com.spendly.domain.User;
import com.spendly.repository.CategoryRepository;
import com.spendly.repository.ExpenseRepository;
import com.spendly.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seedDemoData(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            ExpenseRepository expenseRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (userRepository.existsByEmailIgnoreCase("admin@spendly.app")) {
                return;
            }

            User admin = new User();
            admin.setEmail("admin@spendly.app");
            admin.setPasswordHash(passwordEncoder.encode("Admin123!"));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);

            User demo = new User();
            demo.setEmail("demo@spendly.app");
            demo.setPasswordHash(passwordEncoder.encode("Demo123!"));
            demo.setRole(Role.USER);
            userRepository.save(demo);

            List<Category> categories = List.of(
                    category(demo, "Food", "#E76F51"),
                    category(demo, "Transport", "#2A9D8F"),
                    category(demo, "Rent", "#264653"),
                    category(demo, "Leisure", "#E9C46A"),
                    category(demo, "Other", "#6C757D")
            );
            categoryRepository.saveAll(categories);

            LocalDate today = LocalDate.now();
            expenseRepository.save(expense(demo, categories.get(0), "12.50", today.minusDays(1), "Lunch"));
            expenseRepository.save(expense(demo, categories.get(1), "3.20", today.minusDays(2), "U-Bahn ticket"));
            expenseRepository.save(expense(demo, categories.get(2), "850.00", today.withDayOfMonth(1), "Monthly rent"));
            expenseRepository.save(expense(demo, categories.get(3), "45.00", today.minusDays(5), "Cinema"));

            log.info("Seeded demo users: admin@spendly.app / Admin123!, demo@spendly.app / Demo123!");
        };
    }

    private static Category category(User user, String name, String color) {
        Category c = new Category();
        c.setUser(user);
        c.setName(name);
        c.setColor(color);
        return c;
    }

    private static Expense expense(User user, Category category, String amount, LocalDate spentOn, String description) {
        Expense e = new Expense();
        e.setUser(user);
        e.setCategory(category);
        e.setAmount(new BigDecimal(amount));
        e.setCurrency("EUR");
        e.setSpentOn(spentOn);
        e.setDescription(description);
        return e;
    }
}
