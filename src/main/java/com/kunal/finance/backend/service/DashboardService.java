package com.kunal.finance.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kunal.finance.backend.dto.Dtos.CategoryBreakdown;
import com.kunal.finance.backend.dto.Dtos.DashboardSummary;
import com.kunal.finance.backend.dto.Dtos.Insights;
import com.kunal.finance.backend.dto.Dtos.MonthlyTrend;
import com.kunal.finance.backend.entity.RecordType;
import com.kunal.finance.backend.insights.AnomalyDetector;
import com.kunal.finance.backend.repository.FinancialRecordRepository;
import com.kunal.finance.backend.repository.FinancialRecordRepository.CategoryTotal;
import com.kunal.finance.backend.repository.FinancialRecordRepository.TypeTotal;

import lombok.RequiredArgsConstructor;

/** All totals are computed by the database (GROUP BY), never by loading the table into memory. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final LocalDate MIN = LocalDate.of(1970, 1, 1);
    private static final LocalDate MAX = LocalDate.of(9999, 12, 31);

    private final FinancialRecordRepository records;

    public DashboardSummary summary(LocalDate from, LocalDate to) {
        LocalDate f = from != null ? from : MIN;
        LocalDate t = to != null ? to : MAX;

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        long count = 0;
        for (TypeTotal tt : records.totalsByType(f, t)) {
            count += tt.getCnt();
            if (tt.getType() == RecordType.INCOME) {
                income = tt.getTotal();
            } else {
                expense = tt.getTotal();
            }
        }

        // category rows arrive as (category, type, total); fold income and expense into one row per category
        Map<String, BigDecimal[]> byCategory = new TreeMap<>();
        for (CategoryTotal ct : records.totalsByCategory(f, t)) {
            BigDecimal[] pair = byCategory.computeIfAbsent(ct.getCategory(),
                    k -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
            pair[ct.getType() == RecordType.INCOME ? 0 : 1] = ct.getTotal();
        }
        List<CategoryBreakdown> breakdown = byCategory.entrySet().stream()
                .map(e -> new CategoryBreakdown(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[0].subtract(e.getValue()[1])))
                .toList();

        return new DashboardSummary(income, expense, income.subtract(expense), count, breakdown);
    }

    /** Last {@code months} calendar months including the current one; months with no activity are zero-filled. */
    public List<MonthlyTrend> trends(int months) {
        YearMonth last = YearMonth.now(ZoneOffset.UTC);
        YearMonth first = last.minusMonths(months - 1L);

        Map<YearMonth, BigDecimal[]> byMonth = new TreeMap<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            byMonth.put(m, new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
        }
        for (Object[] row : records.monthlyTotals(first.atDay(1), last.atEndOfMonth())) {
            YearMonth m = YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
            byMonth.get(m)[row[2] == RecordType.INCOME ? 0 : 1] = (BigDecimal) row[3];
        }
        List<MonthlyTrend> out = new ArrayList<>();
        byMonth.forEach((m, v) -> out.add(new MonthlyTrend(m.toString(), v[0], v[1], v[0].subtract(v[1]))));
        return out;
    }

    public Insights insights(int months) {
        YearMonth last = YearMonth.now(ZoneOffset.UTC);
        LocalDate from = last.minusMonths(months - 1L).atDay(1);
        var live = records.findByDeletedFalseAndRecordDateBetween(from, last.atEndOfMonth());
        return new Insights(AnomalyDetector.METHOD, months, AnomalyDetector.detect(live));
    }
}
