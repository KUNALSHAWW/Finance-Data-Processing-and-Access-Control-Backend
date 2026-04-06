package com.kunal.finance.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.kunal.finance.backend.entity.FinancialRecord;
import com.kunal.finance.backend.entity.RecordType;
public interface FinancialRecordRepository extends JpaRepository<FinancialRecord, Long> {

    Page<FinancialRecord> findByDeletedFalse(Pageable pageable);

    Page<FinancialRecord> findByTypeAndDeletedFalse(RecordType type, Pageable pageable);
}