package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.BulletinBatchItemView;
import com.bbc.sms.academic.dto.AcademicDtos.BulletinBatchJobCreateRequest;
import com.bbc.sms.academic.dto.AcademicDtos.BulletinBatchJobView;
import com.bbc.sms.academic.dto.AcademicDtos.BulletinBatchCancelRequest;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.foundation.session.AcademicReportingPeriodRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Creates and exposes durable class-level bulletin generation jobs. */
@Service
public class ReportCardBatchJobService {
    private final JdbcTemplate jdbc;
    private final StudentEnrollmentRepository enrollments;
    private final AcademicReportingPeriodRepository periods;
    private final SchoolClassRepository classes;
    private final StudentRepository students;
    private final TeacherScopeService teacherScope;
    private final ReportCardBatchJobWorker worker;

    public ReportCardBatchJobService(JdbcTemplate jdbc,
                                     StudentEnrollmentRepository enrollments,
                                     AcademicReportingPeriodRepository periods,
                                     SchoolClassRepository classes,
                                     StudentRepository students,
                                     TeacherScopeService teacherScope,
                                     ReportCardBatchJobWorker worker) {
        this.jdbc = jdbc;
        this.enrollments = enrollments;
        this.periods = periods;
        this.classes = classes;
        this.students = students;
        this.teacherScope = teacherScope;
        this.worker = worker;
    }

    @Transactional
    public BulletinBatchJobView create(BulletinBatchJobCreateRequest request) {
        UUID schoolId = TenantContext.get();
        if (request == null || request.classId() == null || request.reportingPeriodId() == null) {
            throw ApiException.badRequest("La classe et la période de résultat sont obligatoires");
        }
        teacherScope.assertClass(request.classId());
        classes.findByIdAndSchoolId(request.classId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));
        AcademicReportingPeriod period = periods.findByIdAndSchoolId(request.reportingPeriodId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Période de résultat"));
        List<StudentEnrollment> roster = enrollments
                .findBySchoolIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByClassNameSnapshotAsc(
                        schoolId, period.getAcademicSessionId(), request.classId(), "ACTIVE");
        if (roster.isEmpty()) throw ApiException.conflict("Aucun élève actif dans cette classe");

        UUID id = UUID.randomUUID();
        String locale = "en".equalsIgnoreCase(request.locale()) ? "en" : "fr";
        jdbc.update("""
                INSERT INTO bulletin_batch_job
                    (id,school_id,academic_session_id,reporting_period_id,class_id,locale,status,total_items,requested_by)
                VALUES (?,?,?,?,?,?,'QUEUED',?,?)
                """, id, schoolId, period.getAcademicSessionId(), period.getId(), request.classId(), locale,
                roster.size(), currentUserId());
        for (StudentEnrollment enrollment : roster) {
            jdbc.update("""
                    INSERT INTO bulletin_batch_item (id,school_id,job_id,student_id,status)
                    VALUES (?,?,?,?,'QUEUED')
                    """, UUID.randomUUID(), schoolId, id, enrollment.getStudentId());
        }
        startAfterCommit(id, schoolId);
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
        return jdbc.query("""
                SELECT i.id,i.student_id,coalesce(s.last_name||' '||s.first_name,i.student_id::text),
                       i.status,i.attempts,coalesce(i.file_name,''),coalesce(i.size_bytes,0),coalesce(i.error,'')
                  FROM bulletin_batch_item i
                  JOIN student s ON s.id=i.student_id
                 WHERE i.school_id=? AND i.job_id=?
                 ORDER BY s.last_name,s.first_name,i.created_at
                """, (rs, row) -> new BulletinBatchItemView(
                        rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getInt(5), rs.getString(6), rs.getLong(7), blank(rs.getString(8))),
                TenantContext.get(), id);
    }

