package com.bbc.sms.academic;

import com.bbc.sms.documents.OfficialDocumentDtos.VerificationView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/** Public, deliberately minimal verification surface used by bulletin QR marks. */
@RestController
@RequestMapping("/api/public/report-card-verification")
public class PublicReportCardVerificationController {
    private final JdbcTemplate jdbc;
    public PublicReportCardVerificationController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/{snapshotId}")
    public VerificationView verify(@PathVariable UUID snapshotId,
                                   @RequestParam(required = false) String checksum) {
        return jdbc.query("""
                SELECT b.state,b.snapshot_hash,b.published_at,
                       coalesce(g.document_number,'REPORT_CARD-' || b.id::text)
                  FROM bulletin_version b
                  LEFT JOIN LATERAL (
                       SELECT document_number
                         FROM generated_document
                        WHERE aggregate_type='BulletinVersion'
                          AND aggregate_id=b.id::text
                          AND status='ISSUED'
                        ORDER BY generated_at DESC
                        LIMIT 1
                  ) g ON true
                 WHERE b.id=?
                """,
                rs -> {
                    if (!rs.next()) return new VerificationView(snapshotId.toString(), "REPORT_CARD", null, "NOT_FOUND", null, null, false);
                    String state = rs.getString(1);
                    String hash = rs.getString(2);
                    Instant issued = rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant();
                    String documentNumber = rs.getString(4);
                    boolean validState = "PUBLISHED".equals(state);
                    boolean validChecksum = checksum == null || checksum.isBlank() || checksum.equalsIgnoreCase(hash);
                    return new VerificationView(documentNumber, "REPORT_CARD", "School report card", state,
                            issued, hash, validState && validChecksum);
                }, snapshotId);
    }
}
