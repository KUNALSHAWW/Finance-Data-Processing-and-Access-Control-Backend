package com.kunal.finance.backend;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.kunal.finance.backend.entity.RecordType;

class RecordsAndDashboardTest extends IntegrationTestBase {

    @Test
    void recordLifecycleKeepsBusinessDateAndSoftDeletes() throws Exception {
        String admin = token(ADMIN);
        LocalDate day = LocalDate.now().minusDays(10);

        String created = call(HttpMethod.POST, "/api/records", admin,
                Map.of("amount", "100.50", "type", "INCOME", "category", "Salary", "date", day.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value(day.toString()))   // the entered date, not the insert time
                .andExpect(jsonPath("$.amount").value(100.50))
                .andExpect(jsonPath("$.createdBy").value(ADMIN))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        call(HttpMethod.PUT, "/api/records/" + id, admin, Map.of("amount", "200.00", "type", "INCOME", "category", "Bonus"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(200.0))
                .andExpect(jsonPath("$.category").value("Bonus"));

        call(HttpMethod.DELETE, "/api/records/" + id, admin, null).andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/records/" + id, admin, null).andExpect(status().isNotFound());
        call(HttpMethod.PUT, "/api/records/" + id, admin, Map.of("amount", "1.00", "type", "INCOME"))
                .andExpect(status().isNotFound());                      // soft-deleted rows cannot be edited
        call(HttpMethod.DELETE, "/api/records/" + id, admin, null).andExpect(status().isConflict());
        call(HttpMethod.GET, "/api/records", admin, null).andExpect(jsonPath("$.totalElements").value(0));

        Integer rowsKept = jdbc.queryForObject("select count(*) from financial_records where id = ?", Integer.class, id);
        org.junit.jupiter.api.Assertions.assertEquals(1, rowsKept);     // the row is still there for audit
    }

    @Test
    void validationReturnsFieldErrorsAndProperStatusCodes() throws Exception {
        String admin = token(ADMIN);
        call(HttpMethod.POST, "/api/records", admin, Map.of("amount", "-5", "type", "INCOME"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.amount").exists());
        call(HttpMethod.POST, "/api/records", admin, Map.of("amount", "1.234", "type", "INCOME"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.amount").exists());
        call(HttpMethod.POST, "/api/records", admin, Map.of("amount", "5"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.type").exists());
        call(HttpMethod.POST, "/api/records", admin,
                Map.of("amount", "5", "type", "INCOME", "date", LocalDate.now().plusDays(3).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.date").exists());
        call(HttpMethod.POST, "/api/records", admin, "{not json").andExpect(status().isBadRequest());

        call(HttpMethod.GET, "/api/records?size=1000", admin, null).andExpect(status().isBadRequest());
        call(HttpMethod.GET, "/api/records?page=-1", admin, null).andExpect(status().isBadRequest());
        call(HttpMethod.GET, "/api/records?type=BOGUS", admin, null).andExpect(status().isBadRequest());
        call(HttpMethod.GET, "/api/records/abc", admin, null).andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, "/api/records", admin, null).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void listFiltersByTypeCategoryAndDate() throws Exception {
        LocalDate d = LocalDate.now().minusDays(1);
        record("10.00", RecordType.INCOME, "Salary", d);
        record("20.00", RecordType.EXPENSE, "Food", d);
        record("30.00", RecordType.EXPENSE, "Rent", LocalDate.of(2020, 1, 15));
        String viewer = token(VIEWER);
        call(HttpMethod.GET, "/api/records?type=EXPENSE", viewer, null).andExpect(jsonPath("$.totalElements").value(2));
        call(HttpMethod.GET, "/api/records?category=food", viewer, null).andExpect(jsonPath("$.totalElements").value(1));
        call(HttpMethod.GET, "/api/records?from=2020-01-01&to=2020-01-31", viewer, null)
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.data[0].category").value("Rent"));
    }

    @Test
    void summaryIsExactAndNeverMixesIncomeWithExpensePerCategory() throws Exception {
        LocalDate d = LocalDate.now().minusDays(1);
        record("1000.00", RecordType.INCOME, "Salary", d);
        record("250.25", RecordType.INCOME, "Freelance", d);
        record("400.10", RecordType.EXPENSE, "Rent", d);
        record("50.05", RecordType.EXPENSE, "Food", d);
        record("25.20", RecordType.EXPENSE, "Food", d);
        record("100.00", RecordType.EXPENSE, "Salary", d);      // same category name, opposite direction
        Long gone = record("9999.00", RecordType.INCOME, "Salary", d);
        records.delete(gone, ADMIN);                            // deleted rows must not be counted

        call(HttpMethod.GET, "/api/dashboard/summary", token(VIEWER), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(1250.25))
                .andExpect(jsonPath("$.totalExpense").value(575.35))
                .andExpect(jsonPath("$.netBalance").value(674.90))
                .andExpect(jsonPath("$.recordCount").value(6))
                .andExpect(jsonPath("$.categoryBreakdown[?(@.category=='Salary')].income").value(1000.0))
                .andExpect(jsonPath("$.categoryBreakdown[?(@.category=='Salary')].expense").value(100.0))
                .andExpect(jsonPath("$.categoryBreakdown[?(@.category=='Salary')].net").value(900.0))
                .andExpect(jsonPath("$.categoryBreakdown[?(@.category=='Food')].expense").value(75.25));
    }

    @Test
    void summaryHonoursDateRangeAndTrendsAreZeroFilled() throws Exception {
        record("500.00", RecordType.INCOME, "Salary", LocalDate.of(2020, 1, 15));
        record("40.00", RecordType.EXPENSE, "Food", LocalDate.now());
        String viewer = token(VIEWER);
        call(HttpMethod.GET, "/api/dashboard/summary?from=2020-01-01&to=2020-01-31", viewer, null)
                .andExpect(jsonPath("$.totalIncome").value(500.0)).andExpect(jsonPath("$.totalExpense").value(0));

        call(HttpMethod.GET, "/api/dashboard/trends?months=3", viewer, null)
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].expense").value(40.0))
                .andExpect(jsonPath("$[0].income").value(0));
        call(HttpMethod.GET, "/api/dashboard/trends?months=0", viewer, null).andExpect(status().isBadRequest());
    }

    @Test
    void insightsFlagTheOutlierWithAReason() throws Exception {
        LocalDate d = LocalDate.now().minusDays(1);
        for (int i = 0; i < 6; i++) {
            record("100.00", RecordType.EXPENSE, "Groceries", d);
        }
        Long odd = record("5000.00", RecordType.EXPENSE, "Groceries", d);

        call(HttpMethod.GET, "/api/dashboard/insights", token(ANALYST), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anomalies.length()").value(1))
                .andExpect(jsonPath("$.anomalies[0].recordId").value(odd))
                .andExpect(jsonPath("$.anomalies[0].typicalAmount").value(100.0))
                .andExpect(jsonPath("$.anomalies[0].reason").value(org.hamcrest.Matchers.containsString("far above")));
    }
}
