package com.kunal.finance.backend.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kunal.finance.backend.entity.FinancialRecord;
import com.kunal.finance.backend.entity.RecordType;

public interface FinancialRecordRepository
        extends JpaRepository<FinancialRecord, Long>, JpaSpecificationExecutor<FinancialRecord> {

    /** Overridden only to fetch the owner in the same query instead of one query per row. */
    @Override
    @EntityGraph(attributePaths = "createdBy")
    Page<FinancialRecord> findAll(Specification<FinancialRecord> spec, Pageable pageable);

    @EntityGraph(attributePaths = "createdBy")
    List<FinancialRecord> findByDeletedFalseAndRecordDateBetween(LocalDate from, LocalDate to);

    List<FinancialRecord> findByIdGreaterThanOrderByIdAsc(long id, Pageable pageable);

    boolean existsByCreatedById(Long userId);

    interface TypeTotal {
        RecordType getType();

        BigDecimal getTotal();

        long getCnt();
    }

    interface CategoryTotal {
        String getCategory();

        RecordType getType();

        BigDecimal getTotal();
    }

    @Query("""
            select r.type as type, sum(r.amount) as total, count(r) as cnt
            from FinancialRecord r
            where r.deleted = false and r.recordDate between :from and :to
            group by r.type""")
    List<TypeTotal> totalsByType(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select r.category as category, r.type as type, sum(r.amount) as total
            from FinancialRecord r
            where r.deleted = false and r.recordDate between :from and :to
            group by r.category, r.type
            order by r.category""")
    List<CategoryTotal> totalsByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select year(r.recordDate), month(r.recordDate), r.type, sum(r.amount)
            from FinancialRecord r
            where r.deleted = false and r.recordDate between :from and :to
            group by year(r.recordDate), month(r.recordDate), r.type""")
    List<Object[]> monthlyTotals(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
