package com.kunal.finance.backend.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.kunal.finance.backend.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findBySeqGreaterThanOrderBySeqAsc(long seq, Pageable pageable);
}
