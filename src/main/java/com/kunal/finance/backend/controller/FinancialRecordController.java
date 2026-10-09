package com.kunal.finance.backend.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kunal.finance.backend.dto.Dtos.FinancialRecordRequest;
import com.kunal.finance.backend.dto.Dtos.FinancialRecordResponse;
import com.kunal.finance.backend.dto.Dtos.PaginatedResponse;
import com.kunal.finance.backend.entity.RecordType;
import com.kunal.finance.backend.service.FinancialRecordService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
@Tag(name = "Financial Records", description = "All roles can read; only ADMIN can create, update or delete")
public class FinancialRecordController {

    private final FinancialRecordService recordService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a record", description = "Appends a CREATED entry to the audit ledger in the same transaction.")
    public ResponseEntity<FinancialRecordResponse> create(@Valid @RequestBody FinancialRecordRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recordService.create(request, auth.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a record", description = "Any admin may update any non-deleted record. Concurrent edits are detected (409).")
    public ResponseEntity<FinancialRecordResponse> update(@PathVariable Long id,
            @Valid @RequestBody FinancialRecordRequest request, Authentication auth) {
        return ResponseEntity.ok(recordService.update(id, request, auth.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft-delete a record", description = "The row is kept and flagged; the deletion is audited.")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        recordService.delete(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','VIEWER')")
    @Operation(summary = "Get one record")
    public ResponseEntity<FinancialRecordResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(recordService.get(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','VIEWER')")
    @Operation(summary = "List records", description = "Paginated (size 1-100), newest first. Filter by type, category and date range.")
    public ResponseEntity<PaginatedResponse<FinancialRecordResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) RecordType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(recordService.list(page, size, type, category, from, to));
    }
}
