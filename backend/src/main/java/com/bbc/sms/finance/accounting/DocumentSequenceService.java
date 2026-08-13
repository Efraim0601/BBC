package com.bbc.sms.finance.accounting;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/** Allocates financial numbers while holding a row lock for only the increment. */
@Service
public class DocumentSequenceService {
    private final JdbcTemplate jdbc;

    public DocumentSequenceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public String allocate(String documentType, String periodKey, String prefix, int padding) {
        UUID schoolId = TenantContext.get();
        String type = normalize(documentType, 48);
        String key = normalize(periodKey, 32);
        String safePrefix = prefix == null ? "" : prefix.trim();
        if (safePrefix.length() > 80) throw ApiException.badRequest("Préfixe de séquence trop long.");
        int safePadding = Math.max(1, Math.min(padding, 12));
        jdbc.update("""
                INSERT INTO document_sequence(school_id, document_type, period_key, prefix, next_number, padding)
                VALUES (?,?,?,?,1,?)
                ON CONFLICT (school_id, document_type, period_key) DO NOTHING
                """, schoolId, type, key, safePrefix, safePadding);
        SequenceRow row = jdbc.queryForObject("""
                SELECT prefix, next_number, padding
                  FROM document_sequence
                 WHERE school_id=? AND document_type=? AND period_key=?
                 FOR UPDATE
                """, (rs, n) -> new SequenceRow(rs.getString(1), rs.getLong(2), rs.getInt(3)),
                schoolId, type, key);
        if (row == null) throw new IllegalStateException("Document sequence row was not created");
        if (row.nextNumber() < 1) throw ApiException.conflict("La séquence documentaire est épuisée.");
        jdbc.update("""
                UPDATE document_sequence SET next_number=next_number+1, updated_at=now()
                 WHERE school_id=? AND document_type=? AND period_key=?
                """, schoolId, type, key);
        return row.prefix() + String.format(Locale.ROOT, "%0" + row.padding() + "d", row.nextNumber());
    }

    @Transactional
    public String allocateJournalNumber(String periodKey) {
        String key = normalize(periodKey, 32);
        return allocate("JOURNAL", key, "JRN/" + key + "/", 6);
    }

    private static String normalize(String value, int max) {
        if (value == null || value.isBlank()) throw ApiException.badRequest("Clé de séquence obligatoire.");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > max) throw ApiException.badRequest("Clé de séquence trop longue.");
        return normalized;
    }

    private record SequenceRow(String prefix, long nextNumber, int padding) {}
}
