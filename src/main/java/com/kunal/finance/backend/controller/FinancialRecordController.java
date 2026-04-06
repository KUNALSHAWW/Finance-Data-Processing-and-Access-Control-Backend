package com.kunal.finance.backend.controller;

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

import com.kunal.finance.backend.dto.FinancialRecordRequest;
import com.kunal.finance.backend.dto.FinancialRecordResponse;
import com.kunal.finance.backend.dto.PaginatedResponse;
import com.kunal.finance.backend.service.FinancialRecordService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
@Tag(name="Financial Records", description="Endpoints for managing financial records (Admin only for create/update/delete, all roles can view)")
public class FinancialRecordController {

    private final FinancialRecordService recordService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new financial record (Admin only)", description = "Creates a new financial record with the specified details. This endpoint is restricted to admin users.")
    public ResponseEntity<FinancialRecordResponse> create(
            @Valid @RequestBody FinancialRecordRequest request,
            Authentication authentication) {

        FinancialRecordResponse response = recordService.createRecord(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a financial record (Admin only)", description = "Updates the financial record with the specified ID. Only the owner of the record or an admin can perform this action.")
    public ResponseEntity<FinancialRecordResponse> update(@PathVariable Long id,
            @Valid @RequestBody FinancialRecordRequest request, Authentication authentication) {

        return ResponseEntity.ok(recordService.updateRecord(id, request, authentication.getName()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN','ANALYST')")
    @Operation(summary = "Get financial records with pagination and filtering", description = "Retrieves a paginated list of financial records. Supports optional filtering by record type (INCOME or EXPENSE). This endpoint is accessible to all authenticated users.")
    public ResponseEntity<PaginatedResponse<FinancialRecordResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) String type) {

        return ResponseEntity.ok(recordService.getAllRecords(page, size, type));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a financial record (Admin only)", description = "Deletes the financial record with the specified ID. Only the owner of the record or an admin can perform this action.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recordService.deleteRecord(id);
        return ResponseEntity.noContent().build();
    }
}