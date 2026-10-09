package com.kunal.finance.backend.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.kunal.finance.backend.dto.Dtos.DemoAccount;
import com.kunal.finance.backend.dto.Dtos.FinancialRecordRequest;
import com.kunal.finance.backend.entity.RecordType;

/** Synthetic data only. The demo passwords are public on purpose: the data is fake and resets itself. */
public final class DemoData {

    public static final String ADMIN_EMAIL = "admin@demo.example";
    public static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount("ADMIN", ADMIN_EMAIL, "Demo@Admin1"),
            new DemoAccount("ANALYST", "analyst@demo.example", "Demo@Analyst1"),
            new DemoAccount("VIEWER", "viewer@demo.example", "Demo@Viewer1"));

    private DemoData() {
    }

    /** Six months of plausible household finances, relative to today, with two planted oddities. */
    static List<FinancialRecordRequest> records(LocalDate today) {
        Random rnd = new Random(42);
        List<FinancialRecordRequest> out = new ArrayList<>();
        for (int m = 5; m >= 0; m--) {
            LocalDate first = today.withDayOfMonth(1).minusMonths(m);
            int days = Math.min(first.lengthOfMonth(), m == 0 ? today.getDayOfMonth() : 31);
            out.add(rec("6200.00", RecordType.INCOME, "Salary", first, "Monthly salary"));
            if (m % 2 == 0) {
                out.add(rec(money(rnd, 700, 1500), RecordType.INCOME, "Freelance", day(first, 12, days), "Side project"));
            }
            out.add(rec("1450.00", RecordType.EXPENSE, "Rent", day(first, 2, days), "Apartment rent"));
            out.add(rec(money(rnd, 90, 130), RecordType.EXPENSE, "Utilities", day(first, 8, days), "Electricity and internet"));
            for (int i = 0; i < 8; i++) {
                out.add(rec(money(rnd, 55, 140), RecordType.EXPENSE, "Groceries", day(first, 1 + i * 3, days), null));
            }
            for (int i = 0; i < 6; i++) {
                out.add(rec(money(rnd, 14, 46), RecordType.EXPENSE, "Transport", day(first, 3 + i * 4, days), null));
            }
            for (int i = 0; i < 3; i++) {
                out.add(rec(money(rnd, 20, 85), RecordType.EXPENSE, "Entertainment", day(first, 5 + i * 9, days), null));
            }
        }
        // the two records the anomaly detector should flag
        out.add(rec("1840.00", RecordType.EXPENSE, "Groceries", today.minusDays(9), "Bulk order, needs review"));
        out.add(rec("950.00", RecordType.EXPENSE, "Entertainment", today.minusDays(20), "Event tickets"));
        return out;
    }

    private static LocalDate day(LocalDate first, int d, int maxDay) {
        return first.withDayOfMonth(Math.max(1, Math.min(d, maxDay)));
    }

    private static String money(Random rnd, int lo, int hi) {
        return BigDecimal.valueOf(lo + rnd.nextInt((hi - lo) * 100) / 100.0).setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static FinancialRecordRequest rec(String amount, RecordType type, String category, LocalDate date,
            String description) {
        return new FinancialRecordRequest(new BigDecimal(amount), type, category, date, description);
    }
}
