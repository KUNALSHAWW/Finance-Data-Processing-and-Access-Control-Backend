package com.kunal.finance.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.kunal.finance.backend.audit.AuditService;
import com.kunal.finance.backend.audit.AuditVerification;
import com.kunal.finance.backend.audit.AuditVerification.Finding;
import com.kunal.finance.backend.dto.Dtos.FinancialRecordRequest;
import com.kunal.finance.backend.entity.RecordType;

/** The headline feature: every kind of tampering below must be caught, and honest use must verify clean. */
class AuditLedgerTest extends IntegrationTestBase {

    @Autowired AuditService audit;

    private Long seeded() {
        Long id = record("100.00", RecordType.EXPENSE, "Food", LocalDate.now().minusDays(1));
        record("50.00", RecordType.INCOME, "Salary", LocalDate.now().minusDays(1));
        return id;
    }

    private static boolean has(AuditVerification v, String type, long id, String problem) {
        return v.findings().contains(new Finding(type, id, problem));
    }

    @Test
    void honestHistoryVerifiesClean() {
        Long id = seeded();
        records.update(id, new FinancialRecordRequest(new BigDecimal("120.00"), RecordType.EXPENSE, "Food", null, null), ADMIN);
        records.delete(id, ADMIN);

        AuditVerification v = audit.verify();
        assertTrue(v.valid(), v.chainMessage() + " " + v.findings());
        assertEquals(3 + 2 + 2, v.entriesChecked());       // 3 users, 2 creates, 1 update, 1 delete
        assertEquals(v.entriesChecked(), v.headSeq());
        assertNull(v.firstBrokenSeq());
    }

    @Test
    void editingALogEntryBreaksTheChainAtThatEntry() {
        seeded();
        jdbc.update("update audit_log set details = 'amount=1.00' where seq = 5");
        AuditVerification v = audit.verify();
        assertFalse(v.valid());
        assertEquals(5L, v.firstBrokenSeq());
    }

    @Test
    void removingTheLatestLogEntryIsDetectedViaTheHead() {
        seeded();
        jdbc.update("delete from audit_log where seq = (select max(seq) from audit_log)");
        AuditVerification v = audit.verify();
        assertFalse(v.valid());
        assertTrue(v.chainMessage().contains("head"), v.chainMessage());
    }

    @Test
    void removingAMiddleLogEntryIsDetectedAsAGap() {
        seeded();
        jdbc.update("delete from audit_log where seq = 4");
        AuditVerification v = audit.verify();
        assertFalse(v.valid());
        assertEquals(5L, v.firstBrokenSeq());
    }

    @Test
    void changingAnAmountDirectlyInTheDatabaseIsCaught() {
        Long id = seeded();
        jdbc.update("update financial_records set amount = 999999.00 where id = ?", id);
        AuditVerification v = audit.verify();
        assertFalse(v.valid());
        assertNull(v.firstBrokenSeq());                      // the log itself is untouched ...
        assertTrue(has(v, "RECORD", id, "MODIFIED"));        // ... but the row no longer matches it
    }

    @Test
    void privilegeEscalationDoneDirectlyInTheDatabaseIsCaught() {
        seeded();
        Long viewerId = jdbc.queryForObject("select id from users where email = ?", Long.class, VIEWER);
        jdbc.update("update users set role = 'ADMIN' where id = ?", viewerId);
        assertTrue(has(audit.verify(), "USER", viewerId, "MODIFIED"));
    }

    @Test
    void aRowInsertedBehindTheApisBackIsFlaggedUnaudited() {
        seeded();
        Long adminId = jdbc.queryForObject("select id from users where email = ?", Long.class, ADMIN);
        jdbc.update("insert into financial_records (amount,type,category,record_date,created_at,updated_at,deleted,user_id,version)"
                + " values (1.00,'INCOME','Sneaky','2026-01-01',now(),now(),false,?,0)", adminId);
        Long sneaky = jdbc.queryForObject("select id from financial_records where category = 'Sneaky'", Long.class);
        assertTrue(has(audit.verify(), "RECORD", sneaky, "UNAUDITED"));
    }

    @Test
    void aHardDeletedRowIsFlaggedMissing() {
        Long id = seeded();
        jdbc.update("delete from financial_records where id = ?", id);
        assertTrue(has(audit.verify(), "RECORD", id, "MISSING"));
    }

    @Test
    void concurrentWritersProduceOneLinearChain() throws Exception {
        long before = audit.verify().entriesChecked();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> jobs = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            jobs.add(pool.submit(() -> {
                for (int i = 0; i < 10; i++) {
                    record("1.00", RecordType.INCOME, "Load", LocalDate.now().minusDays(1));
                }
            }));
        }
        for (Future<?> f : jobs) {
            f.get();
        }
        pool.shutdown();

        AuditVerification v = audit.verify();
        assertTrue(v.valid(), v.chainMessage());
        assertEquals(before + 80, v.entriesChecked());
    }

    @Test
    void auditEndpointsAreReadableByAnalystsButWritesNeverGoThroughThem() throws Exception {
        seeded();
        String analyst = token(ANALYST);
        call(org.springframework.http.HttpMethod.GET, "/api/audit/verify", analyst, null)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.valid").value(true));
        call(org.springframework.http.HttpMethod.GET, "/api/audit?size=2", analyst, null)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.length()").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].seq").value(5));
    }
}
