package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.documents.DocumentStorage;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.session.AcademicWindowPolicyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Creates, exposes and recovers durable official report-card batch jobs. */
@Service
public class ReportCardBatchJobService {
    private final JdbcTemplate jdbc;
    private final TeacherScopeService teacherScope;
    private final ReportCardBatchEligibilityService eligibility;
    private final ReportCardBatchJobWorker worker;
    private final DocumentStorage storage;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final AcademicWindowPolicyService windows;

    public ReportCardBatchJobService(JdbcTemplate jdbc, TeacherScopeService teacherScope,
                                     ReportCardBatchEligibilityService eligibility,
                                     ReportCardBatchJobWorker worker, DocumentStorage storage,
                                     ObjectMapper mapper, AuditService audit,
                                     AcademicWindowPolicyService windows) {
        this.jdbc = jdbc;
        this.teacherScope = teacherScope;
        this.eligibility = eligibility;
        this.worker = worker;
        this.storage = storage;
        this.mapper = mapper;
        this.audit = audit;
        this.windows = windows;
    }

    @Transactional(readOnly = true)
    public BulletinBatchPreviewView preview(BulletinBatchPreviewRequest request) {
        if (request == null) throw ApiException.badRequest("La classe et la période de résultat sont obligatoires.");
        ReportCardBatchEligibilityService.EligibilityPreview preview = request.reportingPeriodIds() == null || request.reportingPeriodIds().isEmpty()
                ? eligibility.preview(request.classId(), request.reportingPeriodId(), request.locale())
                : eligibility.preview(request.classId(), request.periodIds(), request.locale());
        return preview.view();
    }

    @Transactional
    public BulletinBatchJobView create(BulletinBatchJobCreateRequest request) {
        if (request == null || request.classId() == null || request.periodIds().isEmpty()) {
            throw ApiException.fields(org.springframework.http.HttpStatus.BAD_REQUEST, "BATCH_SCOPE_REQUIRED",
                    "La classe et la période de résultat sont obligatoires.",
                    Map.of(request == null || request.classId() == null ? "classId" : "reportingPeriodIds",
                            request == null || request.classId() == null ? "La classe est obligatoire." : "La période est obligatoire."));
        }
        ReportCardBatchEligibilityService.EligibilityPreview preview = request.reportingPeriodIds() == null || request.reportingPeriodIds().isEmpty()
                ? eligibility.preview(request.classId(), request.reportingPeriodId(), request.locale())
                : eligibility.preview(request.classId(), request.periodIds(), request.locale());
        List<AcademicWindowPolicyService.WindowView> authorizations = request.periodIds().stream()
                .map(periodId -> windows.assertAllowed(periodId, AcademicWindowPolicyService.Action.BATCH_GENERATION)).toList();
        AcademicWindowPolicyService.WindowView authorization = authorizations.get(0);
        if (request.scopeFingerprint() != null && !request.scopeFingerprint().isBlank()
                && !request.scopeFingerprint().equals(preview.scopeFingerprint())) {
            throw batchConflict("BATCH_SCOPE_CHANGED", "La préparation du lot a changé. Vérifiez à nouveau les élèves prêts.", preview);
        }
        if (preview.readyStudents() == 0) {
            throw batchConflict("BATCH_NOT_READY", "Aucun bulletin publié n'est prêt pour ce lot.", preview);
        }
        if (preview.blockedStudents() > 0 && !request.includeReadyStudents()) {
            throw batchConflict("BATCH_PARTIALLY_READY", "Certains élèves ne disposent pas encore d'un bulletin publié.", preview);
        }

        UUID schoolId = TenantContext.get();
        String locale = "en".equalsIgnoreCase(request.locale()) ? "en" : "fr";
        lockScope(schoolId, request.classId(), request.periodIds().get(0), preview.scopeFingerprint());
        UUID activeJob = activeJob(schoolId, request.classId(), request.periodIds().get(0), preview.scopeFingerprint());
        if (activeJob != null) return view(activeJob);

        UUID id = UUID.randomUUID();
        int blocked = preview.blockedStudents();
        jdbc.update("""
                INSERT INTO bulletin_batch_job
                    (id,school_id,academic_session_id,reporting_period_id,class_id,locale,policy,scope_fingerprint,product_set,product_fingerprint,window_authorization,
                     status,total_items,processed_items,blocked_items,requested_by)
                VALUES (?,?,?,?,?,?,?,?,?::jsonb,?,?::jsonb,'QUEUED',?,?,?,?)
                """, id, schoolId, preview.academicSessionId(), preview.reportingPeriodId(), preview.classId(), locale,
                ReportCardBatchEligibilityService.POLICY, preview.scopeFingerprint(), json(productSet(preview)), preview.scopeFingerprint(), json(windowDetails(authorization)), preview.totalStudents(), blocked,
                blocked, currentUserId());
        for (ReportCardBatchEligibilityService.EligibilityRow row : preview.rows()) {
            boolean ready = "READY".equals(row.eligibility());
            BulletinBatchSnapshotEvidence evidence = row.snapshot();
            jdbc.update("""
                    INSERT INTO bulletin_batch_item
                        (id,school_id,job_id,student_id,reporting_period_id,reporting_period_code,reporting_period_label,product_code,status,attempts,error,result_code,result_details,
                         snapshot_id,snapshot_version,snapshot_hash,snapshot_published_at)
                    VALUES (?,?,?,?,?,?,?, ?, ?,0, ?, ?, ?::jsonb, ?,?,?,?)
                    """, UUID.randomUUID(), schoolId, id, row.studentId(), row.reportingPeriodId(), row.reportingPeriodCode(), row.reportingPeriodLabel(), row.product(), ready ? "QUEUED" : "BLOCKED",
                    ready ? null : row.code(), ready ? "QUEUED" : row.code(), json(details(row)),
                    evidence == null ? null : evidence.id(), evidence == null ? null : evidence.version(),
                    evidence == null ? null : evidence.hash(), evidence == null ? null : timestamp(evidence.publishedAt()));
        }
        audit.record("BULLETIN_BATCH_REQUESTED", "BulletinBatchJob", id.toString(), null,
                Map.of("policy", ReportCardBatchEligibilityService.POLICY,
                        "classId", preview.classId(), "reportingPeriodId", preview.reportingPeriodId(),
                        "scopeFingerprint", preview.scopeFingerprint(), "readyCount", preview.readyStudents(),
                        "blockedCount", preview.blockedStudents()), null);
        if (preview.readyStudents() > 0) startAfterCommit(id, schoolId);
        return view(id);
    }

