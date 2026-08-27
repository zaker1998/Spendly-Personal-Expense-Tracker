package com.spendly.repository;

import com.spendly.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUserIdOrderByNameAsc(Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    @Query("SELECT COUNT(e) FROM Expense e WHERE e.category.id = :categoryId")
    long countExpensesByCategoryId(@Param("categoryId") Long categoryId);

    @Query("SELECT COUNT(b) FROM Budget b WHERE b.category.id = :categoryId")
    long countBudgetsByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Used only by the demo seeder, which rebuilds the demo account on boot.
     *
     * <p>A bulk JPQL delete rather than the derived {@code deleteByUserId}:
     * the derived form queues entity removals in the persistence context, and
     * Hibernate always flushes inserts before deletes regardless of call order,
     * so re-seeding collided with the rows it was about to replace. DML runs
     * immediately, which also makes it one statement instead of one per row.
     */
    @Modifying
    @Query("DELETE FROM Category c WHERE c.user.id = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
