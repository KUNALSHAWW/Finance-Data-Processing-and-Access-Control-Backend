package com.kunal.finance.backend.audit;

import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.kunal.finance.backend.entity.FinancialRecord;
import com.kunal.finance.backend.entity.User;

/**
 * SHA-256 over length-prefixed fields, so ("ab","c") and ("a","bc") can never collide.
 * Used both for chain hashes and for entity-state digests.
 */
public final class AuditHasher {

    public static final String GENESIS = "0".repeat(64);

    private AuditHasher() {
    }

    public static String hash(Object... fields) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (Object f : fields) {
                byte[] b = f == null ? new byte[0] : String.valueOf(f).getBytes(StandardCharsets.UTF_8);
                sha.update((byte) (f == null ? 0 : 1));
                sha.update(ByteBuffer.allocate(4).putInt(b.length).array());
                sha.update(b);
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 is mandatory in every JRE
        }
    }

    public static String recordDigest(FinancialRecord r) {
        return hash("RECORD", r.getId(), r.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                r.getType(), r.getCategory(), r.getRecordDate(), r.getDescription() == null ? "" : r.getDescription(),
                r.isDeleted(), r.getCreatedBy().getId());
    }

    /** Password hash and lockout counters are deliberately excluded: they change without an admin action. */
    public static String userDigest(User u) {
        return hash("USER", u.getId(), u.getEmail(), u.getName(), u.getRole(), u.isActive());
    }
}
