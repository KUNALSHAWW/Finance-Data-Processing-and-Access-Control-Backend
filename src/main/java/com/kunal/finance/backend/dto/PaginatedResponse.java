package com.kunal.finance.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PaginatedResponse<T> {

    private List<T> data;
    private int currentPage;
    private int totalPages;
    private long totalElements;
}