    @Transactional(readOnly = true)
    public BulletinBatchJobView view(UUID id) {
        JobRow job = job(id);
        teacherScope.assertClass(job.classId());
        return toView(job);
    }

    @Transactional(readOnly = true)
    public List<BulletinBatchJobView> list(UUID classId, UUID periodId) {
        teacherScope.assertClass(classId);
        UUID schoolId = TenantContext.get();
        return jdbc.query("""
                SELECT id FROM bulletin_batch_job
                 WHERE school_id=? AND class_id=? AND reporting_period_id=?
                 ORDER BY requested_at DESC LIMIT 20
                """, (rs, row) -> view(rs.getObject(1, UUID.class)), schoolId, classId, periodId);
    }

    @Transactional(readOnly = true)
    public List<BulletinBatchItemView> items(UUID id) {
        JobRow job = job(id);
        teacherScope.assertClass(job.classId());
        return itemViews(job);
    }

    /** Re-evaluate business blockers without increasing their PDF attempt count. */
    @Transactional
    public BulletinBatchJobView recheckBlocked(UUID id, UUID itemId) {
        JobRow job = job(id);
        teacherScope.assertClass(job.classId());
        if (Set.of("RUNNING", "QUEUED").contains(job.status())) {
            throw ApiException.conflict("La génération est déjà en cours; vérifiez son état après son actualisation.");
        }
        windows.assertAllowed(job.reportingPeriodId(), AcademicWindowPolicyService.Action.BATCH_GENERATION);
        List<ItemRow> candidates = itemRows(job).stream()
                .filter(row -> "BLOCKED".equals(row.status()) && (itemId == null || itemId.equals(row.id())))
                .toList();
        if (candidates.isEmpty()) throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT,
                "BATCH_BLOCKED_RECHECK_NOT_APPLICABLE", "Aucun élève bloqué ne peut être revérifié.");
        int becameReady = 0;
        int remainedBlocked = 0;
        int becameTechnical = 0;
        for (ItemRow item : candidates) {
            ReportCardBatchEligibilityService.EligibilityRow result = eligibility.resolveForJob(
                    TenantContext.get(), job.academicSessionId(), job.classId(), job.reportingPeriodId(), item.studentId(), job.locale());
            if ("READY".equals(result.eligibility())) {
                BulletinBatchSnapshotEvidence evidence = result.snapshot();
                jdbc.update("""
                        UPDATE bulletin_batch_item
                           SET status='QUEUED', error=NULL, result_code='QUEUED', result_details=?::jsonb,
                               file_name=NULL,file_storage_key=NULL,sha256=NULL,size_bytes=NULL,
                               snapshot_id=?,snapshot_version=?,snapshot_hash=?,snapshot_published_at=?,
                               started_at=NULL,completed_at=NULL,version=version+1
                         WHERE school_id=? AND id=? AND status='BLOCKED'
                        """, json(details(result)), evidence == null ? null : evidence.id(),
                        evidence == null ? null : evidence.version(), evidence == null ? null : evidence.hash(),
                        evidence == null ? null : timestamp(evidence.publishedAt()), TenantContext.get(), item.id());
                becameReady++;
            } else if ("TECHNICAL_ERROR".equals(result.category())) {
                jdbc.update("""
                        UPDATE bulletin_batch_item
                           SET status='ERROR', error=?, result_code=?, result_details=?::jsonb, version=version+1
                         WHERE school_id=? AND id=? AND status='BLOCKED'
                        """, result.code(), result.code(), json(details(result)), TenantContext.get(), item.id());
                becameTechnical++;
            } else {
                jdbc.update("""
                        UPDATE bulletin_batch_item
                           SET result_code=?, result_details=?::jsonb, error=?, version=version+1
                         WHERE school_id=? AND id=? AND status='BLOCKED'
                        """, result.code(), json(details(result)), result.code(), TenantContext.get(), item.id());
                remainedBlocked++;
            }
        }
        refreshCounters(job.id(), becameReady > 0 ? "QUEUED" : job.status());
        audit.record("BULLETIN_BATCH_BLOCKER_RECHECKED", "BulletinBatchJob", id.toString(), null,
                Map.of("becameReady", becameReady, "stillBlocked", remainedBlocked,
                        "becameTechnical", becameTechnical), null);
        if (becameReady > 0) startAfterCommit(id, TenantContext.get());
        return view(id);
    }

    /** Retry only retryable technical errors; business blockers are untouched. */
    @Transactional
    public BulletinBatchJobView retryErrors(UUID id, UUID itemId) {
        JobRow job = job(id);
        teacherScope.assertClass(job.classId());
        if (Set.of("RUNNING", "QUEUED").contains(job.status())) {
            throw ApiException.conflict("La génération est déjà en cours.");
        }
        windows.assertAllowed(job.reportingPeriodId(), AcademicWindowPolicyService.Action.BATCH_GENERATION);
        List<ItemRow> candidates = itemRows(job).stream()
                .filter(row -> "ERROR".equals(row.status()) && row.retryableNow()
                        && (itemId == null || itemId.equals(row.id())))
                .toList();
        if (candidates.isEmpty()) throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT,
                "BATCH_TECHNICAL_RETRY_NOT_APPLICABLE", "Aucune erreur technique relançable n'est disponible.");
        for (ItemRow item : candidates) {
            Map<String, Object> retryDetails = new LinkedHashMap<>();
            retryDetails.put("retryOf", item.resultCode());
            retryDetails.put("previousAttempts", item.attempts());
            retryDetails.put("retryableNow", true);
            jdbc.update("""
                    UPDATE bulletin_batch_item
                       SET status='QUEUED', error=NULL, result_code='QUEUED', result_details=?::jsonb,
                           file_name=NULL,file_storage_key=NULL,sha256=NULL,size_bytes=NULL,
                           started_at=NULL,completed_at=NULL,version=version+1
                     WHERE school_id=? AND id=? AND status='ERROR'
                    """, json(retryDetails), TenantContext.get(), item.id());
        }
        refreshCounters(job.id(), "QUEUED");
        audit.record("BULLETIN_BATCH_TECHNICAL_RETRY", "BulletinBatchJob", id.toString(), null,
                Map.of("itemCount", candidates.size()), null);
        startAfterCommit(id, TenantContext.get());
        return view(id);
    }

    /** Compatibility route: old clients now retry technical errors only. */
    @Transactional
    public BulletinBatchJobView retry(UUID id, UUID itemId) { return retryErrors(id, itemId); }

    @Transactional
    public BulletinBatchJobView cancel(UUID id, BulletinBatchCancelRequest request) {
        JobRow job = job(id);
        teacherScope.assertClass(job.classId());
        if (Set.of("COMPLETED", "COMPLETED_ERRORS", "FAILED", "CANCELLED").contains(job.status())) {
            throw ApiException.conflict("Cette génération est déjà terminée ou annulée.");
        }
        int changed = jdbc.update("""
                UPDATE bulletin_batch_job
                   SET status='CANCELLED',cancelled_at=now(),cancelled_by=?,cancel_reason=?,version=version+1
                 WHERE school_id=? AND id=? AND status IN ('QUEUED','RUNNING')
                """, currentUserId(), request.reason().trim(), TenantContext.get(), id);
        if (changed == 0) throw ApiException.conflict("La génération a changé entre-temps.");
        audit.record("BULLETIN_BATCH_CANCELLED", "BulletinBatchJob", id.toString(), null,
                Map.of("reason", request.reason().trim()), request.reason().trim());
        return view(id);
    }

    @Transactional(readOnly = true)
    public byte[] archive(UUID id) {
        JobRow job = job(id);
        teacherScope.assertClass(job.classId());
        if (job.publishedItems() == 0 || job.archiveStorageKey() == null || job.archiveStorageKey().isBlank()) {
            throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT,
                    "BATCH_STUDENT_ARCHIVE_UNAVAILABLE", "Aucun PDF de bulletin publié n'est disponible dans ce lot.");
        }
        return worker.readArchive(job.archiveStorageKey());
    }

    @Transactional
    public byte[] diagnostic(UUID id) {
        JobRow job = job(id);
        teacherScope.assertClass(job.classId());
        if (job.diagnosticStorageKey() != null && !job.diagnosticStorageKey().isBlank()) {
            return worker.readArchive(job.diagnosticStorageKey());
        }
        byte[] csv = diagnosticCsv(job, itemRows(job));
        String key = storage.store(TenantContext.get().toString(), "bulletin-batch/" + id + "/diagnostic", "csv", csv);
        jdbc.update("""
                UPDATE bulletin_batch_job
                   SET diagnostic_storage_key=?,diagnostic_sha256=?,diagnostic_size_bytes=?,version=version+1
                 WHERE school_id=? AND id=?
                """, key, sha256(csv), (long) csv.length, TenantContext.get(), id);
        audit.record("BULLETIN_BATCH_DIAGNOSTIC_GENERATED", "BulletinBatchJob", id.toString(), null,
                Map.of("sizeBytes", csv.length, "sha256", sha256(csv)), null);
        return csv;
    }

    private void refreshCounters(UUID id, String preferredStatus) {
        jdbc.update("""
                UPDATE bulletin_batch_job j SET
                    processed_items=x.processed,published_items=x.published,blocked_items=x.blocked,error_items=x.errors,
                    status=CASE WHEN ?='QUEUED' AND x.queued > 0 THEN 'QUEUED'
                                WHEN x.processed < j.total_items THEN 'RUNNING'
                                WHEN x.errors > 0 OR x.blocked > 0 THEN 'COMPLETED_ERRORS'
                                ELSE 'COMPLETED' END,
                    completed_at=CASE WHEN ?='QUEUED' AND x.queued > 0 THEN NULL
                                      WHEN x.processed >= j.total_items THEN now() ELSE j.completed_at END,
                    version=j.version+1
                  FROM (SELECT job_id,
                               count(*) FILTER (WHERE status IN ('PUBLISHED','BLOCKED','ERROR')) AS processed,
                               count(*) FILTER (WHERE status='PUBLISHED') AS published,
                               count(*) FILTER (WHERE status='BLOCKED') AS blocked,
                               count(*) FILTER (WHERE status='ERROR') AS errors,
                               count(*) FILTER (WHERE status='QUEUED') AS queued
                          FROM bulletin_batch_item WHERE school_id=? AND job_id=? GROUP BY job_id) x
                 WHERE j.school_id=? AND j.id=? AND j.status<>'CANCELLED'
                """, preferredStatus, preferredStatus, TenantContext.get(), id, TenantContext.get(), id);
    }

    private ApiException batchConflict(String code, String message,
                                       ReportCardBatchEligibilityService.EligibilityPreview preview) {
        return ApiException.conflictWithDetails(code, message, Map.of("preview", preview.view()));
    }

    private UUID activeJob(UUID schoolId, UUID classId, UUID periodId, String fingerprint) {
        List<UUID> rows = jdbc.query("""
                SELECT id FROM bulletin_batch_job
                 WHERE school_id=? AND class_id=? AND reporting_period_id=? AND scope_fingerprint=?
                   AND status IN ('QUEUED','RUNNING') ORDER BY requested_at DESC LIMIT 1
                """, (rs, n) -> rs.getObject(1, UUID.class), schoolId, classId, periodId, fingerprint);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Serialize same-scope creates inside the database transaction across app instances. */
    private void lockScope(UUID schoolId, UUID classId, UUID periodId, String fingerprint) {
        String key = schoolId + "|" + classId + "|" + periodId + "|" + fingerprint;
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, key);
    }

    private void startAfterCommit(UUID id, UUID schoolId) {
        Runnable start = () -> worker.start(id, schoolId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { start.run(); }
            });
        } else start.run();
    }

    private JobRow job(UUID id) {
        JobRow row = jdbc.query("""
                SELECT j.id,j.school_id,j.academic_session_id,j.reporting_period_id,j.class_id,j.locale,j.status,
                       j.total_items,j.processed_items,j.published_items,j.blocked_items,j.error_items,
                       j.requested_by,j.requested_at,j.started_at,j.completed_at,j.archive_storage_key,
                       j.archive_sha256,j.archive_size_bytes,j.last_error,j.version,
                       coalesce(j.policy,'PUBLISHED_ONLY'),j.scope_fingerprint,j.diagnostic_storage_key,
                       j.diagnostic_sha256,j.diagnostic_size_bytes,coalesce(j.window_authorization,'{}'::jsonb)::text,
                       p.code,p.label,c.name
                  FROM bulletin_batch_job j
                  JOIN academic_reporting_period p ON p.id=j.reporting_period_id AND p.school_id=j.school_id
                  JOIN school_class c ON c.id=j.class_id AND c.school_id=j.school_id
                 WHERE j.school_id=? AND j.id=?
                """, rs -> rs.next() ? new JobRow(
                        rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7),
                        rs.getInt(8), rs.getInt(9), rs.getInt(10), rs.getInt(11), rs.getInt(12),
                        rs.getObject(13, UUID.class), rs.getObject(14, OffsetDateTime.class),
                        rs.getObject(15, OffsetDateTime.class), rs.getObject(16, OffsetDateTime.class), rs.getString(17),
                        rs.getString(18), (Long) rs.getObject(19), rs.getString(20), rs.getLong(21),
                         rs.getString(22), rs.getString(23), rs.getString(24), rs.getString(25),
                         (Long) rs.getObject(26), rs.getString(27), rs.getString(28), rs.getString(29),
                         rs.getString(30)) : null,
                TenantContext.get(), id);
        if (row == null) throw ApiException.notFound("Lot de génération des bulletins");
        return row;
    }

    private BulletinBatchJobView toView(JobRow job) {
        List<BulletinBatchItemView> items = itemViews(job);
        int progress = job.totalItems() == 0 ? 0 : Math.min(100, (job.processedItems() * 100) / job.totalItems());
        String category = resultCategory(job);
        String headlineCode = "BATCH_" + category;
        Map<String, Object> headlineArgs = new LinkedHashMap<>();
        headlineArgs.put("periodCode", job.periodCode());
        headlineArgs.put("published", job.publishedItems());
        headlineArgs.put("blocked", job.blockedItems());
        headlineArgs.put("errors", job.errorItems());
        headlineArgs.put("total", job.totalItems());
        List<BulletinBatchReasonCount> reasonCounts = items.stream()
                .filter(item -> item.resultCode() != null && !"PUBLISHED".equals(item.resultCode()) && !"QUEUED".equals(item.resultCode()))
                .collect(Collectors.groupingBy(BulletinBatchItemView::resultCode, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().map(entry -> new BulletinBatchReasonCount(entry.getKey(), entry.getValue().intValue())).toList();
        boolean archive = job.publishedItems() > 0 && job.archiveStorageKey() != null && !job.archiveStorageKey().isBlank();
        boolean diagnostic = job.totalItems() > 0 && (job.completedAt() != null || job.diagnosticStorageKey() != null);
        int retryable = (int) items.stream().filter(item -> "ERROR".equals(item.status()) && item.retryableNow()).count();
        int nowEligible = (int) items.stream().filter(item -> "BLOCKED".equals(item.status()) && item.retryableNow()).count();
        int stillBlocked = job.blockedItems() - nowEligible;
        List<UUID> periodIds = items.stream().map(BulletinBatchItemView::reportingPeriodId).filter(java.util.Objects::nonNull).distinct().toList();
        List<String> products = items.stream().map(BulletinBatchItemView::product).filter(java.util.Objects::nonNull).distinct().toList();
        return new BulletinBatchJobView(job.id(), job.academicSessionId(), job.reportingPeriodId(), job.classId(), job.locale(),
                job.status(), job.totalItems(), job.processedItems(), job.publishedItems(), job.blockedItems(), job.errorItems(),
                progress, job.requestedAt(), job.startedAt(), job.completedAt(), archive, job.archiveSha256(), job.archiveSizeBytes(),
                job.lastError(), job.version(), job.policy(), job.scopeFingerprint(), category, headlineCode, headlineArgs,
                 reasonCounts, archive, diagnostic, retryable, nowEligible, Math.max(0, stillBlocked),
                 job.diagnosticSha256(), job.diagnosticSizeBytes(), currentWindow(job), periodIds, products,
                 List.of(currentWindow(job)));
    }

    private String resultCategory(JobRow job) {
        if ("CANCELLED".equals(job.status())) return "CANCELLED";
        if (Set.of("QUEUED", "RUNNING").contains(job.status())) return "RUNNING";
        if (job.publishedItems() == job.totalItems() && job.blockedItems() == 0 && job.errorItems() == 0) return "SUCCESS";
        if (job.publishedItems() > 0 && (job.blockedItems() > 0 || job.errorItems() > 0)) return "PARTIAL";
        if (job.publishedItems() == 0 && job.blockedItems() > 0 && job.errorItems() == 0) return "BLOCKED";
        return "FAILED";
    }

    private List<BulletinBatchItemView> itemViews(JobRow job) {
        return itemRows(job).stream().map(item -> itemView(item, job)).toList();
    }

    private List<ItemRow> itemRows(JobRow job) {
        return jdbc.query("""
                SELECT i.id,i.student_id,coalesce(s.last_name||' '||s.first_name,i.student_id::text),i.status,
                       i.reporting_period_id,i.reporting_period_code,i.reporting_period_label,i.product_code,
                       i.attempts,coalesce(i.file_name,''),coalesce(i.size_bytes,0),coalesce(i.error,''),
                       i.result_code,coalesce(i.result_details::text,'{}'),i.snapshot_id,i.snapshot_version,
                       i.snapshot_hash,i.snapshot_published_at
                  FROM bulletin_batch_item i
                  JOIN student s ON s.id=i.student_id AND s.school_id=i.school_id
                 WHERE i.school_id=? AND i.job_id=?
                 ORDER BY s.last_name,s.first_name,i.created_at,i.id
                """, (rs, row) -> new ItemRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getInt(9), rs.getString(10), rs.getLong(11), blank(rs.getString(12)),
                        rs.getString(13), parseDetails(rs.getString(14)), rs.getObject(15, UUID.class),
                        (Long) rs.getObject(16), rs.getString(17), instant(rs.getObject(18, OffsetDateTime.class))),
                TenantContext.get(), job.id());
    }

    private BulletinBatchItemView itemView(ItemRow item, JobRow job) {
        String code = "PUBLISHED".equals(item.status())
                ? BulletinBatchResultCode.PUBLISHED.name()
                : ReportCardBatchEligibilityService.legacyCode(item.resultCode(), item.error());
        BulletinBatchResultCode known = BulletinBatchResultCode.from(code);
        Map<String, Object> details = item.details();
        Map<String, Object> args = new LinkedHashMap<>();
        Object rawArgs = details.get("messageArgs");
        if (rawArgs instanceof Map<?, ?> map) map.forEach((key, value) -> { if (key != null && value != null) args.put(String.valueOf(key), value); });
        args.putIfAbsent("periodCode", job.periodCode());
        args.putIfAbsent("periodLabel", job.periodLabel());
        args.putIfAbsent("studentName", item.studentName());
        String category = known == null ? (item.status().equals("ERROR") ? "TECHNICAL_ERROR" : "BUSINESS_BLOCKER") : known.category();
        String messageKey = known == null ? "academic.batch.unknownResult" : known.messageKey();
        String state = string(details.get("currentState"));
        boolean retryable = booleanValue(details.get("retryableNow"), known != null && known.retryableByDefault());
        BulletinBatchRepairTarget target = null;
        if (known != null && known.businessBlocker()) target = repairTarget(job, item.studentId());
        BulletinBatchSnapshotEvidence snapshot = item.snapshotId() == null ? null : new BulletinBatchSnapshotEvidence(
                item.snapshotId(), item.snapshotVersion() == null ? 0 : item.snapshotVersion(), item.snapshotHash(),
                item.snapshotPublishedAt(), state);
        return new BulletinBatchItemView(item.id(), item.studentId(), item.studentName(), item.status(), item.attempts(),
                item.fileName(), item.sizeBytes(), item.error(), code, category, messageKey, args, state, retryable,
                target, snapshot, string(details.get("correlationId")), item.error(), item.reportingPeriodId(),
                item.reportingPeriodCode(), item.reportingPeriodLabel(), item.product(),
                string(details.get("correctiveAction")), stringList(details.get("affectedRows")));
    }

    private BulletinBatchRepairTarget repairTarget(JobRow job, UUID studentId) {
        return new BulletinBatchRepairTarget("/academic", Map.of("mode", "bulletin", "classId", job.classId().toString(),
                "reportingPeriodId", job.reportingPeriodId().toString(), "studentId", studentId.toString()));
    }

    private byte[] diagnosticCsv(JobRow job, List<ItemRow> items) {
        String locale = job.locale();
        List<String> lines = new ArrayList<>();
        lines.add("student_id,student_name,matricule,status,attempts,result_code,category,current_state,retryable_now,message,repair_route,snapshot_id,snapshot_version,snapshot_hash,snapshot_published_at");
        for (ItemRow item : items) {
            BulletinBatchItemView view = itemView(item, job);
            String message = friendly(view.resultCode(), job.periodCode(), view.studentName(), locale);
            String route = view.repairTarget() == null ? "" : view.repairTarget().route();
            BulletinBatchSnapshotEvidence snapshot = view.snapshot();
            lines.add(csv(item.studentId().toString(), item.studentName(), "", item.status(), String.valueOf(item.attempts()),
                    view.resultCode(), view.category(), String.valueOf(view.currentState()), String.valueOf(view.retryableNow()),
                    message, route, snapshot == null ? "" : snapshot.id().toString(), snapshot == null ? "" : String.valueOf(snapshot.version()),
                    snapshot == null ? "" : snapshot.hash(), snapshot == null ? "" : String.valueOf(snapshot.publishedAt())));
        }
        return String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    private String friendly(String code, String periodCode, String studentName, String locale) {
        String period = periodCode == null ? "the selected period" : periodCode;
        String student = studentName == null ? "the student" : studentName;
        boolean fr = !"en".equalsIgnoreCase(locale);
        return switch (code == null ? "" : code) {
            case "REPORT_NOT_CREATED", "REPORT_NOT_PUBLISHED_LEGACY" -> fr ? "Aucun bulletin " + period + " n'est publié pour " + student + "." : "No " + period + " report card is published for " + student + ".";
            case "REPORT_DRAFT" -> fr ? "Le bulletin " + period + " est encore en brouillon." : "The " + period + " report card is still a draft.";
            case "REPORT_RETURNED" -> fr ? "Le bulletin " + period + " a été retourné pour correction." : "The " + period + " report card was returned for correction.";
            case "REPORT_VALIDATED_NOT_PUBLISHED" -> fr ? "Le bulletin " + period + " est validé mais pas publié." : "The " + period + " report card is validated but not published.";
            case "REPORT_SUPERSEDED_ONLY" -> fr ? "Seules des versions supersédées du bulletin " + period + " existent." : "Only superseded versions of the " + period + " report card exist.";
            case "REPORT_STALE" -> fr ? "Le bulletin " + period + " doit être actualisé avant publication." : "The " + period + " report card must be refreshed before publication.";
            case "SNAPSHOT_UNREADABLE" -> fr ? "Le snapshot du bulletin est illisible; consultez la référence technique." : "The report-card snapshot is unreadable; consult the technical reference.";
            default -> fr ? "Une intervention technique est nécessaire pour ce résultat." : "Technical intervention may be required for this result.";
        };
    }

    private Map<String, Object> details(ReportCardBatchEligibilityService.EligibilityRow row) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("messageKey", row.messageKey());
        details.put("messageArgs", row.messageArgs());
        details.put("currentState", row.currentState());
        details.put("retryableNow", row.retryableNow());
        details.put("reportingPeriodId", row.reportingPeriodId());
        details.put("reportingPeriodCode", row.reportingPeriodCode());
        details.put("reportingPeriodLabel", row.reportingPeriodLabel());
        details.put("product", row.product());
        details.put("correctiveAction", row.correctiveAction());
        details.put("affectedRows", row.affectedRows());
        if (row.correlationId() != null) details.put("correlationId", row.correlationId());
        if (row.snapshot() != null) {
            details.put("snapshotId", row.snapshot().id());
            details.put("snapshotVersion", row.snapshot().version());
            details.put("snapshotHash", row.snapshot().hash());
            details.put("snapshotPublishedAt", row.snapshot().publishedAt());
        }
        return details;
    }

    private List<Map<String, Object>> productSet(ReportCardBatchEligibilityService.EligibilityPreview preview) {
        return preview.reportingPeriodIds().stream().map(id -> {
            Map<String, Object> product = new LinkedHashMap<>();
            product.put("reportingPeriodId", id);
            int index = preview.reportingPeriodIds().indexOf(id);
            product.put("product", preview.products().size() > index ? preview.products().get(index) : "UNKNOWN");
            return product;
        }).toList();
    }

    private Map<String, Object> windowDetails(AcademicWindowPolicyService.WindowView window) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("state", window.state());
        details.put("launchAllowed", window.open());
        details.put("governingTrimesterCode", window.governingTermCode());
        details.put("governingTrimesterLabel", window.governingTermLabel());
        details.put("affectedMilestones", window.governedPeriodCodes());
        details.put("timezone", window.timezone());
        details.put("serverTime", window.serverTime());
        details.put("opensAt", window.opensAt());
        details.put("closesAt", window.closesAt());
        details.put("nextTransition", window.nextTransition());
        details.put("repairTarget", Map.of("route", "/settings", "query", Map.of("tab", "sessions")));
        return details;
    }

    private BulletinBatchWindowView currentWindow(JobRow job) {
        AcademicWindowPolicyService.WindowView window = windows.effective(
                job.reportingPeriodId(), AcademicWindowPolicyService.Action.BATCH_GENERATION);
        String state = "UNRESTRICTED".equals(window.effectiveMode()) ? "UNRESTRICTED" : window.state();
        return new BulletinBatchWindowView(state, window.open(), window.governingTermCode(),
                window.governingTermLabel(), window.governedPeriodCodes(), window.timezone(), window.serverTime(),
                window.opensAt(), window.closesAt(), window.nextTransition(),
                new BulletinBatchRepairTarget("/settings", Map.of("tab", "sessions")));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to persist batch diagnostics", ex); }
    }

    private Map<String, Object> parseDetails(String value) {
        if (value == null || value.isBlank()) return new LinkedHashMap<>();
        try {
            Map<String, Object> parsed = mapper.readValue(value, new TypeReference<>() {});
            Map<String, Object> safe = new LinkedHashMap<>();
            parsed.forEach((key, raw) -> { if (key != null && raw != null) safe.put(key, raw); });
            return safe;
        } catch (Exception ignored) { return new LinkedHashMap<>(); }
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value == null ? fallback : value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        return List.of();
    }
    private static String blank(String value) { return value == null ? "" : value; }
    private static Instant instant(OffsetDateTime value) { return value == null ? null : value.toInstant(); }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static String csv(String... values) { return java.util.Arrays.stream(values).map(v -> "\"" + (v == null ? "" : v.replace("\"", "\"\"")) + "\"").collect(Collectors.joining(",")); }
    private static String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private UUID currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private record JobRow(UUID id, UUID schoolId, UUID academicSessionId, UUID reportingPeriodId, UUID classId,
                          String locale, String status, int totalItems, int processedItems, int publishedItems,
                          int blockedItems, int errorItems, UUID requestedBy, OffsetDateTime requestedAt,
                          OffsetDateTime startedAt, OffsetDateTime completedAt, String archiveStorageKey,
                          String archiveSha256, Long archiveSizeBytes, String lastError, long version,
                          String policy, String scopeFingerprint, String diagnosticStorageKey,
                           String diagnosticSha256, Long diagnosticSizeBytes, String windowAuthorization,
                           String periodCode, String periodLabel, String className) {}

    private record ItemRow(UUID id, UUID studentId, String studentName, String status, UUID reportingPeriodId,
                           String reportingPeriodCode, String reportingPeriodLabel, String product, int attempts,
                           String fileName, long sizeBytes, String error, String resultCode,
                           Map<String, Object> details, UUID snapshotId, Long snapshotVersion,
                           String snapshotHash, Instant snapshotPublishedAt) {
        private boolean retryableNow() {
            BulletinBatchResultCode code = BulletinBatchResultCode.from(ReportCardBatchEligibilityService.legacyCode(resultCode, error));
            return booleanValue(details.get("retryableNow"), code != null && code.retryableByDefault());
        }
    }
}
