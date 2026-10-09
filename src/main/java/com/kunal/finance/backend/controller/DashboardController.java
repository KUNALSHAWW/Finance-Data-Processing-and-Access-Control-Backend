package com.kunal.finance.backend.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kunal.finance.backend.dto.Dtos.DashboardSummary;
import com.kunal.finance.backend.dto.Dtos.Insights;
import com.kunal.finance.backend.dto.Dtos.MonthlyTrend;
import com.kunal.finance.backend.service.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Summaries for every role; anomaly insights for ADMIN and ANALYST")
public class DashboardController {

    private final DashboardService dashboard;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','VIEWER')")
    @Operation(summary = "Income, expense, net balance and per-category breakdown",
            description = "Optional from/to (ISO dates). Computed with GROUP BY in the database.")
    public ResponseEntity<DashboardSummary> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(dashboard.summary(from, to));
    }

    @GetMapping("/trends")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST','VIEWER')")
    @Operation(summary = "Monthly income/expense/net for the last N months (zero-filled)")
    public ResponseEntity<List<MonthlyTrend>> trends(@RequestParam(defaultValue = "12") @Min(1) @Max(60) int months) {
        return ResponseEntity.ok(dashboard.trends(months));
    }

    @GetMapping("/insights")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    @Operation(summary = "Explainable anomaly detection",
            description = "Flags records far outside the typical amount for their own type and category (modified z-score on median/MAD). Every flag includes its reason.")
    public ResponseEntity<Insights> insights(@RequestParam(defaultValue = "12") @Min(1) @Max(60) int months) {
        return ResponseEntity.ok(dashboard.insights(months));
    }
}
