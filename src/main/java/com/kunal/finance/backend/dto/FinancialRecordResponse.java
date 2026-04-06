package com.kunal.finance.backend.dto;

import java.time.LocalDateTime;

import com.kunal.finance.backend.entity.RecordType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FinancialRecordResponse {

    private Long id;
    private Double amount;
    private RecordType type;
    private String category;
    private LocalDateTime date;
    private String description;
    private String createdBy;
}