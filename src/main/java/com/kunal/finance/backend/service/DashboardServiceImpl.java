package com.kunal.finance.backend.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.kunal.finance.backend.dto.DashboardResponse;
import com.kunal.finance.backend.entity.FinancialRecord;
import com.kunal.finance.backend.entity.RecordType;
import com.kunal.finance.backend.repository.FinancialRecordRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

        private final FinancialRecordRepository recordRepository;

        @Override
        public DashboardResponse getSummary() {

                List<FinancialRecord> records = recordRepository.findAll();

                double totalIncome = records.stream()
                                .filter(r -> !r.isDeleted() && r.getType() == RecordType.INCOME)
                                .mapToDouble(FinancialRecord::getAmount)
                                .sum();

                double totalExpense = records.stream()
                                .filter(r -> !r.isDeleted() && r.getType() == RecordType.EXPENSE)
                                .mapToDouble(FinancialRecord::getAmount)
                                .sum();

                double netBalance = totalIncome - totalExpense;

                Map<String, Double> categoryTotals = records.stream()
                                .filter(r -> !r.isDeleted())
                                .collect(Collectors.groupingBy(
                                                FinancialRecord::getCategory,
                                                Collectors.summingDouble(FinancialRecord::getAmount)));

                return DashboardResponse.builder()
                                .totalIncome(totalIncome)
                                .totalExpense(totalExpense)
                                .netBalance(netBalance)
                                .categoryTotals(categoryTotals)
                                .build();
        }
}