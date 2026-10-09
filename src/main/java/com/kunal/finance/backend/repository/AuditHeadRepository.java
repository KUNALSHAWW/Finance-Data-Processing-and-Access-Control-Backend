package com.kunal.finance.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.kunal.finance.backend.entity.AuditHead;

import jakarta.persistence.LockModeType;

public interface AuditHeadRepository extends JpaRepository<AuditHead, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AuditHead h where h.id = 1")
    AuditHead lockHead();

    @Query("select h from AuditHead h where h.id = 1")
    AuditHead head();
}
