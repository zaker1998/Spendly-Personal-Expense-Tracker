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
              AND (:categoryId IS NULL OR e.category.id = :categoryId)
              AND (:fromDate IS NULL OR e.spentOn >= :fromDate)
              AND (:toDate IS NULL OR e.spentOn <= :toDate)
              AND (:minAmount IS NULL OR e.amount >= :minAmount)
              AND (:maxAmount IS NULL OR e.amount <= :maxAmount)
              AND (:hasSearch = false OR LOWER(COALESCE(e.description, '')) LIKE :searchPattern)
            """)
    Page<Expense> findFiltered(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("hasSearch") boolean hasSearch,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"category", "user"})
    @Query("""
            SELECT e FROM Expense e
            WHERE (:categoryId IS NULL OR e.category.id = :categoryId)
              AND (:fromDate IS NULL OR e.spentOn >= :fromDate)
              AND (:toDate IS NULL OR e.spentOn <= :toDate)
            ORDER BY e.spentOn DESC
            """)
    List<Expense> findAllForAdmin(
            @Param("categoryId") Long categoryId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
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
}
