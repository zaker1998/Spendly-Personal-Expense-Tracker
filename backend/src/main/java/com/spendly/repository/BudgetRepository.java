package com.spendly.repository;

import com.spendly.domain.Budget;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    @EntityGraph(attributePaths = {"category"})
    List<Budget> findByUserIdAndYearAndMonthOrderByIdAsc(Long userId, int year, int month);

    @EntityGraph(attributePaths = {"category"})
    Optional<Budget> findByIdAndUserId(Long id, Long userId);

    @Query("""
            SELECT COUNT(b) > 0 FROM Budget b
            WHERE b.user.id = :userId
              AND b.year = :year
              AND b.month = :month
              AND ((:categoryId IS NULL AND b.category IS NULL)
                   OR (:categoryId IS NOT NULL AND b.category.id = :categoryId))
              AND (:excludeId IS NULL OR b.id <> :excludeId)
            """)
    boolean existsForPeriod(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month,
            @Param("categoryId") Long categoryId,
            @Param("excludeId") Long excludeId
    );

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
    @Query("DELETE FROM Budget b WHERE b.user.id = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
