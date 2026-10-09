package com.kunal.finance.backend.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.kunal.finance.backend.exception.ConflictException;
import com.kunal.finance.backend.exception.DemoRestrictionException;
import com.kunal.finance.backend.repository.FinancialRecordRepository;

import lombok.RequiredArgsConstructor;

/** Safeguards for the public demo only. With app.demo.enabled=false (the default) every check is a no-op. */
@Component
@RequiredArgsConstructor
public class DemoGuard {

    static final long MAX_RECORDS = 400;

    private final FinancialRecordRepository records;

    @Value("${app.demo.enabled:false}")
    private boolean enabled;

    public boolean enabled() {
        return enabled;
    }

    /** Public demo accounts must not be deactivated or deleted by whoever visits next. */
    public void assertUserWritesAllowed() {
        if (enabled) {
            throw new DemoRestrictionException("User management is read-only in the public demo");
        }
    }

    public void assertRecordCapacity() {
        if (enabled && records.count() >= MAX_RECORDS) {
            throw new ConflictException("Demo record limit reached; use Restore demo data");
        }
    }
}
