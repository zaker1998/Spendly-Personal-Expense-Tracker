package com.spendly.repository;

import com.spendly.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUserIdOrderByNameAsc(Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    @Query("SELECT COUNT(e) FROM Expense e WHERE e.category.id = :categoryId")
    long countExpensesByCategoryId(@Param("categoryId") Long categoryId);

    @Query("SELECT COUNT(b) FROM Budget b WHERE b.category.id = :categoryId")
    long countBudgetsByCategoryId(@Param("categoryId") Long categoryId);
}
