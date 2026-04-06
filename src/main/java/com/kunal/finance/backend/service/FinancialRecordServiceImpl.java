package com.kunal.finance.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.kunal.finance.backend.dto.FinancialRecordRequest;
import com.kunal.finance.backend.dto.FinancialRecordResponse;
import com.kunal.finance.backend.dto.PaginatedResponse;
import com.kunal.finance.backend.entity.FinancialRecord;
import com.kunal.finance.backend.entity.RecordType;
import com.kunal.finance.backend.entity.User;
import com.kunal.finance.backend.exception.ResourceNotFoundException;
import com.kunal.finance.backend.repository.FinancialRecordRepository;
import com.kunal.finance.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FinancialRecordServiceImpl implements FinancialRecordService {

    private final FinancialRecordRepository recordRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new financial record for the authenticated user.
     */
    @Override
    public FinancialRecordResponse createRecord(FinancialRecordRequest request, String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        FinancialRecord record = FinancialRecord.builder()
                .amount(request.getAmount())
                .type(request.getType())
                .category(request.getCategory() != null ? request.getCategory() : "Others")
                .description(request.getDescription())
                .createdBy(user)
                .build();

        recordRepository.save(record);

        return mapToResponse(record);
    }

    /**
     * Updates an existing record — only the owner can do this.
     */
    @Override
    public FinancialRecordResponse updateRecord(Long id, FinancialRecordRequest request, String email) {

        // Fetch record
        FinancialRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found"));

        // Ownership check
        if (!record.getCreatedBy().getEmail().equals(email)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not allowed to update this record");
        }

        // Update fields
        record.setAmount(request.getAmount());
        record.setType(request.getType());
        record.setCategory(request.getCategory() != null ? request.getCategory() : "Others");
        record.setDescription(request.getDescription());

        recordRepository.save(record);

        return mapToResponse(record);
    }

    @Override
    public PaginatedResponse<FinancialRecordResponse> getAllRecords(int page, int size, String type) {

        Pageable pageable = PageRequest.of(page, size);

        Page<FinancialRecord> recordPage;

        if (type != null && !type.isBlank()) {

            RecordType recordType;
            try {
                recordType = RecordType.valueOf(type.toUpperCase());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid record type");
            }

            recordPage = recordRepository.findByTypeAndDeletedFalse(recordType, pageable);

        } else {
            recordPage = recordRepository.findByDeletedFalse(pageable);
        }

        List<FinancialRecordResponse> data = recordPage.getContent()
                .stream()
                .map(this::mapToResponse)
                .toList();

        return PaginatedResponse.<FinancialRecordResponse>builder()
                .data(data)
                .currentPage(recordPage.getNumber())
                .totalPages(recordPage.getTotalPages())
                .totalElements(recordPage.getTotalElements())
                .build();
    }

    @Override
    public void deleteRecord(Long id) {

        FinancialRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found"));

        if (record.isDeleted()) {
            throw new RuntimeException("Record already deleted");
        }

        record.setDeleted(true);
        record.setDeletedAt(LocalDateTime.now());
        // soft delete - we can keep the record for audit purposes
        recordRepository.save(record);
    }

    private FinancialRecordResponse mapToResponse(FinancialRecord record) {
        return FinancialRecordResponse.builder()
                .id(record.getId())
                .amount(record.getAmount())
                .type(record.getType())
                .category(record.getCategory())
                .date(record.getCreatedAt())
                .description(record.getDescription())
                .createdBy(record.getCreatedBy().getEmail())
                .build();
    }
}