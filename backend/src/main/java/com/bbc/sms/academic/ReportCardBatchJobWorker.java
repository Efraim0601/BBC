package com.bbc.sms.academic;

import com.bbc.sms.documents.DocumentStorage;
import com.bbc.sms.documents.OfficialDocumentDtos.GeneratedDocumentView;
import com.bbc.sms.documents.OfficialDocumentService;
import com.bbc.sms.academic.dto.AcademicDtos.BulletinSnapshotView;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Runs one student at a time and persists progress after every item. */
@Service
public class ReportCardBatchJobWorker {
    private final JdbcTemplate jdbc;
    private final BulletinSnapshotService snapshots;
    private final ReportCardPdfService pdf;
    private final DocumentStorage storage;
    private final OfficialDocumentService officialDocuments;

    public ReportCardBatchJobWorker(JdbcTemplate jdbc, BulletinSnapshotService snapshots,
                                    ReportCardPdfService pdf, DocumentStorage storage,
                                    OfficialDocumentService officialDocuments) {
        this.jdbc = jdbc; this.snapshots = snapshots; this.pdf = pdf; this.storage = storage;
        this.officialDocuments = officialDocuments;
    }

    @Async("academicBatchExecutor")
    public void start(UUID jobId, UUID schoolId) {
        TenantContext.set(schoolId);
        try {
            run(jobId, schoolId);
        } catch (Exception ex) {
            jdbc.update("""
                    UPDATE bulletin_batch_job
                       SET status='FAILED', completed_at=now(), last_error=?, version=version+1
                     WHERE school_id=? AND id=?
                    """, clip(ex.getMessage()), schoolId, jobId);
        } finally {
            TenantContext.clear();
        }
    }

    public byte[] readArchive(String key) { return storage.read(key); }

