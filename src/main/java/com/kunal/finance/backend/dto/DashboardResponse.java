package com.kunal.finance.backend.dto;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardResponse {

    private Double totalIncome;
    private Double totalExpense;
    private Double netBalance;
    private Map<String, Double> categoryTotals;
}