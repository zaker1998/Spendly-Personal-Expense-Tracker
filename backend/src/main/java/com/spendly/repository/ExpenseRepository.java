package com.spendly.repository;

import com.spendly.domain.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @EntityGraph(attributePaths = {"category"})
    @Query("""
            SELECT e FROM Expense e
            WHERE e.id = :id AND e.user.id = :userId
            """)
    Optional<Expense> findByIdAndUserIdWithCategory(@Param("id") Long id, @Param("userId") Long userId);

    @EntityGraph(attributePaths = {"category"})
    @Query("""
            SELECT e FROM Expense e
            WHERE e.user.id = :userId
              AND (:hasCategory = false OR e.category.id = :categoryId)
              AND (:hasFrom = false OR e.spentOn >= :fromDate)
              AND (:hasTo = false OR e.spentOn <= :toDate)
              AND (:hasMin = false OR e.amount >= :minAmount)
              AND (:hasMax = false OR e.amount <= :maxAmount)
              AND (:hasSearch = false OR LOWER(COALESCE(e.description, '')) LIKE :searchPattern ESCAPE '\\')
            """)
    Page<Expense> findFiltered(
            @Param("userId") Long userId,
            @Param("hasCategory") boolean hasCategory,
            @Param("categoryId") Long categoryId,
            @Param("hasFrom") boolean hasFrom,
            @Param("fromDate") LocalDate fromDate,
            @Param("hasTo") boolean hasTo,
            @Param("toDate") LocalDate toDate,
            @Param("hasMin") boolean hasMin,
            @Param("minAmount") BigDecimal minAmount,
            @Param("hasMax") boolean hasMax,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("hasSearch") boolean hasSearch,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"category", "user"})
    @Query("""
            SELECT e FROM Expense e
            WHERE (:hasCategory = false OR e.category.id = :categoryId)
              AND (:hasFrom = false OR e.spentOn >= :fromDate)
              AND (:hasTo = false OR e.spentOn <= :toDate)
            """)
    Page<Expense> findAllForAdmin(
            @Param("hasCategory") boolean hasCategory,
            @Param("categoryId") Long categoryId,
            @Param("hasFrom") boolean hasFrom,
            @Param("fromDate") LocalDate fromDate,
            @Param("hasTo") boolean hasTo,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    @Query("""
            SELECT e.category.id, e.category.name, COALESCE(SUM(e.amount), 0)
            FROM Expense e
            WHERE e.user.id = :userId
              AND e.spentOn >= :fromDate
              AND e.spentOn <= :toDate
            GROUP BY e.category.id, e.category.name
            ORDER BY SUM(e.amount) DESC
            """)
    List<Object[]> sumByCategoryForMonth(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
            WHERE e.user.id = :userId
              AND e.spentOn >= :fromDate
              AND e.spentOn <= :toDate
            """)
    BigDecimal totalForMonth(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
            WHERE e.user.id = :userId
              AND e.category.id = :categoryId
              AND e.spentOn >= :fromDate
              AND e.spentOn <= :toDate
            """)
    BigDecimal totalForCategoryMonth(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
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
    @Query("DELETE FROM Expense e WHERE e.user.id = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
