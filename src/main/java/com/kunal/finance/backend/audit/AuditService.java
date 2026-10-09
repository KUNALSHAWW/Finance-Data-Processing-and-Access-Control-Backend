package com.kunal.finance.backend.audit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.kunal.finance.backend.audit.AuditVerification.Finding;
import com.kunal.finance.backend.entity.AuditHead;
import com.kunal.finance.backend.entity.AuditLog;
import com.kunal.finance.backend.entity.FinancialRecord;
import com.kunal.finance.backend.entity.User;
import com.kunal.finance.backend.repository.AuditHeadRepository;
import com.kunal.finance.backend.repository.AuditLogRepository;
import com.kunal.finance.backend.repository.FinancialRecordRepository;
import com.kunal.finance.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Append-only, hash-chained audit ledger. Each entry commits to the previous entry's hash and to a digest of the
 * entity's new state, so editing the log breaks the chain and editing a business row behind the API's back
 * no longer matches the last audited digest.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int BATCH = 500;

    private final AuditLogRepository logs;
    private final AuditHeadRepository heads;
    private final FinancialRecordRepository records;
    private final UserRepository users;

    /** MANDATORY: the audit entry commits or rolls back together with the change it describes. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String actor, String action, String entityType, long entityId, String entityDigest,
            String details) {
        // ponytail: one global row lock serialises audit writes; shard the chain per entity type if write volume demands it
        AuditHead head = heads.lockHead();
        long seq = head.getLastSeq() + 1;
        long now = System.currentTimeMillis();
        String hash = AuditHasher.hash(seq, head.getLastHash(), now, actor, action, entityType, entityId,
                entityDigest, details);
        logs.save(AuditLog.builder().seq(seq).occurredAt(now).actor(actor).action(action).entityType(entityType)
                .entityId(entityId).entityDigest(entityDigest).details(details).prevHash(head.getLastHash())
                .hash(hash).build());
        head.setLastSeq(seq);
        head.setLastHash(hash);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> list(int page, int size) {
        return logs.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "seq")));
    }

    @Transactional(readOnly = true)
    public AuditVerification verify() {
        String prev = AuditHasher.GENESIS;
        long expectedSeq = 1;
        Long broken = null;
        String message = "Chain intact";
        // key "TYPE:id" -> digest after the latest audited change; null means "audited as deleted"
        Map<String, String> expected = new HashMap<>();

        long after = 0;
        outer:
        while (true) {
            List<AuditLog> batch = logs.findBySeqGreaterThanOrderBySeqAsc(after, PageRequest.of(0, BATCH));
            if (batch.isEmpty()) {
                break;
            }
            for (AuditLog e : batch) {
                if (e.getSeq() != expectedSeq) {
                    broken = e.getSeq();
                    message = "Sequence gap: expected " + expectedSeq + " but found " + e.getSeq();
                    break outer;
                }
                if (!e.getPrevHash().equals(prev)) {
                    broken = e.getSeq();
                    message = "Entry " + e.getSeq() + " does not link to the previous entry";
                    break outer;
                }
                String recomputed = AuditHasher.hash(e.getSeq(), e.getPrevHash(), e.getOccurredAt(), e.getActor(),
                        e.getAction(), e.getEntityType(), e.getEntityId(), e.getEntityDigest(), e.getDetails());
                if (!recomputed.equals(e.getHash())) {
                    broken = e.getSeq();
                    message = "Entry " + e.getSeq() + " content was altered after it was written";
                    break outer;
                }
                expected.put(e.getEntityType() + ":" + e.getEntityId(), e.getEntityDigest());
                prev = e.getHash();
                expectedSeq++;
                after = e.getSeq();
            }
        }

        AuditHead head = heads.head();
        long entries = expectedSeq - 1;
        if (broken == null && (head.getLastSeq() != entries || !head.getLastHash().equals(prev))) {
            broken = entries + 1;
            message = "Chain head does not match the log: entries were removed or appended outside the API";
        }

        List<Finding> findings = new ArrayList<>();
        if (broken == null) {
            crossCheck(expected, findings);
        }
        boolean valid = broken == null && findings.isEmpty();
        return new AuditVerification(valid, entries, head.getLastSeq(), head.getLastHash(), broken, message, findings);
    }

    /** Compares live rows against the last audited digest. Skipped when the chain itself is broken (nothing to trust). */
    private void crossCheck(Map<String, String> expected, List<Finding> findings) {
        // ponytail: full scan in 500-row batches; add an incremental checkpoint if the tables grow into millions
        Set<String> seen = new HashSet<>();

        long lastId = 0;
        List<FinancialRecord> rb;
        while (!(rb = records.findByIdGreaterThanOrderByIdAsc(lastId, PageRequest.of(0, BATCH))).isEmpty()) {
            for (FinancialRecord r : rb) {
                String key = "RECORD:" + r.getId();
                seen.add(key);
                compare(key, "RECORD", r.getId(), AuditHasher.recordDigest(r), expected, findings);
                lastId = r.getId();
            }
        }

        // users are few and bounded by admin action; load them in one go
        for (User u : users.findAll()) {
            String key = "USER:" + u.getId();
            seen.add(key);
            compare(key, "USER", u.getId(), AuditHasher.userDigest(u), expected, findings);
        }

        expected.forEach((key, digest) -> {
            if (digest != null && !seen.contains(key)) {
                String[] p = key.split(":");
                findings.add(new Finding(p[0], Long.parseLong(p[1]), "MISSING"));
            }
        });
    }

    private void compare(String key, String type, long id, String actual, Map<String, String> expected,
            List<Finding> findings) {
        if (!expected.containsKey(key)) {
            findings.add(new Finding(type, id, "UNAUDITED"));
        } else if (!actual.equals(expected.get(key))) {
            findings.add(new Finding(type, id, "MODIFIED"));
        }
    }
}
