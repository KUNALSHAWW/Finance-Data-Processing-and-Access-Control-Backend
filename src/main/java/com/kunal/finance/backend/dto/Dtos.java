package com.kunal.finance.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kunal.finance.backend.entity.RecordType;
import com.kunal.finance.backend.entity.Role;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** Request/response shapes, kept together because each is a few lines. */
public final class Dtos {

    private Dtos() {
    }

    // ---- auth ----
    public record AuthRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record MeResponse(String email, String role) {
    }

    public record AuthResponse(String token, String tokenType, long expiresInMs) {
    }

    // ---- users ----
    public static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,72}$";

    public record UserRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            @NotBlank(message = "Email is required") @Email(message = "Invalid email format") @Size(max = 255) String email,
            @NotBlank(message = "Password is required")
            @Pattern(regexp = PASSWORD_PATTERN, message = "Password must be 8-72 characters and include uppercase, lowercase, number, and special character") String password,
            @NotNull(message = "Role is required") Role role) {
    }

    public record UserResponse(Long id, String name, String email, Role role, boolean active) {
    }

    // ---- records ----
    public record FinancialRecordRequest(
            @NotNull(message = "Amount is required") @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
            @Digits(integer = 15, fraction = 2, message = "Amount allows at most 2 decimal places") BigDecimal amount,
            @NotNull(message = "Type is required") RecordType type,
            @Size(max = 100) String category,
            @PastOrPresent(message = "Date cannot be in the future") LocalDate date,
            @Size(max = 500) String description) {
    }

    public record FinancialRecordResponse(Long id, BigDecimal amount, RecordType type, String category,
            LocalDate date, String description, String createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record PaginatedResponse<T>(List<T> data, int currentPage, int totalPages, long totalElements) {
    }

    // ---- dashboard ----
    public record CategoryBreakdown(String category, BigDecimal income, BigDecimal expense, BigDecimal net) {
    }

    public record DashboardSummary(BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal netBalance,
            long recordCount, List<CategoryBreakdown> categoryBreakdown) {
    }

    public record MonthlyTrend(String month, BigDecimal income, BigDecimal expense, BigDecimal net) {
    }

    public record Anomaly(Long recordId, String category, RecordType type, BigDecimal amount,
            BigDecimal typicalAmount, double score, String reason) {
    }

    public record Insights(String method, int windowMonths, List<Anomaly> anomalies) {
    }

    // ---- public / demo ----
    public record DemoAccount(String role, String email, String password) {
    }

    public record PublicConfig(boolean demo, List<DemoAccount> accounts) {
    }

    public record VisitRequest(@Size(max = 100) String source, @Size(max = 100) String medium,
            @Size(max = 100) String campaign, @Size(max = 100) String content, @Size(max = 100) String term,
            @Size(max = 100) String path) {
    }

    public record TamperResult(Long recordId, String category, BigDecimal originalAmount, BigDecimal newAmount,
            String sql) {
    }

    // ---- misc ----
    public record MessageResponse(String status, String message) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(String error, String message, Map<String, String> fieldErrors) {
        public static ErrorResponse of(String error, String message) {
            return new ErrorResponse(error, message, null);
        }
    }
}
