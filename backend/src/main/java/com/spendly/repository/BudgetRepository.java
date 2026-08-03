package com.spendly.repository;

import com.spendly.domain.Budget;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
