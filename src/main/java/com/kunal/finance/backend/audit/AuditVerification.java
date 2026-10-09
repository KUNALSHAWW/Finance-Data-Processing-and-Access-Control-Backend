package com.kunal.finance.backend.audit;

import java.util.List;

/**
 * Result of re-checking the whole ledger.
 *
 * @param valid          chain intact AND every live row matches the last audited state
 * @param headHash       publish this somewhere the database owner cannot rewrite to anchor the chain
 * @param firstBrokenSeq first chain entry that fails verification, or null
 */
public record AuditVerification(boolean valid, long entriesChecked, long headSeq, String headHash,
        Long firstBrokenSeq, String chainMessage, List<Finding> findings) {

    /** problem is MODIFIED (row differs from last audited state), UNAUDITED (row never audited) or MISSING (row gone). */
    public record Finding(String entityType, long entityId, String problem) {
    }
}