    @Transactional
    public BulletinBatchJobView retry(UUID id, UUID itemId) {
        JobRow job = job(id);
        teacherScope.assertClass(job.classId());
        if ("RUNNING".equals(job.status())) throw ApiException.conflict("La génération est déjà en cours");
        int changed;
        if (itemId == null) {
            changed = jdbc.update("""
                    UPDATE bulletin_batch_item
                       SET status='QUEUED', error=NULL, started_at=NULL, completed_at=NULL,
                           file_name=NULL, file_storage_key=NULL, sha256=NULL, size_bytes=NULL, version=version+1
                     WHERE school_id=? AND job_id=? AND status IN ('ERROR','BLOCKED')
                    """, TenantContext.get(), id);
        } else {
            changed = jdbc.update("""
                    UPDATE bulletin_batch_item
                       SET status='QUEUED', error=NULL, started_at=NULL, completed_at=NULL,
                           file_name=NULL, file_storage_key=NULL, sha256=NULL, size_bytes=NULL, version=version+1
                     WHERE school_id=? AND job_id=? AND id=? AND status IN ('ERROR','BLOCKED')
                    """, TenantContext.get(), id, itemId);
        }
        if (changed == 0) throw ApiException.conflict("Aucun étudiant en erreur ou bloqué ne peut être relancé");
        jdbc.update("""
                UPDATE bulletin_batch_job
                   SET status='QUEUED', processed_items=0, published_items=0,
                       blocked_items=0, error_items=0, completed_at=NULL,
                       archive_storage_key=NULL, archive_sha256=NULL, archive_size_bytes=NULL,
                       last_error=NULL, version=version+1
                 WHERE school_id=? AND id=?
                """, TenantContext.get(), id);
        startAfterCommit(id, TenantContext.get());
        return view(id);
    }

    @Transactional
    public BulletinBatchJobView cancel(UUID id, BulletinBatchCancelRequest request) {
        JobRow job = job(id);
        teacherScope.assertClass(job.classId());
        if (Set.of("COMPLETED", "COMPLETED_ERRORS", "FAILED", "CANCELLED").contains(job.status())) {
            throw ApiException.conflict("Cette génération est déjà terminée ou annulée");
        }
        int changed = jdbc.update("""
                UPDATE bulletin_batch_job
                   SET status='CANCELLED',cancelled_at=now(),cancelled_by=?,cancel_reason=?,version=version+1
                 WHERE school_id=? AND id=? AND status IN ('QUEUED','RUNNING')
                """, currentUserId(), request.reason().trim(), TenantContext.get(), id);
        if (changed == 0) throw ApiException.conflict("La génération a changé entre-temps");
        return view(id);
    }

    @Transactional(readOnly = true)
    public byte[] archive(UUID id) {
        JobRow job = job(id);
        teacherScope.assertClass(job.classId());
        if (job.archiveStorageKey() == null || job.archiveStorageKey().isBlank()) {
            throw ApiException.conflict("L'archive n'est pas encore disponible");
        }
        return worker.readArchive(job.archiveStorageKey());
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
                SELECT id,school_id,academic_session_id,reporting_period_id,class_id,locale,status,
                       total_items,processed_items,published_items,blocked_items,error_items,
                       requested_by,requested_at,started_at,completed_at,archive_storage_key,
                       archive_sha256,archive_size_bytes,last_error,version
                  FROM bulletin_batch_job WHERE school_id=? AND id=?
                """, rs -> rs.next() ? new JobRow(
                        rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7),
                        rs.getInt(8), rs.getInt(9), rs.getInt(10), rs.getInt(11), rs.getInt(12),
                        rs.getObject(13, UUID.class), rs.getObject(14, java.time.OffsetDateTime.class),
                        rs.getObject(15, java.time.OffsetDateTime.class), rs.getObject(16, java.time.OffsetDateTime.class),
                        rs.getString(17), rs.getString(18), (Long) rs.getObject(19), rs.getString(20), rs.getLong(21)) : null,
                TenantContext.get(), id);
        if (row == null) throw ApiException.notFound("Lot de génération des bulletins");
        return row;
    }

    private BulletinBatchJobView toView(JobRow x) {
        int progress = x.totalItems() == 0 ? 0 : Math.min(100, (x.processedItems() * 100) / x.totalItems());
        return new BulletinBatchJobView(x.id(), x.academicSessionId(), x.reportingPeriodId(), x.classId(), x.locale(),
                x.status(), x.totalItems(), x.processedItems(), x.publishedItems(), x.blockedItems(), x.errorItems(),
                progress, x.requestedAt(), x.startedAt(), x.completedAt(), x.archiveStorageKey() != null,
                x.archiveSha256(), x.archiveSizeBytes(), x.lastError(), x.version());
    }

    private UUID currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private static String blank(String value) { return value == null ? "" : value; }

    private record JobRow(UUID id, UUID schoolId, UUID academicSessionId, UUID reportingPeriodId, UUID classId,
                          String locale, String status, int totalItems, int processedItems, int publishedItems,
                          int blockedItems, int errorItems, UUID requestedBy, java.time.OffsetDateTime requestedAt,
                          java.time.OffsetDateTime startedAt, java.time.OffsetDateTime completedAt,
                          String archiveStorageKey, String archiveSha256, Long archiveSizeBytes, String lastError,
                          long version) {}
}
