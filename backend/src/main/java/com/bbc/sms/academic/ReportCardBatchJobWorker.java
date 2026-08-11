package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.BulletinBatchSnapshotEvidence;
import com.bbc.sms.academic.dto.AcademicDtos.BulletinSnapshotView;
import com.bbc.sms.documents.DocumentStorage;
import com.bbc.sms.documents.OfficialDocumentDtos.GeneratedDocumentView;
import com.bbc.sms.documents.OfficialDocumentService;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Runs ready rows one at a time and persists progress after every item. */
@Service
public class ReportCardBatchJobWorker {
    private final JdbcTemplate jdbc;
    private final ReportCardBatchEligibilityService eligibility;
    private final BulletinSnapshotService snapshots;
    private final ReportCardPdfService pdf;
    private final DocumentStorage storage;
    private final OfficialDocumentService officialDocuments;
    private final ObjectMapper mapper;
    private final AuditService audit;

    public ReportCardBatchJobWorker(JdbcTemplate jdbc, ReportCardBatchEligibilityService eligibility,
                                    BulletinSnapshotService snapshots, ReportCardPdfService pdf,
                                    DocumentStorage storage, OfficialDocumentService officialDocuments,
                                    ObjectMapper mapper, AuditService audit) {
        this.jdbc = jdbc;
        this.eligibility = eligibility;
        this.snapshots = snapshots;
        this.pdf = pdf;
        this.storage = storage;
        this.officialDocuments = officialDocuments;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Async("academicBatchExecutor")
    public void start(UUID jobId, UUID schoolId) {
        TenantContext.set(schoolId);
        try {
            run(jobId, schoolId);
        } catch (Exception ex) {
            String correlationId = UUID.randomUUID().toString();
            jdbc.update("""
                    UPDATE bulletin_batch_job
                       SET status='FAILED', completed_at=now(), last_error=?, version=version+1
                     WHERE school_id=? AND id=?
                    """, "Batch worker failed; reference " + correlationId, schoolId, jobId);
        } finally {
            TenantContext.clear();
        }
    }

    public byte[] readArchive(String key) { return storage.read(key); }

    private void run(UUID jobId, UUID schoolId) {
        JobScope scope = jdbc.query("""
                SELECT j.academic_session_id,j.reporting_period_id,j.class_id,j.locale,coalesce(j.policy,'PUBLISHED_ONLY'),
                       p.code,p.label
                  FROM bulletin_batch_job j
                  JOIN academic_reporting_period p ON p.id=j.reporting_period_id AND p.school_id=j.school_id
                 WHERE j.school_id=? AND j.id=?
                """, rs -> rs.next() ? new JobScope(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)) : null, schoolId, jobId);
        if (scope == null) return;
        jdbc.update("""
                UPDATE bulletin_batch_job
                   SET status='RUNNING',started_at=coalesce(started_at,now()),last_error=NULL,version=version+1
                 WHERE school_id=? AND id=? AND status IN ('QUEUED','RUNNING')
                """, schoolId, jobId);
        List<Item> items = jdbc.query("""
                SELECT id,student_id,snapshot_id,snapshot_version,snapshot_hash,snapshot_published_at
                  FROM bulletin_batch_item
                 WHERE school_id=? AND job_id=? AND status='QUEUED'
                 ORDER BY created_at,id
                """, (rs, row) -> new Item(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), (Long) rs.getObject(4), rs.getString(5),
                        instant(rs.getObject(6, OffsetDateTime.class))), schoolId, jobId);
        for (Item item : items) {
            if (isCancelled(schoolId, jobId)) return;
            processItem(jobId, schoolId, scope, item);
        }
        if (isCancelled(schoolId, jobId)) return;
        buildArtifacts(jobId, schoolId, scope);
    }

    private boolean isCancelled(UUID schoolId, UUID jobId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT status='CANCELLED' FROM bulletin_batch_job WHERE school_id=? AND id=?",
                Boolean.class, schoolId, jobId));
    }

    private void processItem(UUID jobId, UUID schoolId, JobScope scope, Item item) {
        ReportCardBatchEligibilityService.EligibilityRow current = eligibility.resolveForJob(
                schoolId, scope.academicSessionId(), scope.classId(), scope.reportingPeriodId(), item.studentId(), scope.locale());
        if (!"READY".equals(current.eligibility())) {
            if ("TECHNICAL_ERROR".equals(current.category())) {
                markTerminal(schoolId, item.id(), "ERROR", null, null, null, null, null, null, null, current.code(),
                        details(current, UUID.randomUUID().toString()), current.code());
            } else {
                markTerminal(schoolId, item.id(), "BLOCKED", null, null, null, null, null, null, null, current.code(),
                        details(current, null), current.code());
            }
            refreshCounts(schoolId, jobId);
            return;
        }
        if (!sameEvidence(item, current.snapshot())) {
            BulletinBatchResultCode changed = BulletinBatchResultCode.REPORT_PUBLICATION_CHANGED;
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("messageKey", changed.messageKey());
            details.put("messageArgs", current.messageArgs());
            details.put("currentState", "PUBLISHED");
            details.put("retryableNow", false);
            details.put("queuedSnapshotId", item.snapshotId());
            details.put("currentSnapshotId", current.snapshot() == null ? null : current.snapshot().id());
            details.put("snapshotId", current.snapshot() == null ? null : current.snapshot().id());
            markTerminal(schoolId, item.id(), "BLOCKED", null, null, null,
                    current.snapshot() == null ? null : current.snapshot().id(),
                    current.snapshot() == null ? null : current.snapshot().version(),
                    current.snapshot() == null ? null : current.snapshot().hash(), null,
                    changed.name(), details, changed.name());
            refreshCounts(schoolId, jobId);
            return;
        }

        int attempts = jdbc.queryForObject("SELECT attempts FROM bulletin_batch_item WHERE school_id=? AND id=?",
                Integer.class, schoolId, item.id());
        int changed = jdbc.update("""
                UPDATE bulletin_batch_item
                   SET status='RUNNING',attempts=?,started_at=now(),error=NULL,result_code='QUEUED',version=version+1
                 WHERE school_id=? AND id=? AND status='QUEUED'
                """, attempts + 1, schoolId, item.id());
        if (changed == 0) return;

        UUID snapshotId = current.snapshot().id();
        String stage = "PDF";
        try {
            byte[] bytes = pdf.render(snapshotId, !"en".equalsIgnoreCase(scope.locale()));
            ReportCardBatchEligibilityService.EligibilityRow finalState = eligibility.resolveForJob(
                    schoolId, scope.academicSessionId(), scope.classId(), scope.reportingPeriodId(), item.studentId(), scope.locale());
            if (!"READY".equals(finalState.eligibility()) || !sameEvidence(item, finalState.snapshot())) {
                BulletinBatchResultCode code = "TECHNICAL_ERROR".equals(finalState.category())
                        ? BulletinBatchResultCode.from(finalState.code())
                        : BulletinBatchResultCode.REPORT_PUBLICATION_CHANGED;
                if (code == null) code = BulletinBatchResultCode.UNEXPECTED_GENERATION_ERROR;
                Map<String, Object> result = details(finalState, code.businessBlocker() ? null : UUID.randomUUID().toString());
                result.put("messageKey", code.messageKey());
                result.put("messageArgs", finalState.messageArgs());
                result.put("recheckedBeforeRegistration", true);
                result.put("retryableNow", code.retryableByDefault());
                UUID finalSnapshotId = finalState.snapshot() == null ? null : finalState.snapshot().id();
                Long finalSnapshotVersion = finalState.snapshot() == null ? null : finalState.snapshot().version();
                String finalSnapshotHash = finalState.snapshot() == null ? null : finalState.snapshot().hash();
                String status = code.businessBlocker() ? "BLOCKED" : "ERROR";
                markTerminal(schoolId, item.id(), status, null, null, null, finalSnapshotId, finalSnapshotVersion,
                        finalSnapshotHash, null, code.name(), result, code.name());
                refreshCounts(schoolId, jobId);
                return;
            }
            BulletinSnapshotView snapshot = snapshots.byId(snapshotId);
            String name = snapshot.studentName();
            String fileName = safeFile(name) + "-" + item.studentId().toString().substring(0, 8) + ".pdf";
            stage = "STORAGE";
            String key = storage.store(schoolId.toString(), "bulletin-batch/" + jobId + "/" + item.id(), "pdf", bytes);
            stage = "DOCUMENT";
            GeneratedDocumentView document = officialDocuments.registerPdf("REPORT_CARD", "BulletinVersion",
                    snapshot.id().toString(), String.valueOf(snapshot.version()), scope.locale(),
                    ("en".equalsIgnoreCase(scope.locale()) ? "School report card" : "Bulletin scolaire") + " - " + name,
                    "PARENT", bytes, "bulletin-batch:" + jobId + ":" + item.id());
            Map<String, Object> result = details(current, null);
            result.put("currentState", "PUBLISHED");
            result.put("retryableNow", false);
            result.put("documentId", document.id());
            markTerminal(schoolId, item.id(), "PUBLISHED", fileName, key, bytes, snapshot.id(), snapshot.version(),
                    snapshot.snapshotHash(), document.id(), BulletinBatchResultCode.PUBLISHED.name(), result, null);
            refreshCounts(schoolId, jobId);
        } catch (Exception ex) {
            BulletinBatchResultCode code = switch (stage) {
                case "PDF" -> BulletinBatchResultCode.PDF_RENDER_FAILED;
                case "STORAGE" -> BulletinBatchResultCode.STORAGE_FAILED;
                case "DOCUMENT" -> BulletinBatchResultCode.DOCUMENT_REGISTRATION_FAILED;
                default -> BulletinBatchResultCode.UNEXPECTED_GENERATION_ERROR;
            };
            String correlationId = UUID.randomUUID().toString();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("messageKey", code.messageKey());
            result.put("messageArgs", current.messageArgs());
            result.put("currentState", "PUBLISHED");
            result.put("retryableNow", code.retryableByDefault());
            result.put("correlationId", correlationId);
            result.put("stage", stage);
            markTerminal(schoolId, item.id(), "ERROR", null, null, null, null, null, null, null,
                    code.name(), result, clip(ex.getMessage()));
            refreshCounts(schoolId, jobId);
        }
    }

    private void markTerminal(UUID schoolId, UUID itemId, String status, String fileName, String key, byte[] bytes,
                              UUID snapshotId, Long snapshotVersion, String snapshotHash, UUID documentId,
                              String resultCode, Map<String, Object> details, String error) {
        jdbc.update("""
                UPDATE bulletin_batch_item
                   SET status=?,file_name=?,file_storage_key=?,sha256=?,size_bytes=?,error=?,result_code=?,result_details=?::jsonb,
                       snapshot_id=coalesce(?,snapshot_id),snapshot_version=coalesce(?,snapshot_version),
                       snapshot_hash=coalesce(?,snapshot_hash),generated_document_id=?,completed_at=now(),version=version+1
                 WHERE school_id=? AND id=?
                """, status, fileName, key, bytes == null ? null : sha256(bytes), bytes == null ? null : (long) bytes.length,
                error, resultCode, json(details), snapshotId, snapshotVersion, snapshotHash, documentId, schoolId, itemId);
    }

    private void refreshCounts(UUID schoolId, UUID jobId) {
        jdbc.update("""
                UPDATE bulletin_batch_job j SET
                    processed_items=x.processed,published_items=x.published,blocked_items=x.blocked,error_items=x.errors,
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

    private void buildArtifacts(UUID jobId, UUID schoolId, JobScope scope) {
        List<ArchiveItem> items = jdbc.query("""
                SELECT i.student_id,coalesce(s.last_name||' '||s.first_name,i.student_id::text),s.matricule,i.status,i.attempts,
                       coalesce(i.file_name,''),i.file_storage_key,coalesce(i.sha256,''),coalesce(i.size_bytes,0),
                       coalesce(i.error,''),i.result_code,coalesce(i.result_details::text,'{}'),i.snapshot_id,
                       i.snapshot_version,coalesce(i.snapshot_hash,''),i.generated_document_id
                  FROM bulletin_batch_item i JOIN student s ON s.id=i.student_id AND s.school_id=i.school_id
                 WHERE i.school_id=? AND i.job_id=? ORDER BY s.last_name,s.first_name,i.created_at
                """, (rs, row) -> new ArchiveItem(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getInt(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getLong(9),
                        rs.getString(10), rs.getString(11), parseDetails(rs.getString(12)), rs.getObject(13, UUID.class),
                        (Long) rs.getObject(14), rs.getString(15), rs.getObject(16, UUID.class)), schoolId, jobId);

        byte[] diagnostic = diagnosticCsv(items, scope);
        String diagnosticKey = storage.store(schoolId.toString(), "bulletin-batch/" + jobId + "/diagnostic", "csv", diagnostic);
        jdbc.update("""
                UPDATE bulletin_batch_job SET diagnostic_storage_key=?,diagnostic_sha256=?,diagnostic_size_bytes=?,version=version+1
                 WHERE school_id=? AND id=?
                """, diagnosticKey, sha256(diagnostic), (long) diagnostic.length, schoolId, jobId);
        audit.record("BULLETIN_BATCH_DIAGNOSTIC_GENERATED", "BulletinBatchJob", jobId.toString(), null,
                Map.of("sizeBytes", diagnostic.length, "sha256", sha256(diagnostic)), null);

        List<ArchiveItem> successful = items.stream().filter(item -> "PUBLISHED".equals(item.status())
                && item.fileStorageKey() != null && !item.fileStorageKey().isBlank()).toList();
        if (successful.isEmpty()) return;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            List<String> manifest = new ArrayList<>();
            manifest.add("student_id,student_name,status,file,sha256,size_bytes,result_code,snapshot_id,snapshot_version,snapshot_hash,document_id,error");
            for (ArchiveItem item : items) {
                if (item.fileStorageKey() != null && !item.fileStorageKey().isBlank()) {
                    byte[] bytes = storage.read(item.fileStorageKey());
                    zip.putNextEntry(new ZipEntry(item.fileName())); zip.write(bytes); zip.closeEntry();
                }
                manifest.add(csv(item.studentId().toString(), item.studentName(), item.status(), item.fileName(), item.sha256(),
                        String.valueOf(item.sizeBytes()), ReportCardBatchEligibilityService.legacyCode(item.resultCode(), item.error()),
                        String.valueOf(item.snapshotId()), String.valueOf(item.snapshotVersion()), item.snapshotHash(),
                        String.valueOf(item.documentId()), item.error()));
            }
            addCompanionArtifacts(zip, manifest, successful, scope);
            byte[] report = String.join("\n", manifest).getBytes(StandardCharsets.UTF_8);
            zip.putNextEntry(new ZipEntry("manifest.csv")); zip.write(report); zip.closeEntry();
            if (successful.size() < items.size()) {
                zip.putNextEntry(new ZipEntry("generation-report.csv")); zip.write(report); zip.closeEntry();
            }
            zip.finish();
            byte[] archive = out.toByteArray();
            String key = storage.store(schoolId.toString(), "bulletin-batch/" + jobId + "/archive", "zip", archive);
            jdbc.update("""
                    UPDATE bulletin_batch_job SET archive_storage_key=?,archive_sha256=?,archive_size_bytes=?,version=version+1
                     WHERE school_id=? AND id=?
                    """, key, sha256(archive), (long) archive.length, schoolId, jobId);
            audit.record("BULLETIN_BATCH_ARCHIVE_GENERATED", "BulletinBatchJob", jobId.toString(), null,
                    Map.of("publishedItems", successful.size(), "sizeBytes", archive.length, "sha256", sha256(archive)), null);
        } catch (Exception ex) {
            String correlationId = UUID.randomUUID().toString();
            jdbc.update("UPDATE bulletin_batch_job SET status='FAILED',completed_at=now(),last_error=?,version=version+1 WHERE school_id=? AND id=?",
                    "Archive generation failed; reference " + correlationId, schoolId, jobId);
        }
    }

    private void addCompanionArtifacts(ZipOutputStream zip, List<String> manifest,
                                       List<ArchiveItem> items, JobScope scope) throws Exception {
        List<BulletinSnapshotView> frozen = new ArrayList<>();
        for (ArchiveItem item : items) {
            if (item.snapshotId() != null) {
                try { frozen.add(snapshots.byId(item.snapshotId())); } catch (Exception ignored) { }
            }
        }
        if (frozen.isEmpty()) return;
        List<BulletinSnapshotView> honors = frozen.stream().filter(x -> x.conduct() != null && x.conduct().honorRoll()).toList();
        for (BulletinSnapshotView snapshot : honors) {
            String file = "honor-roll/" + safeFile(snapshot.studentName()) + "-certificate.pdf";
            byte[] bytes = companionPdf("en".equalsIgnoreCase(scope.locale()) ? "HONOR ROLL" : "TABLEAU D'HONNEUR",
                    List.of(snapshot.studentName(), "Average: " + number(snapshot.average()) + " / 20"));
            zip.putNextEntry(new ZipEntry(file)); zip.write(bytes); zip.closeEntry();
            manifest.add(companionManifest(file, bytes, "HONOR_CERTIFICATE"));
        }
        List<String> stats = new ArrayList<>(); stats.add("CLASS STATISTICS " + scope.reportingPeriodId());
        if (!frozen.isEmpty() && frozen.get(0).classStats() != null) {
            var x = frozen.get(0).classStats(); stats.add("Average: " + number(x.average()) + " / 20");
            stats.add("Pass rate: " + number(x.successRate()) + "%");
        }
        byte[] statsPdf = companionPdf(stats.get(0), stats.subList(1, stats.size()));
        zip.putNextEntry(new ZipEntry("class-statistics.pdf")); zip.write(statsPdf); zip.closeEntry();
        manifest.add(companionManifest("class-statistics.pdf", statsPdf, "CLASS_STATISTICS"));
        List<String> pv = new ArrayList<>(); pv.add("CLASS PV / REGISTER " + scope.reportingPeriodId());
        frozen.stream().sorted(Comparator.comparing(BulletinSnapshotView::studentName, String.CASE_INSENSITIVE_ORDER))
                .forEach(x -> pv.add(x.studentName() + " | " + number(x.average()) + " | " + String.valueOf(x.rank())));
        byte[] pvPdf = companionPdf(pv.get(0), pv.subList(1, pv.size()));
        zip.putNextEntry(new ZipEntry("pv-register.pdf")); zip.write(pvPdf); zip.closeEntry();
        manifest.add(companionManifest("pv-register.pdf", pvPdf, "PV_REGISTER"));
    }

    private byte[] diagnosticCsv(List<ArchiveItem> items, JobScope scope) {
        boolean fr = !"en".equalsIgnoreCase(scope.locale());
        List<String> lines = new ArrayList<>();
        lines.add("student_id,student_name,status,attempts,result_code,category,current_state,retryable_now,message,repair_scope,snapshot_id,snapshot_version,snapshot_hash,document_id");
        for (ArchiveItem item : items) {
            String code = "PUBLISHED".equals(item.status()) ? "PUBLISHED" : ReportCardBatchEligibilityService.legacyCode(item.resultCode(), item.error());
            BulletinBatchResultCode known = BulletinBatchResultCode.from(code);
            String state = String.valueOf(item.details().getOrDefault("currentState", ""));
            boolean retryable = Boolean.TRUE.equals(item.details().get("retryableNow"));
            String message = friendly(code, scope.reportingPeriodCode(), item.studentName(), fr);
            String repair = known != null && known.businessBlocker()
                    ? "/academic?mode=bulletin&classId=" + scope.classId() + "&reportingPeriodId=" + scope.reportingPeriodId() + "&studentId=" + item.studentId() : "";
            lines.add(csv(item.studentId().toString(), item.studentName(), item.status(), String.valueOf(item.attempts()), code,
                    known == null ? "" : known.category(), state, String.valueOf(retryable), message, repair,
                    String.valueOf(item.snapshotId()), String.valueOf(item.snapshotVersion()), item.snapshotHash(), String.valueOf(item.documentId())));
        }
        return String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    private String friendly(String code, String period, String student, boolean fr) {
        return switch (code == null ? "" : code) {
            case "REPORT_NOT_CREATED", "REPORT_NOT_PUBLISHED_LEGACY" -> fr ? "Aucun bulletin publié pour " + student + " dans la période sélectionnée." : "No published report card for " + student + " in the selected period.";
            case "REPORT_DRAFT" -> fr ? "Le bulletin est encore en brouillon." : "The report card is still a draft.";
            case "REPORT_RETURNED" -> fr ? "Le bulletin a été retourné pour correction." : "The report card was returned for correction.";
            case "REPORT_VALIDATED_NOT_PUBLISHED" -> fr ? "Le bulletin est validé mais pas publié." : "The report card is validated but not published.";
            case "REPORT_STALE" -> fr ? "Le bulletin doit être actualisé." : "The report card must be refreshed.";
            case "PDF_RENDER_FAILED" -> fr ? "Le PDF n'a pas pu être créé; une relance technique est possible." : "The PDF could not be created; a technical retry is available.";
            case "STORAGE_FAILED" -> fr ? "Le stockage du PDF a échoué; une relance technique est possible." : "PDF storage failed; a technical retry is available.";
            case "DOCUMENT_REGISTRATION_FAILED" -> fr ? "L'enregistrement officiel a échoué; une relance idempotente est possible." : "Official registration failed; an idempotent retry is available.";
            default -> fr ? "Consultez la référence technique associée à ce résultat." : "Consult the technical reference for this result.";
        };
    }

    private Map<String, Object> details(ReportCardBatchEligibilityService.EligibilityRow row, String correlationId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("messageKey", row.messageKey()); details.put("messageArgs", row.messageArgs());
        details.put("currentState", row.currentState()); details.put("retryableNow", row.retryableNow());
        if (correlationId != null) details.put("correlationId", correlationId);
        if (row.snapshot() != null) {
            details.put("snapshotId", row.snapshot().id()); details.put("snapshotVersion", row.snapshot().version());
            details.put("snapshotHash", row.snapshot().hash()); details.put("snapshotPublishedAt", row.snapshot().publishedAt());
        }
        return details;
    }

    private static boolean sameEvidence(Item item, BulletinBatchSnapshotEvidence evidence) {
        // Legacy queued rows may predate frozen evidence. They are allowed to
        // capture the current published candidate once; rows created by the
        // new path always carry evidence and must match it exactly.
        if (item.snapshotId() == null) return evidence != null;
        return evidence != null && item.snapshotId().equals(evidence.id())
                && item.snapshotVersion() != null && item.snapshotVersion() == evidence.version()
                && java.util.Objects.equals(item.snapshotHash(), evidence.hash())
                && java.util.Objects.equals(item.snapshotPublishedAt(), evidence.publishedAt());
    }

    private static String companionManifest(String file, byte[] bytes, String kind) {
        return csv("", kind, "COMPANION", file, sha256(bytes), String.valueOf(bytes.length), "", "", "", "", "");
    }
    private static byte[] companionPdf(String title, List<String> lines) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4); document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, 15); cs.newLineAtOffset(52, 790);
                cs.showText(pdfSafe(title)); cs.setFont(PDType1Font.HELVETICA, 10);
                for (String line : lines) { cs.newLineAtOffset(0, -18); cs.showText(pdfSafe(line)); }
                cs.endText();
            }
            document.save(out); return out.toByteArray();
        } catch (Exception ex) { throw new IllegalStateException("Companion document generation failed", ex); }
    }
    private static String pdfSafe(String value) { return value == null ? "" : value.replace('é','e').replace('è','e').replace('ê','e').replace('à','a').replace('ù','u').replace('ô','o').replace('î','i').replace('ç','c'); }
    private static String number(java.math.BigDecimal value) { return value == null ? "-" : value.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    private static String csv(String... values) { return Arrays.stream(values).map(v -> "\"" + (v == null ? "" : v.replace("\"", "\"\"")) + "\"").reduce((a,b)->a+","+b).orElse(""); }
    private static String safeFile(String value) { return value == null ? "student" : value.replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("_+", "_"); }
    private static String clip(String value) { return value == null || value.isBlank() ? "Generation failed" : value.replace('\n',' ').replace('\r',' ').substring(0, Math.min(240, value.length())); }
    private static String sha256(byte[] bytes) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private static Instant instant(OffsetDateTime value) { return value == null ? null : value.toInstant(); }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (JsonProcessingException ex) { throw new IllegalStateException(ex); } }
    private Map<String, Object> parseDetails(String value) { try { return mapper.readValue(value == null ? "{}" : value, new TypeReference<>() {}); } catch (Exception ex) { return new LinkedHashMap<>(); } }

    private record JobScope(UUID academicSessionId, UUID reportingPeriodId, UUID classId, String locale, String policy,
                            String reportingPeriodCode, String reportingPeriodLabel) {}
    private record Item(UUID id, UUID studentId, UUID snapshotId, Long snapshotVersion, String snapshotHash, Instant snapshotPublishedAt) {}
    private record ArchiveItem(UUID studentId, String studentName, String matricule, String status, int attempts, String fileName,
                               String fileStorageKey, String sha256, long sizeBytes, String error, String resultCode,
                               Map<String, Object> details, UUID snapshotId, Long snapshotVersion,
                               String snapshotHash, UUID documentId) {}
}
