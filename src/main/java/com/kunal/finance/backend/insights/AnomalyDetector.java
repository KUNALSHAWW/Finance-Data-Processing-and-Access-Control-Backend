package com.kunal.finance.backend.insights;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kunal.finance.backend.dto.Dtos.Anomaly;
import com.kunal.finance.backend.entity.FinancialRecord;

/**
 * Flags records that are unusual for their own (type, category) using the Iglewicz-Hoaglin modified z-score:
 * M = 0.6745 * (x - median) / MAD, flagged when |M| > 3.5. Median/MAD are used instead of mean/stddev because a
 * single huge outlier inflates the stddev enough to hide itself. When MAD is 0 (many identical amounts) it falls back
 * to the mean absolute deviation with the 1.253314 scale factor. Every flag carries a plain-English reason.
 */
public final class AnomalyDetector {

    public static final double THRESHOLD = 3.5;
    public static final int MIN_SAMPLES = 5;
    public static final String METHOD = "Modified z-score (median/MAD), flag when |score| > 3.5, "
            + "needs at least 5 records per type and category";

    private static final double MAD_SCALE = 0.6745;
    private static final double MEAN_AD_SCALE = 1.253314;

    private AnomalyDetector() {
    }

    public static List<Anomaly> detect(Collection<FinancialRecord> records) {
        Map<String, List<FinancialRecord>> groups = records.stream()
                .collect(Collectors.groupingBy(r -> r.getType() + "|" + r.getCategory().toLowerCase()));
        List<Anomaly> out = new ArrayList<>();
        for (List<FinancialRecord> g : groups.values()) {
            if (g.size() < MIN_SAMPLES) {
                continue;
            }
            double[] v = g.stream().mapToDouble(r -> r.getAmount().doubleValue()).toArray();
            double median = median(v);
            double[] dev = Arrays.stream(v).map(x -> Math.abs(x - median)).toArray();
            double mad = median(dev);
            double scale;
            if (mad > 0) {
                scale = mad / MAD_SCALE;
            } else {
                double meanAd = Arrays.stream(dev).average().orElse(0);
                if (meanAd == 0) {
                    continue; // all amounts identical: nothing can be unusual
                }
                scale = MEAN_AD_SCALE * meanAd;
            }
            for (FinancialRecord r : g) {
                double score = (r.getAmount().doubleValue() - median) / scale;
                if (Math.abs(score) > THRESHOLD) {
                    out.add(toAnomaly(r, median, score, g.size()));
                }
            }
        }
        out.sort(Comparator.comparingDouble((Anomaly a) -> Math.abs(a.score())).reversed());
        return out;
    }

    private static Anomaly toAnomaly(FinancialRecord r, double median, double score, int n) {
        BigDecimal typical = BigDecimal.valueOf(median).setScale(2, RoundingMode.HALF_UP);
        String dir = score > 0 ? "above" : "below";
        String reason = String.format("%s of %s is far %s the typical %s in '%s' (median %s over %d records, score %.1f)",
                r.getType().name().toLowerCase(), r.getAmount().toPlainString(), dir,
                r.getType().name().toLowerCase(), r.getCategory(), typical.toPlainString(), n, score);
        return new Anomaly(r.getId(), r.getCategory(), r.getType(), r.getAmount(), typical,
                Math.round(score * 100.0) / 100.0, reason);
    }

    static double median(double[] values) {
        double[] s = values.clone();
        Arrays.sort(s);
        int n = s.length;
        return n % 2 == 1 ? s[n / 2] : (s[n / 2 - 1] + s[n / 2]) / 2.0;
    }
}
