package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.academic.security.AcademicAccessPolicyService;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.cohort.AcademicCohortResolver;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.foundation.session.AcademicReportingPeriodRepository;
import com.bbc.sms.foundation.session.AcademicWindowPolicyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.util.*;

/**
 * Administrative inputs that are printed on a bulletin but are not grades:
 * attendance-period corrections and the class-council/conduct decision.
 *
 * These values deliberately live outside the immutable bulletin snapshot until
 * they are approved. Approval invalidates any old calculated snapshot so a
 * report can never silently contain stale attendance or council data.
 */
@Service
public class ReportCardInputService {
    private final JdbcTemplate jdbc;
    private final AcademicReportingPeriodRepository periods;
    private final StudentEnrollmentRepository enrollments;
    private final StudentRepository students;
    private final SchoolClassRepository classes;
    private final AcademicWindowPolicyService windows;
    private final AcademicAccessPolicyService accessPolicy;
    private final AuditService audit;
    private final AcademicCohortResolver cohorts;

    @Autowired
    public ReportCardInputService(JdbcTemplate jdbc,
                                  AcademicReportingPeriodRepository periods,
                                  StudentEnrollmentRepository enrollments,
                                  StudentRepository students,
                                  SchoolClassRepository classes,
                                  AcademicWindowPolicyService windows,
                                  AcademicAccessPolicyService accessPolicy,
                                  AuditService audit,
                                  AcademicCohortResolver cohorts) {
        this.jdbc = jdbc;
        this.periods = periods;
        this.enrollments = enrollments;
        this.students = students;
        this.classes = classes;
        this.windows = windows;
        this.accessPolicy = accessPolicy;
        this.audit = audit;
        this.cohorts = cohorts;
    }

    public ReportCardInputService(JdbcTemplate jdbc,
                                  AcademicReportingPeriodRepository periods,
                                  StudentEnrollmentRepository enrollments,
                                  StudentRepository students,
                                  SchoolClassRepository classes,
                                  AcademicWindowPolicyService windows,
                                  AcademicAccessPolicyService accessPolicy,
                                  AuditService audit) {
        this(jdbc, periods, enrollments, students, classes, windows, accessPolicy, audit, null);
    }

    @Transactional(readOnly = true)
    public ReportCardInputsView list(UUID periodId, UUID classId) {
        AcademicReportingPeriod period = period(periodId);
        SchoolClass schoolClass = schoolClass(classId);
        accessPolicy.require(AcademicAccessPolicyService.Capability.COUNCIL_INPUT_VIEW,
                period.getAcademicSessionId(), classId, null, null, period.getStartDate());
        List<UUID> rosterIds = cohorts == null ? null
                : cohorts.rosterStudentIds(period.getAcademicSessionId(), classId, "ACTIVE", period.getStartDate());
        String rosterFilter = rosterIds == null
                ? "e.school_class_id=?"
                : "e.student_id IN (" + String.join(",", Collections.nCopies(Math.max(1, rosterIds.size()), "?")) + ")";
        if (rosterIds != null && rosterIds.isEmpty()) return new ReportCardInputsView(period.getAcademicSessionId(), period.getId(), period.getCode(), period.getLabel(),
                classId, schoolClass.getName(), List.of(),
                accessPolicy.can(AcademicAccessPolicyService.Capability.COUNCIL_INPUT_EDIT,
                        period.getAcademicSessionId(), classId, null, null, period.getStartDate()),
                accessPolicy.can(AcademicAccessPolicyService.Capability.GRADE_PACKET_REVIEW,
                        period.getAcademicSessionId(), classId, null, null, period.getStartDate()));
        List<Object> rosterArgs = new ArrayList<>(List.of(TenantContext.get(), period.getAcademicSessionId()));
        if (rosterIds == null) rosterArgs.add(classId);
        else rosterArgs.addAll(rosterIds);
        rosterArgs.add(period.getStartDate()); rosterArgs.add(period.getStartDate());
        String rosterSql = "SELECT e.student_id, s.matricule, upper(s.last_name) || ' ' || s.first_name AS student_name "
                + "FROM student_enrollment e JOIN student s ON s.id=e.student_id AND s.active "
                + "WHERE e.school_id=? AND e.academic_session_id=? AND " + rosterFilter
                + " AND e.status='ACTIVE' AND e.enrolled_on<=? AND (e.exited_on IS NULL OR e.exited_on>=?) "
                + "ORDER BY s.last_name, s.first_name";
        List<ReportCardInputRow> rows = jdbc.query(rosterSql, (rs, n) -> {
                    UUID studentId = rs.getObject("student_id", UUID.class);
                    return new ReportCardInputRow(studentId, rs.getString("student_name"), rs.getString("matricule"),
                            attendance(period, studentId), latestAdjustment(periodId, studentId), conduct(periodId, studentId));
                }, rosterArgs.toArray());
        return new ReportCardInputsView(period.getAcademicSessionId(), period.getId(), period.getCode(), period.getLabel(),
                classId, schoolClass.getName(), rows,
                accessPolicy.can(AcademicAccessPolicyService.Capability.COUNCIL_INPUT_EDIT,
                        period.getAcademicSessionId(), classId, null, null, period.getStartDate()),
                accessPolicy.can(AcademicAccessPolicyService.Capability.GRADE_PACKET_REVIEW,
                        period.getAcademicSessionId(), classId, null, null, period.getStartDate()));
    }

