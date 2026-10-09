package com.kunal.finance.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kunal.finance.backend.audit.AuditService;
import com.kunal.finance.backend.audit.AuditVerification;
import com.kunal.finance.backend.dto.Dtos.PaginatedResponse;
import com.kunal.finance.backend.entity.AuditLog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
@Tag(name = "Audit Ledger", description = "Hash-chained history of every record and user change")
public class AuditController {

    private final AuditService audit;

    @GetMapping
    @Operation(summary = "List audit entries, newest first")
    public ResponseEntity<PaginatedResponse<AuditLog>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var p = audit.list(page, size);
        return ResponseEntity.ok(new PaginatedResponse<>(p.getContent(), p.getNumber(), p.getTotalPages(),
                p.getTotalElements()));
    }

    @GetMapping("/verify")
    @Operation(summary = "Verify the ledger",
            description = "Recomputes the whole hash chain, then checks every live record and user against its last audited state. "
                    + "Detects edited or deleted log entries and rows changed directly in the database. "
                    + "Store headHash somewhere the database owner cannot rewrite to also detect a fully rebuilt chain.")
    public ResponseEntity<AuditVerification> verify() {
        return ResponseEntity.ok(audit.verify());
    }
}