    private void run(UUID jobId, UUID schoolId) {
        JobScope scope = jdbc.query("""
                SELECT academic_session_id,reporting_period_id,class_id,locale
                  FROM bulletin_batch_job WHERE school_id=? AND id=?
                """, rs -> rs.next() ? new JobScope(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4)) : null, schoolId, jobId);
        if (scope == null) return;
        jdbc.update("UPDATE bulletin_batch_job SET status='RUNNING',started_at=coalesce(started_at,now()),last_error=NULL,version=version+1 WHERE school_id=? AND id=? AND status IN ('QUEUED','RUNNING')", schoolId, jobId);
        List<Item> items = jdbc.query("""
                SELECT id,student_id FROM bulletin_batch_item
                 WHERE school_id=? AND job_id=? AND status='QUEUED'
                 ORDER BY created_at,id
                """, (rs, row) -> new Item(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), schoolId, jobId);
        for (Item item : items) {
            if (isCancelled(schoolId, jobId)) return;
            processItem(jobId, schoolId, scope, item);
        }
        if (isCancelled(schoolId, jobId)) return;
        buildArchive(jobId, schoolId, scope);
    }

    private boolean isCancelled(UUID schoolId, UUID jobId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT status='CANCELLED' FROM bulletin_batch_job WHERE school_id=? AND id=?",
                Boolean.class, schoolId, jobId));
    }

    private void processItem(UUID jobId, UUID schoolId, JobScope scope, Item item) {
        int attempts = jdbc.queryForObject("SELECT attempts FROM bulletin_batch_item WHERE school_id=? AND id=?", Integer.class, schoolId, item.id());
        jdbc.update("UPDATE bulletin_batch_item SET status='RUNNING',attempts=?,started_at=now(),error=NULL,version=version+1 WHERE school_id=? AND id=? AND status='QUEUED'", attempts + 1, schoolId, item.id());
        try {
            UUID snapshotId = publishedOrValidated(schoolId, item.studentId(), scope.reportingPeriodId(), scope.classId());
            // Draft snapshots are intentionally excluded: only validated/published results enter an archive.
            if (snapshotId == null) {
                markTerminal(schoolId, item.id(), "BLOCKED", null, null, null, null, null, null, null, "No validated or published snapshot");
                refreshCounts(schoolId, jobId);
                return;
            }
            byte[] bytes = pdf.render(snapshotId, "en".equalsIgnoreCase(scope.locale()) ? false : true);
            BulletinSnapshotView snapshot = snapshots.byId(snapshotId);
            String name = snapshot.studentName();
            String fileName = safeFile(name) + "-" + item.studentId().toString().substring(0, 8) + ".pdf";
            String key = storage.store(schoolId.toString(), "bulletin-batch/" + jobId + "/" + item.id(), "pdf", bytes);
            GeneratedDocumentView document = officialDocuments.registerPdf("REPORT_CARD", "BulletinVersion",
                    snapshot.id().toString(), String.valueOf(snapshot.version()), scope.locale(),
                    ("en".equalsIgnoreCase(scope.locale()) ? "School report card" : "Bulletin scolaire") + " - " + name,
                    "PARENT", bytes, "bulletin-batch:" + jobId + ":" + item.id());
            markTerminal(schoolId, item.id(), "PUBLISHED", fileName, key, bytes, snapshot.id(), snapshot.version(),
                    snapshot.snapshotHash(), document.id(), null);
            refreshCounts(schoolId, jobId);
        } catch (Exception ex) {
            markTerminal(schoolId, item.id(), "ERROR", null, null, null, null, null, null, null, clip(ex.getMessage()));
            refreshCounts(schoolId, jobId);
        }
    }

    private UUID publishedOrValidated(UUID schoolId, UUID studentId, UUID periodId, UUID classId) {
        return jdbc.query("""
                SELECT v.id FROM bulletin_version v
                 JOIN student_enrollment e ON e.id=v.enrollment_id
                 WHERE v.school_id=? AND v.student_id=? AND v.reporting_period_id=? AND v.state IN ('PUBLISHED','VALIDATED')
                   AND e.school_id=? AND e.school_class_id=?
                 ORDER BY CASE WHEN v.state='PUBLISHED' THEN 0 ELSE 1 END,
                          v.published_at DESC NULLS LAST,v.created_at DESC LIMIT 1
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                schoolId, studentId, periodId, schoolId, classId);
    }

    private void markTerminal(UUID schoolId, UUID itemId, String status, String fileName, String key, byte[] bytes,
                              UUID snapshotId, Long snapshotVersion, String snapshotHash, UUID documentId, String error) {
        jdbc.update("""
                UPDATE bulletin_batch_item
                   SET status=?,file_name=?,file_storage_key=?,sha256=?,size_bytes=?,error=?,snapshot_id=?,
                       snapshot_version=?,snapshot_hash=?,generated_document_id=?,completed_at=now(),version=version+1
                 WHERE school_id=? AND id=?
                """, status, fileName, key, bytes == null ? null : sha256(bytes), bytes == null ? null : (long) bytes.length,
                error, snapshotId, snapshotVersion, snapshotHash, documentId, schoolId, itemId);
    }

    private void refreshCounts(UUID schoolId, UUID jobId) {
        jdbc.update("""
                UPDATE bulletin_batch_job j SET
                    processed_items=x.processed,
                    published_items=x.published,
                    blocked_items=x.blocked,
                    error_items=x.errors,
                    status=CASE WHEN x.processed < j.total_items THEN 'RUNNING'
                                WHEN x.errors > 0 OR x.blocked > 0 THEN 'COMPLETED_ERRORS'
                                ELSE 'COMPLETED' END,
                    completed_at=CASE WHEN x.processed >= j.total_items THEN now() ELSE j.completed_at END,
                    version=j.version+1
                  FROM (SELECT job_id,
                               count(*) FILTER (WHERE status IN ('PUBLISHED','BLOCKED','ERROR')) AS processed,
                               count(*) FILTER (WHERE status='PUBLISHED') AS published,
                               count(*) FILTER (WHERE status='BLOCKED') AS blocked,
                               count(*) FILTER (WHERE status='ERROR') AS errors
                          FROM bulletin_batch_item WHERE school_id=? AND job_id=? GROUP BY job_id) x
                 WHERE j.school_id=? AND j.id=? AND j.status<>'CANCELLED'
                """, schoolId, jobId, schoolId, jobId);
    }

    private void buildArchive(UUID jobId, UUID schoolId, JobScope scope) {
        List<ArchiveItem> files = jdbc.query("""
                SELECT i.student_id,coalesce(s.last_name||' '||s.first_name,i.student_id::text),i.status,
                       coalesce(i.file_name,''),i.file_storage_key,coalesce(i.sha256,''),coalesce(i.size_bytes,0),coalesce(i.error,''),
                       i.snapshot_id,i.snapshot_version,coalesce(i.snapshot_hash,''),i.generated_document_id
                  FROM bulletin_batch_item i JOIN student s ON s.id=i.student_id
                 WHERE i.school_id=? AND i.job_id=? ORDER BY s.last_name,s.first_name,i.created_at
                """, (rs, row) -> new ArchiveItem(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getLong(7), rs.getString(8),
                        rs.getObject(9, UUID.class), rs.getObject(10, Long.class), rs.getString(11), rs.getObject(12, UUID.class)), schoolId, jobId);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            List<String> manifest = new ArrayList<>();
            manifest.add("student_id,student_name,status,file,sha256,size_bytes,snapshot_id,snapshot_version,snapshot_hash,document_id,error");
            for (ArchiveItem item : files) {
                if (item.fileStorageKey() != null && !item.fileStorageKey().isBlank()) {
                    byte[] bytes = storage.read(item.fileStorageKey());
                    zip.putNextEntry(new ZipEntry(item.fileName())); zip.write(bytes); zip.closeEntry();
                }
                manifest.add(csv(item.studentId().toString(), item.studentName(), item.status(), item.fileName(), item.sha256(), String.valueOf(item.sizeBytes()),
                        String.valueOf(item.snapshotId()), String.valueOf(item.snapshotVersion()), item.snapshotHash(), String.valueOf(item.documentId()), item.error()));
            }
            zip.putNextEntry(new ZipEntry("manifest.csv")); zip.write(String.join("\n", manifest).getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); zip.finish();
            byte[] archive = out.toByteArray();
            String key = storage.store(schoolId.toString(), "bulletin-batch/" + jobId + "/archive", "zip", archive);
            jdbc.update("UPDATE bulletin_batch_job SET archive_storage_key=?,archive_sha256=?,archive_size_bytes=?,version=version+1 WHERE school_id=? AND id=?", key, sha256(archive), (long) archive.length, schoolId, jobId);
        } catch (Exception ex) {
            jdbc.update("UPDATE bulletin_batch_job SET status='FAILED',completed_at=now(),last_error=?,version=version+1 WHERE school_id=? AND id=?", clip(ex.getMessage()), schoolId, jobId);
        }
    }

    private static String csv(String... values) { return Arrays.stream(values).map(v -> "\"" + (v == null ? "" : v.replace("\"", "\"\"")) + "\"").reduce((a,b)->a+","+b).orElse(""); }
    private static String safeFile(String value) { return value == null ? "student" : value.replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("_+", "_"); }
    private static String clip(String value) { return value == null || value.isBlank() ? "Generation failed" : value.replace('\n',' ').replace('\r',' ').substring(0, Math.min(240, value.length())); }
    private static String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private record JobScope(UUID academicSessionId, UUID reportingPeriodId, UUID classId, String locale) {}
    private record Item(UUID id, UUID studentId) {}
    private record ArchiveItem(UUID studentId, String studentName, String status, String fileName, String fileStorageKey, String sha256, long sizeBytes, String error,
                               UUID snapshotId, Long snapshotVersion, String snapshotHash, UUID documentId) {}

}