    @Transactional
    public ReportCardInputsView save(ReportCardInputUpsert in) {
        AcademicReportingPeriod period = period(in.reportingPeriodId());
        accessPolicy.require(AcademicAccessPolicyService.Capability.COUNCIL_INPUT_EDIT,
                period.getAcademicSessionId(), in.classId(), null, in.studentId(), period.getStartDate());
        assertRoster(period, in.classId(), in.studentId());
        windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.REVIEW);
        BigDecimal justified = nonNegative(in.justifiedAbsenceHours(), "Les heures justifiées");
        BigDecimal unjustified = nonNegative(in.unjustifiedAbsenceHours(), "Les heures non justifiées");
        int late = Math.max(0, in.lateMinutes() == null ? 0 : in.lateMinutes());
        String reason = requiredText(in.reason(), 500, "Le motif de correction est obligatoire");
        String evidence = optionalText(in.evidenceReference(), 240, "La référence de preuve est trop longue");
        if (justified.signum() > 0 || unjustified.signum() > 0 || late > 0) {
            saveAdjustment(period, in, justified, unjustified, late, reason, evidence);
        }
        saveConduct(period, in);
        audit.record("REPORT_CARD_INPUT_SAVED", "REPORT_CARD_INPUT", inputAggregate(in.reportingPeriodId(), in.studentId()),
                null, Map.of("reportingPeriodId", in.reportingPeriodId(), "studentId", in.studentId()), reason);
        return list(in.reportingPeriodId(), in.classId());
    }

    @Transactional
    public ReportCardInputsView submit(UUID periodId, UUID classId, UUID studentId) {
        AcademicReportingPeriod period = period(periodId);
        accessPolicy.require(AcademicAccessPolicyService.Capability.COUNCIL_INPUT_EDIT,
                period.getAcademicSessionId(), classId, null, studentId, period.getStartDate());
        assertRoster(period, classId, studentId);
        windows.assertOpen(periodId, AcademicWindowPolicyService.Action.REVIEW);
        int attendance = jdbc.update("""
                UPDATE attendance_period_adjustment
                   SET status='SUBMITTED', updated_at=now()
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                   AND status IN ('DRAFT','REJECTED')
                """, TenantContext.get(), periodId, studentId);
        int conduct = jdbc.update("""
                UPDATE student_period_conduct
                   SET status='SUBMITTED', updated_at=now()
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                   AND status IN ('DRAFT','RETURNED')
                """, TenantContext.get(), periodId, studentId);
        if (attendance == 0 && conduct == 0) throw ApiException.conflict("Aucun brouillon d'assiduité ou de conseil à soumettre");
        audit.record("REPORT_CARD_INPUT_SUBMITTED", "REPORT_CARD_INPUT", inputAggregate(periodId, studentId), null,
                Map.of("attendanceChanged", attendance, "conductChanged", conduct), "Soumission pour revue");
        return list(periodId, classId);
    }

    @Transactional
    public ReportCardInputsView review(UUID periodId, UUID classId, UUID studentId, ReportCardInputReview in) {
        AcademicReportingPeriod period = period(periodId);
        accessPolicy.require(AcademicAccessPolicyService.Capability.GRADE_PACKET_REVIEW,
                period.getAcademicSessionId(), classId, null, studentId, period.getStartDate());
        assertRoster(period, classId, studentId);
        windows.assertOpen(periodId, AcademicWindowPolicyService.Action.REVIEW);
        String action = in.action().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVE", "REJECT", "RETURN").contains(action))
            throw ApiException.badRequest("Action invalide : utilisez APPROVE, RETURN ou REJECT");
        String reason = "APPROVE".equals(action)
                ? optionalText(in.reason(), 500, "Motif trop long")
                : requiredText(in.reason(), 500, "Le motif du rejet ou retour est obligatoire");
        if ("APPROVE".equals(action)) invalidateSnapshots(periodId, studentId);
        String attendanceState = "APPROVE".equals(action) ? "APPROVED" : "REJECTED";
        int attendance = jdbc.update("""
                UPDATE attendance_period_adjustment
                   SET status=?, reviewed_by=?, reviewed_at=now(), updated_at=now(), version=version+1
                 WHERE school_id=? AND reporting_period_id=? AND student_id=? AND status='SUBMITTED'
                   AND (CAST(? AS BIGINT) IS NULL OR version=CAST(? AS BIGINT))
                """, attendanceState, actorId(), TenantContext.get(), periodId, studentId,
                in.attendanceVersion(), in.attendanceVersion());
        String conductState = "APPROVE".equals(action) ? "APPROVED" : "RETURNED";
        int conduct = jdbc.update("""
                UPDATE student_period_conduct
                   SET status=?, reviewed_by=?, reviewed_at=now(), updated_at=now(), version=version+1
                 WHERE school_id=? AND reporting_period_id=? AND student_id=? AND status='SUBMITTED'
                   AND (CAST(? AS BIGINT) IS NULL OR version=CAST(? AS BIGINT))
                """, conductState, actorId(), TenantContext.get(), periodId, studentId,
                in.conductVersion(), in.conductVersion());
        if (attendance == 0 && conduct == 0) throw ApiException.conflict("Aucun élément soumis à revoir ou version périmée");
        audit.record("REPORT_CARD_INPUT_" + action, "REPORT_CARD_INPUT", inputAggregate(periodId, studentId), null,
                Map.of("attendanceChanged", attendance, "conductChanged", conduct, "action", action), reason);
        return list(periodId, classId);
    }

    private void saveAdjustment(AcademicReportingPeriod period, ReportCardInputUpsert in,
                                BigDecimal justified, BigDecimal unjustified, int late,
                                String reason, String evidence) {
        Map<String, Object> latest = jdbc.query("""
                SELECT id,status,version FROM attendance_period_adjustment
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                 ORDER BY created_at DESC LIMIT 1
                """, rs -> rs.next() ? Map.of("id", rs.getObject("id", UUID.class),
                "status", rs.getString("status"), "version", rs.getLong("version")) : null,
                TenantContext.get(), period.getId(), in.studentId());
        String status = latest == null ? null : String.valueOf(latest.get("status"));
        if ("SUBMITTED".equals(status)) throw ApiException.conflict("La correction d'assiduité est déjà soumise à la revue");
        if ("APPROVED".equals(status)) {
            windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.CORRECTION);
            latest = null;
        }
        if (latest == null) {
            jdbc.update("""
                    INSERT INTO attendance_period_adjustment
                    (school_id,academic_session_id,reporting_period_id,student_id,
                     justified_absence_hours,unjustified_absence_hours,late_minutes,
                     reason,evidence_reference,status,created_by)
                    VALUES (?,?,?,?,?,?,?,?,?,'DRAFT',?)
                    """, TenantContext.get(), period.getAcademicSessionId(), period.getId(), in.studentId(),
                    justified, unjustified, late, reason, evidence, actorId());
            return;
        }
        long expected = in.attendanceVersion() == null ? ((Number) latest.get("version")).longValue() : in.attendanceVersion();
        int updated = jdbc.update("""
                UPDATE attendance_period_adjustment
                   SET justified_absence_hours=?, unjustified_absence_hours=?, late_minutes=?,
                       reason=?, evidence_reference=?, status='DRAFT', updated_at=now(), version=version+1
                 WHERE id=? AND school_id=? AND status IN ('DRAFT','REJECTED') AND version=?
                """, justified, unjustified, late, reason, evidence, latest.get("id"), TenantContext.get(), expected);
        if (updated == 0) throw ApiException.conflict("La correction d'assiduité a été modifiée entre-temps");
    }

    private void saveConduct(AcademicReportingPeriod period, ReportCardInputUpsert in) {
        String observation = optionalText(in.councilObservation(), 4000, "L'observation du conseil est trop longue");
        int exclusionDays = Math.max(0, in.exclusionDays() == null ? 0 : in.exclusionDays());
        Map<String, Object> latest = jdbc.query("""
                SELECT id,status,version FROM student_period_conduct
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                """, rs -> rs.next() ? Map.of("id", rs.getObject("id", UUID.class),
                "status", rs.getString("status"), "version", rs.getLong("version")) : null,
                TenantContext.get(), period.getId(), in.studentId());
        if (latest == null) {
            jdbc.update("""
                    INSERT INTO student_period_conduct
                    (school_id,academic_session_id,reporting_period_id,student_id,
                     work_warning,work_blame,conduct_warning,conduct_blame,honor_roll,
                     encouragement,congratulations,exclusion_days,decision_code,
                     council_observation,status,created_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?)
                    """, TenantContext.get(), period.getAcademicSessionId(), period.getId(), in.studentId(),
                    in.workWarning(), in.workBlame(), in.conductWarning(), in.conductBlame(), in.honorRoll(),
                    in.encouragement(), in.congratulations(), exclusionDays, optionalText(in.decisionCode(), 64, "La décision est trop longue"),
                    observation, actorId());
            return;
        }
        String status = String.valueOf(latest.get("status"));
        if ("SUBMITTED".equals(status)) throw ApiException.conflict("La fiche du conseil est déjà soumise à la revue");
        if ("APPROVED".equals(status) || "LOCKED".equals(status)) windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.CORRECTION);
        long expected = in.conductVersion() == null ? ((Number) latest.get("version")).longValue() : in.conductVersion();
        int updated = jdbc.update("""
                UPDATE student_period_conduct
                   SET work_warning=?,work_blame=?,conduct_warning=?,conduct_blame=?,honor_roll=?,
                       encouragement=?,congratulations=?,exclusion_days=?,decision_code=?,
                       council_observation=?,status='DRAFT',updated_at=now(),version=version+1
                 WHERE id=? AND school_id=? AND version=?
                """, in.workWarning(), in.workBlame(), in.conductWarning(), in.conductBlame(), in.honorRoll(),
                in.encouragement(), in.congratulations(), exclusionDays, optionalText(in.decisionCode(), 64, "La décision est trop longue"),
                observation, latest.get("id"), TenantContext.get(), expected);
        if (updated == 0) throw ApiException.conflict("La fiche du conseil a été modifiée entre-temps");
    }

    private AttendanceSummaryView attendance(AcademicReportingPeriod period, UUID studentId) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT count(DISTINCT s.id) AS finalized_sessions,
                       count(*) FILTER (WHERE m.status='PRESENT') AS present_count,
                       count(*) FILTER (WHERE m.status='ABSENT') AS absent_count,
                       count(*) FILTER (WHERE m.status='EXCUSED') AS excused_count,
                       count(*) FILTER (WHERE m.status='LATE') AS late_count,
                       coalesce(sum(m.late_minutes),0) AS late_minutes,
                       coalesce(sum(CASE WHEN m.status='EXCUSED' THEN s.duration_minutes ELSE 0 END),0)/60.0 AS justified,
                       coalesce(sum(CASE WHEN m.status='ABSENT' THEN s.duration_minutes ELSE 0 END),0)/60.0 AS unjustified
                  FROM attendance_session s JOIN attendance_mark m ON m.attendance_session_id=s.id
                 WHERE s.school_id=? AND s.academic_session_id=? AND m.student_id=?
                   AND s.status='FINALIZED' AND s.session_date BETWEEN ? AND ?
                """, TenantContext.get(), period.getAcademicSessionId(), studentId,
                Date.valueOf(period.getStartDate()), Date.valueOf(period.getEndDate()));
        Map<String, Object> adjustment = jdbc.queryForMap("""
                SELECT coalesce(sum(justified_absence_hours),0) AS justified,
                       coalesce(sum(unjustified_absence_hours),0) AS unjustified,
                       coalesce(sum(late_minutes),0) AS late_minutes
                  FROM attendance_period_adjustment
                 WHERE school_id=? AND reporting_period_id=? AND student_id=? AND status='APPROVED'
                """, TenantContext.get(), period.getId(), studentId);
        return new AttendanceSummaryView(number(row.get("finalized_sessions")), number(row.get("present_count")),
                number(row.get("absent_count")), number(row.get("excused_count")), number(row.get("late_count")),
                number(row.get("late_minutes")), decimal(row.get("justified")), decimal(row.get("unjustified")),
                decimal(adjustment.get("justified")), decimal(adjustment.get("unjustified")), number(adjustment.get("late_minutes")));
    }

    private AttendanceAdjustmentView latestAdjustment(UUID periodId, UUID studentId) {
        return jdbc.query("""
                SELECT id,justified_absence_hours,unjustified_absence_hours,late_minutes,
                       reason,evidence_reference,status,version
                  FROM attendance_period_adjustment
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                 ORDER BY created_at DESC LIMIT 1
                """, rs -> rs.next() ? new AttendanceAdjustmentView(rs.getObject("id", UUID.class),
                rs.getBigDecimal("justified_absence_hours"), rs.getBigDecimal("unjustified_absence_hours"),
                rs.getInt("late_minutes"), rs.getString("reason"), rs.getString("evidence_reference"),
                rs.getString("status"), rs.getLong("version")) : null,
                TenantContext.get(), periodId, studentId);
    }

    private ConductInputView conduct(UUID periodId, UUID studentId) {
        return jdbc.query("""
                SELECT work_warning,work_blame,conduct_warning,conduct_blame,honor_roll,
                       encouragement,congratulations,exclusion_days,decision_code,
                       council_observation,status,version
                  FROM student_period_conduct
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                """, rs -> rs.next() ? new ConductInputView(rs.getBoolean(1), rs.getBoolean(2),
                rs.getBoolean(3), rs.getBoolean(4), rs.getBoolean(5), rs.getBoolean(6), rs.getBoolean(7),
                rs.getInt(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getLong(12)) : null,
                TenantContext.get(), periodId, studentId);
    }

    private void invalidateSnapshots(UUID periodId, UUID studentId) {
        int count = jdbc.queryForObject("""
                SELECT count(*) FROM bulletin_version
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                   AND state IN ('VALIDATED','PUBLISHED')
                """, Integer.class, TenantContext.get(), periodId, studentId);
        if (count > 0) windows.assertOpen(periodId, AcademicWindowPolicyService.Action.CORRECTION);
        if (count > 0) jdbc.update("""
                UPDATE bulletin_version SET state='SUPERSEDED'
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                   AND state IN ('VALIDATED','PUBLISHED')
                """, TenantContext.get(), periodId, studentId);
    }

    private AcademicReportingPeriod period(UUID id) {
        return periods.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période de résultat"));
    }

    private SchoolClass schoolClass(UUID id) {
        return classes.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Classe"));
    }

    private void assertRoster(AcademicReportingPeriod period, UUID classId, UUID studentId) {
        schoolClass(classId);
        students.findByIdAndSchoolId(studentId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Élève"));
        enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), studentId, period.getAcademicSessionId(), "ACTIVE")
                .filter(e -> (cohorts != null
                        ? cohorts.studentBelongsToClass(period.getAcademicSessionId(), classId, studentId, "ACTIVE", period.getStartDate())
                        : classId.equals(e.getSchoolClassId()))
                        && !e.getEnrolledOn().isAfter(period.getStartDate())
                        && (e.getExitedOn() == null || !e.getExitedOn().isBefore(period.getStartDate())))
                .orElseThrow(() -> ApiException.badRequest("L'élève n'est pas inscrit dans cette classe pour la session"));
    }

    private void requireReviewer() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String role = auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.roleCode() : "";
        if (!Set.of("admin", "principal", "dean_of_studies", "censor").contains(role))
            throw ApiException.forbidden("Seuls la direction, le censeur ou le responsable des études peuvent approuver ces données");
    }

    private UUID actorId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private static String inputAggregate(UUID periodId, UUID studentId) { return periodId + ":" + studentId; }
    private static BigDecimal nonNegative(BigDecimal value, String label) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        if (v.signum() < 0) throw ApiException.badRequest(label + " ne peuvent pas être négatives");
        return v;
    }
    private static String requiredText(String value, int max, String message) {
        if (value == null || value.isBlank()) throw ApiException.badRequest(message);
        return optionalText(value, max, message);
    }
    private static String optionalText(String value, int max, String message) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.length() > max) throw ApiException.badRequest(message);
        return v;
    }
    private static int number(Object value) { return value == null ? 0 : ((Number) value).intValue(); }
    private static BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
}
