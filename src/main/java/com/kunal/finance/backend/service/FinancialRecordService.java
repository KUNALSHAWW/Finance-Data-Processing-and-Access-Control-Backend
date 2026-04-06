package com.kunal.finance.backend.service;

import com.kunal.finance.backend.dto.FinancialRecordRequest;
import com.kunal.finance.backend.dto.FinancialRecordResponse;
import com.kunal.finance.backend.dto.PaginatedResponse;

public interface FinancialRecordService {

    FinancialRecordResponse createRecord(FinancialRecordRequest request, String userEmail);

    PaginatedResponse<FinancialRecordResponse> getAllRecords(int page, int size,String type);

    FinancialRecordResponse updateRecord(Long id, FinancialRecordRequest request, String Email);

    void deleteRecord(Long id);
}