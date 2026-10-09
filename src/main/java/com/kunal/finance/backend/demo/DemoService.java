package com.kunal.finance.backend.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kunal.finance.backend.audit.AuditHasher;
import com.kunal.finance.backend.dto.Dtos.DemoAccount;
import com.kunal.finance.backend.dto.Dtos.TamperResult;
import com.kunal.finance.backend.dto.Dtos.UserRequest;
import com.kunal.finance.backend.entity.Role;
import com.kunal.finance.backend.repository.UserRepository;
import com.kunal.finance.backend.service.FinancialRecordService;
import com.kunal.finance.backend.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Only exists when app.demo.enabled=true. Seeds realistic data through the normal services (so the audit ledger is
 * consistent), resets it on a timer, and can edit a row with raw SQL to show the ledger catching it.
 */
@Service
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoService implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final UserService users;
    private final FinancialRecordService records;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            seed();
        }
    }

    @Scheduled(initialDelayString = "${app.demo.reset-interval:PT30M}", fixedDelayString = "${app.demo.reset-interval:PT30M}")
    @Transactional
    public void reset() {
        jdbc.update("delete from audit_log");
        jdbc.update("update audit_head set last_seq = 0, last_hash = ? where id = 1", AuditHasher.GENESIS);
        jdbc.update("delete from financial_records");
        jdbc.update("delete from users");
        seed();
        log.info("Demo data reset");
    }

    private void seed() {
        for (DemoAccount a : DemoData.ACCOUNTS) {
            users.create(new UserRequest("Demo " + a.role().charAt(0) + a.role().substring(1).toLowerCase(), a.email(),
                    a.password(), Role.valueOf(a.role())), "system");
        }
        DemoData.records(LocalDate.now(ZoneOffset.UTC)).forEach(r -> records.create(r, DemoData.ADMIN_EMAIL));
        log.info("Demo data seeded");
    }

    /** Edits the biggest expense with raw SQL, exactly what someone with database access could do to hide it. */
    @Transactional
    public TamperResult tamper() {
        Map<String, Object> row = jdbc.queryForMap("select id, category, amount from financial_records "
                + "where type = 'EXPENSE' and deleted = false order by amount desc limit 1");
        long id = ((Number) row.get("id")).longValue();
        BigDecimal original = (BigDecimal) row.get("amount");
        BigDecimal hidden = original.movePointLeft(2).setScale(2, java.math.RoundingMode.HALF_UP);
        jdbc.update("update financial_records set amount = ? where id = ?", hidden, id);
        String sql = "UPDATE financial_records SET amount = " + hidden.toPlainString() + " WHERE id = " + id + ";";
        return new TamperResult(id, (String) row.get("category"), original, hidden, sql);
    }
}
