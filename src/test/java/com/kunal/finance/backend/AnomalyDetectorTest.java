package com.kunal.finance.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.kunal.finance.backend.dto.Dtos.Anomaly;
import com.kunal.finance.backend.entity.FinancialRecord;
import com.kunal.finance.backend.entity.RecordType;
import com.kunal.finance.backend.insights.AnomalyDetector;

class AnomalyDetectorTest {

    private static List<FinancialRecord> expenses(String category, double... amounts) {
        List<FinancialRecord> out = new ArrayList<>();
        long id = 1;
        for (double a : amounts) {
            out.add(FinancialRecord.builder().id(id++).amount(BigDecimal.valueOf(a)).type(RecordType.EXPENSE)
                    .category(category).build());
        }
        return out;
    }

    @Test
    void flagsOnlyTheOutlierUsingMedianAndMad() {
        // median 10, MAD 1 -> 95 scores 0.6745*85 = 57.3; the ordinary 12 scores 1.35
        List<Anomaly> found = AnomalyDetector.detect(expenses("Food", 10, 11, 9, 10, 12, 10, 95));
        assertEquals(1, found.size());
        assertEquals(7L, found.get(0).recordId());
        assertEquals(57.33, found.get(0).score(), 0.01);
    }

    @Test
    void aHugeOutlierDoesNotHideItselfTheWayItWouldWithMeanAndStddev() {
        // with mean/stddev the 1_000_000 can never score above (n-1)/sqrt(n) = 2.27 for n=7 (below the 3.5 cutoff) and slip through
        assertEquals(1, AnomalyDetector.detect(expenses("Food", 10, 11, 9, 10, 12, 10, 1_000_000)).size());
    }

    @Test
    void fallsBackToMeanAbsoluteDeviationWhenMostAmountsAreIdentical() {
        List<Anomaly> found = AnomalyDetector.detect(expenses("Groceries", 100, 100, 100, 100, 100, 100, 5000));
        assertEquals(1, found.size());
        assertEquals(7L, found.get(0).recordId());
    }

    @Test
    void needsEnoughSamplesAndSomeSpread() {
        assertTrue(AnomalyDetector.detect(expenses("Food", 10, 10, 10, 500)).isEmpty());           // only 4 records
        assertTrue(AnomalyDetector.detect(expenses("Food", 10, 10, 10, 10, 10, 10)).isEmpty());    // all identical
    }

    @Test
    void categoriesAreJudgedAgainstThemselvesNotEachOther() {
        List<FinancialRecord> mixed = new ArrayList<>(expenses("Rent", 1000, 1010, 990, 1000, 1005));
        mixed.addAll(expenses("Coffee", 4, 5, 4, 5, 4));
        assertTrue(AnomalyDetector.detect(mixed).isEmpty());   // 1000 is huge next to 4, but normal for Rent
    }
}
