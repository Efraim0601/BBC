package com.bbc.sms.attendance;

import com.bbc.sms.academic.dto.AcademicDtos.ConductSummaryView;
import com.bbc.sms.academic.dto.AcademicDtos.ConductInputView;
import com.bbc.sms.academic.dto.AcademicDtos.ReportCardInputUpsert;
import com.bbc.sms.attendance.dto.AttendanceDtos.*;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.session.AcademicWindowPolicyService;
import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.foundation.session.AcademicReportingPeriodRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The single attendance evidence read/write boundary used by Attendance and
 * report-card flows.  It deliberately treats roll calls as immutable source
 * facts: adjustments and council decisions are separate, append-audited rows.
 */
@Service
public class AttendanceEvidenceService {
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Set<String> OPEN_ADJUSTMENT_STATES = Set.of("DRAFT", "RETURNED", "REJECTED");
    private static final Set<String> APPROVED_STATES = Set.of("APPROVED", "LOCKED", "LOCKED_BY_PUBLICATION");

    private final JdbcTemplate jdbc;
    private final AcademicReportingPeriodRepository periods;
    private final StudentEnrollmentRepository enrollments;
    private final TeacherScopeService teacherScope;
    private final AcademicWindowPolicyService windows;
    private final ObjectMapper mapper;

    public AttendanceEvidenceService(JdbcTemplate jdbc,
                                     AcademicReportingPeriodRepository periods,
                                     StudentEnrollmentRepository enrollments,
                                     TeacherScopeService teacherScope,
                                     AcademicWindowPolicyService windows,
                                     ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.periods = periods;
        this.enrollments = enrollments;
        this.teacherScope = teacherScope;
        this.windows = windows;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryView aggregate(UUID reportingPeriodId, UUID studentId) {
        teacherScope.assertStudent(studentId);
        AcademicReportingPeriod period = period(reportingPeriodId);
        EnrollmentInfo enrollment = enrollment(period, studentId);
        if (enrollment == null) {
            throw ApiException.conflict("Cet élève n'est pas inscrit dans la session académique sélectionnée.");
        }
        return "ANNUAL_RESULT".equalsIgnoreCase(period.getPeriodType())
                ? annualAggregate(period, studentId, enrollment)
                : periodAggregate(period, studentId, enrollment);
    }

    @Transactional(readOnly = true)
    public AttendanceAggregationView aggregateView(UUID reportingPeriodId, UUID studentId) {
        teacherScope.assertStudent(studentId);
        AcademicReportingPeriod period = period(reportingPeriodId);
        EnrollmentInfo enrollment = enrollment(period, studentId);
        if (enrollment == null) throw ApiException.conflict("Cet élève n'est pas inscrit dans la session académique sélectionnée.");
        return new AttendanceAggregationView(period.getAcademicSessionId(), reportingPeriodId, enrollment.classId(),
                studentId, enrollment.className(), modelFor(enrollment.level()), aggregate(reportingPeriodId, studentId));
    }

    @Transactional(readOnly = true)
    public List<AttendanceAggregationView> aggregateForClass(UUID reportingPeriodId, UUID classId) {
        AcademicReportingPeriod period = period(reportingPeriodId);
        teacherScope.assertClass(classId);
        return jdbc.query("""
                SELECT e.student_id, c.name, c.level, c.id
                  FROM student_enrollment e
                  JOIN school_class c ON c.id=e.school_class_id
                 WHERE e.school_id=? AND e.academic_session_id=? AND e.school_class_id=?
                   AND e.status='ACTIVE'
                 ORDER BY e.student_id
                """, (rs, n) -> {
                    UUID studentId = rs.getObject("student_id", UUID.class);
                    AttendanceSummaryView summary = aggregate(reportingPeriodId, studentId);
                    return new AttendanceAggregationView(period.getAcademicSessionId(), reportingPeriodId,
                            classId, studentId, rs.getString("name"), modelFor(rs.getString("level")), summary);
                }, TenantContext.get(), period.getAcademicSessionId(), classId);
    }

    @Transactional(readOnly = true)
    public List<AttendanceSourceBreakdownView> sourceBreakdown(UUID reportingPeriodId, UUID studentId) {
        teacherScope.assertStudent(studentId);
        AcademicReportingPeriod period = period(reportingPeriodId);
        EnrollmentInfo enrollment = enrollment(period, studentId);
        if (enrollment == null) throw ApiException.conflict("Cet élève n'est pas inscrit dans la session académique sélectionnée.");
        if ("ANNUAL_RESULT".equalsIgnoreCase(period.getPeriodType())) {
            return annualSourceBreakdown(period, studentId);
        }
        Map<UUID, MarkRow> marks = marks(period, studentId);
        List<SessionRow> rows = sessionRows(period, enrollment.classId());
        List<AttendanceSourceBreakdownView> result = new ArrayList<>();
        for (SessionRow row : rows) {
            MarkRow mark = row.rollCallId() == null ? null : marks.get(row.rollCallId());
            DurationValue duration = duration(row, period.getAcademicSessionId());
            int minutes = duration.minutes() == null ? 0 : duration.minutes();
            BigDecimal absence = mark != null && Set.of("ABSENT", "EXCUSED").contains(mark.status())
                    ? BigDecimal.valueOf(minutes) : BigDecimal.ZERO;
            result.add(new AttendanceSourceBreakdownView(row.expectedId(), row.rollCallId(), studentId,
                    row.date(), row.model(), row.periodKey(), row.subjectCode(), row.status(),
                    mark == null ? "UNMARKED" : mark.status(), minutes, absence,
                    mark == null ? 0 : mark.lateMinutes(), mark == null ? null : mark.source(),
                    mark == null ? null : mark.reason(), mark == null ? null : mark.note(),
                    row.cancelled(), row.version()));
        }
        return result;
    }

    @Transactional
    public ConductRecommendationView recommendation(UUID reportingPeriodId, UUID studentId) {
        AcademicReportingPeriod period = period(reportingPeriodId);
        return calculateRecommendation(period, studentId, true);
    }

    /**
     * Calculates the recommendation without mutating the recommendation
     * evidence table when called from a read-only preview.  The council can
     * still ask the write endpoint to persist the same calculation explicitly.
     */
    private ConductRecommendationView calculateRecommendation(AcademicReportingPeriod period,
                                                               UUID studentId,
                                                               boolean persist) {
        AttendanceSummaryView attendance = aggregate(period.getId(), studentId);
        PolicyRecommendation policy = recommendationPolicy();
        int incidents = jdbc.queryForObject("""
                SELECT count(*) FROM discipline_incident
                 WHERE school_id=? AND student_id=? AND incident_date BETWEEN ? AND ?
                """, Integer.class, TenantContext.get(), studentId,
                Date.valueOf(period.getStartDate()), Date.valueOf(period.getEndDate()));
        BigDecimal absencePercent = attendance.expectedHours() == null || attendance.expectedHours().signum() == 0
                ? BigDecimal.ZERO
                : attendance.totalAbsenceHours().multiply(HUNDRED)
                    .divide(attendance.expectedHours(), 4, RoundingMode.HALF_UP);
        boolean workWarning = absencePercent.compareTo(policy.absenceWarningPercent()) >= 0
                || attendance.lateMinutes() >= policy.lateWarningMinutes();
        boolean workBlame = absencePercent.compareTo(policy.workBlameAbsencePercent()) >= 0;
        boolean conductWarning = incidents >= policy.disciplineWarningCount();
        boolean conductBlame = incidents >= policy.disciplineBlameCount();
        boolean awardReady = attendance.coveragePercent().compareTo(policy.awardMinCoveragePercent()) >= 0
                && absencePercent.compareTo(policy.honorMaxAbsencePercent()) <= 0
                && BigDecimal.valueOf(attendance.lateMinutes()).compareTo(BigDecimal.valueOf(policy.honorMaxLateMinutes())) <= 0;
        boolean honor = awardReady;
        boolean encouragement = awardReady;
        boolean congratulations = awardReady && incidents == 0;
        String reason = "absence=" + absencePercent + "%; late=" + attendance.lateMinutes()
                + "; incidents=" + incidents + "; policy=" + policy.policyVersion();
        String fingerprint = sha256(reason + ":" + attendance.policyVersion());
        UUID existing = jdbc.query("""
                SELECT id FROM student_period_conduct_recommendation
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), period.getId(), studentId);
        if (persist && existing == null) {
            jdbc.update("""
                    INSERT INTO student_period_conduct_recommendation
                    (school_id,academic_session_id,reporting_period_id,student_id,policy_version,
                     work_warning,work_blame,conduct_warning,conduct_blame,honor_roll,encouragement,
                     congratulations,exclusion_days,recommendation_reason,source_fingerprint,calculated_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, TenantContext.get(), period.getAcademicSessionId(), period.getId(), studentId,
                    policy.policyVersion(), workWarning, workBlame, conductWarning, conductBlame, honor,
                    encouragement, congratulations, 0, reason, fingerprint, actorId());
        } else if (persist) {
            jdbc.update("""
                    UPDATE student_period_conduct_recommendation
                       SET policy_version=?,work_warning=?,work_blame=?,conduct_warning=?,conduct_blame=?,
                           honor_roll=?,encouragement=?,congratulations=?,exclusion_days=?,
                           recommendation_reason=?,source_fingerprint=?,version=version+1,
                           calculated_at=now(),calculated_by=?
                     WHERE school_id=? AND id=?
                    """, policy.policyVersion(), workWarning, workBlame, conductWarning, conductBlame,
                    honor, encouragement, congratulations, 0, reason, fingerprint, actorId(),
                    TenantContext.get(), existing);
        }
        return jdbc.query("""
                SELECT version,calculated_at
                  FROM student_period_conduct_recommendation
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                """, rs -> rs.next() ? new ConductRecommendationView(
                        workWarning, workBlame, conductWarning, conductBlame, honor, encouragement,
                        congratulations, 0, policy.policyVersion(), reason, rs.getLong(1), instant(rs.getObject(2)))
                        : new ConductRecommendationView(workWarning, workBlame, conductWarning, conductBlame,
                        honor, encouragement, congratulations, 0, policy.policyVersion(), reason, 0, null),
                TenantContext.get(), period.getId(), studentId);
    }

    @Transactional(readOnly = true)
    public ConductSummaryView conductSummary(UUID reportingPeriodId, UUID studentId) {
        AcademicReportingPeriod period = period(reportingPeriodId);
        ConductRecommendationView recommendation = calculateRecommendation(period, studentId, false);
        return jdbc.query("""
                SELECT work_warning,work_blame,conduct_warning,conduct_blame,honor_roll,encouragement,
                       congratulations,exclusion_days,decision_code,council_observation,status,version,
                       override_by,override_reason
                  FROM student_period_conduct
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                """, rs -> rs.next() ? new ConductSummaryView(
                        rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4),
                        rs.getBoolean(5), rs.getBoolean(6), rs.getBoolean(7), rs.getInt(8),
                        rs.getString(9), rs.getString(10), rs.getString(11), recommendation,
                        rs.getObject(13, UUID.class), rs.getString(14), rs.getLong(12))
                        : new ConductSummaryView(false, false, false, false, false, false, false,
                        0, null, null, "DRAFT", recommendation, null, null, 0),
                TenantContext.get(), reportingPeriodId, studentId);
    }

    @Transactional(readOnly = true)
    public ConductInputView conductInput(UUID reportingPeriodId, UUID studentId) {
        ConductSummaryView view = conductSummary(reportingPeriodId, studentId);
        return new ConductInputView(view.workWarning(), view.workBlame(), view.conductWarning(),
                view.conductBlame(), view.honorRoll(), view.encouragement(), view.congratulations(),
                view.exclusionDays(), view.decisionCode(), view.councilObservation(), view.status(),
                view.version(), view.recommendation(), view.overrideBy(), view.overrideReason());
    }

    /**
     * Saves the council choice separately from the calculated recommendation.
     * A choice that differs from the recommendation is an override and must be
     * made by a principal/prefect (or another role granted the override action)
     * with a reason. The row remains versioned and its workflow history is
     * append-only.
     */
    @Transactional
    public void saveConductInput(AcademicReportingPeriod period, UUID classId, ReportCardInputUpsert input) {
        teacherScope.assertClass(classId);
        assertRoster(period, classId, input.studentId());
        String decisionCode = optional(input.decisionCode(), 64);
        String observation = optional(input.councilObservation(), 4000);
        int exclusionDays = Math.max(0, input.exclusionDays() == null ? 0 : input.exclusionDays());
        ConductRecommendationView recommendation = recommendation(period.getId(), input.studentId());
        boolean override = !sameDecision(input, recommendation, exclusionDays);
        String overrideReason = optional(input.overrideReason(), 500);
        if (override && !canOverrideCouncil())
            throw ApiException.forbidden("Seule une autorité habilitée peut déroger à la recommandation du conseil.");
        if (override && overrideReason == null)
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "COUNCIL_OVERRIDE_REASON_REQUIRED",
                    "Le motif de dérogation est obligatoire.", "overrideReason", "Le motif de dérogation est obligatoire.");
        LatestConduct latest = latestConduct(period.getId(), input.studentId());
        if (latest == null) {
            UUID id = jdbc.queryForObject("""
                    INSERT INTO student_period_conduct
                    (school_id,academic_session_id,reporting_period_id,student_id,
                     work_warning,work_blame,conduct_warning,conduct_blame,honor_roll,
                     encouragement,congratulations,exclusion_days,decision_code,
                     council_observation,status,created_by,override_by,override_reason)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?) RETURNING id
                    """, UUID.class, TenantContext.get(), period.getAcademicSessionId(), period.getId(), input.studentId(),
                    input.workWarning(), input.workBlame(), input.conductWarning(), input.conductBlame(), input.honorRoll(),
                    input.encouragement(), input.congratulations(), exclusionDays, decisionCode, observation, actorId(),
                    override ? actorId() : null, overrideReason);
            historyConduct(id, null, "DRAFT", "Saisie du conseil", overrideReason, 0);
            return;
        }
        if ("SUBMITTED".equals(latest.status()))
            throw ApiException.conflict("La fiche du conseil est déjà soumise à la revue.");
        String correctionReason = optional(input.correctionReason(), 500);
        String correctionEvidence = optional(input.correctionEvidenceReference(), 240);
        boolean locked = Set.of("APPROVED", "LOCKED", "LOCKED_BY_PUBLICATION").contains(latest.status());
        if (locked) windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.CORRECTION);
        if (locked && correctionReason == null)
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "COUNCIL_CORRECTION_REASON_REQUIRED",
                    "Une correction explicite est obligatoire après approbation/publication.",
                    "correctionReason", "Une correction explicite est obligatoire après approbation/publication.");
        long expected = input.conductVersion() == null ? latest.version() : input.conductVersion();
        int changed = jdbc.update("""
                UPDATE student_period_conduct
                   SET work_warning=?,work_blame=?,conduct_warning=?,conduct_blame=?,honor_roll=?,
                       encouragement=?,congratulations=?,exclusion_days=?,decision_code=?,
                       council_observation=?,status='DRAFT',override_by=?,override_reason=?,
                       corrects_conduct_id=CASE WHEN student_period_conduct.status IN ('APPROVED','LOCKED','LOCKED_BY_PUBLICATION') THEN id ELSE corrects_conduct_id END,
                       correction_reason=?,correction_evidence_reference=?,updated_at=now(),version=version+1
                 WHERE school_id=? AND id=? AND version=?
                   AND status IN ('DRAFT','RETURNED','APPROVED','LOCKED','LOCKED_BY_PUBLICATION')
                """, input.workWarning(), input.workBlame(), input.conductWarning(), input.conductBlame(), input.honorRoll(),
                input.encouragement(), input.congratulations(), exclusionDays, decisionCode, observation,
                override ? actorId() : null, overrideReason, correctionReason, correctionEvidence,
                TenantContext.get(), latest.id(), expected);
        if (changed == 0)
            throw ApiException.staleVersion("La fiche du conseil a été modifiée entre-temps.", latest.version(), expected);
        historyConduct(latest.id(), latest.status(), "DRAFT", correctionReason == null ? "Mise à jour du conseil" : correctionReason,
                overrideReason, expected + 1);
    }

    @Transactional
    public void submitInputs(UUID reportingPeriodId, UUID classId, UUID studentId) {
        AcademicReportingPeriod period = period(reportingPeriodId);
        teacherScope.assertClass(classId);
        assertRoster(period, classId, studentId);
        int changed = 0;
        LatestAdjustment adjustment = latestAdjustment(reportingPeriodId, studentId);
        if (adjustment != null && OPEN_ADJUSTMENT_STATES.contains(adjustment.status())) {
            transitionAdjustment(adjustment.id(), "SUBMITTED", "Soumission pour revue", adjustment.version());
            changed++;
        }
        LatestConduct conduct = latestConduct(reportingPeriodId, studentId);
        if (conduct != null && OPEN_ADJUSTMENT_STATES.contains(conduct.status())) {
            transitionConduct(conduct.id(), "SUBMITTED", "Soumission pour revue", conduct.version());
            changed++;
        }
        if (changed == 0) throw ApiException.conflict("Aucun brouillon d'assiduité ou de conseil à soumettre");
    }

    @Transactional
    public void reviewInputs(UUID reportingPeriodId, UUID classId, UUID studentId,
                             String action, String reason, Long attendanceVersion, Long conductVersion) {
        AcademicReportingPeriod period = period(reportingPeriodId);
        teacherScope.assertClass(classId);
        assertRoster(period, classId, studentId);
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVE", "RETURN", "REJECT").contains(normalized))
            throw ApiException.badRequest("Action invalide : utilisez APPROVE, RETURN ou REJECT");
        String reviewReason = "APPROVE".equals(normalized) ? optional(reason, 500) : required(reason, "Le motif du retour est obligatoire");
        String target = "APPROVE".equals(normalized) ? "APPROVED" : "RETURNED";
        int changed = 0;
        LatestAdjustment adjustment = latestAdjustment(reportingPeriodId, studentId);
        if (adjustment != null && "SUBMITTED".equals(adjustment.status())) {
            transitionAdjustment(adjustment.id(), target, reviewReason, attendanceVersion == null ? adjustment.version() : attendanceVersion);
            changed++;
        }
        LatestConduct conduct = latestConduct(reportingPeriodId, studentId);
        if (conduct != null && "SUBMITTED".equals(conduct.status())) {
            transitionConduct(conduct.id(), target, reviewReason, conductVersion == null ? conduct.version() : conductVersion);
            changed++;
        }
        if (changed == 0) throw ApiException.conflict("Aucun élément soumis à revoir ou version périmée");
    }

    @Transactional
    public AttendanceAdjustmentBatchResponse saveAdjustments(AttendanceAdjustmentBatchRequest request) {
        AcademicReportingPeriod period = period(request.reportingPeriodId());
        teacherScope.assertClass(request.classId());
        windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.REVIEW);
        List<AttendanceAdjustmentRowResult> results = request.rows().stream()
                .map(row -> saveAdjustmentRow(period, request.classId(), row)).toList();
        return new AttendanceAdjustmentBatchResponse(request.reportingPeriodId(), request.classId(), results,
                results.stream().allMatch(x -> "SAVED".equals(x.outcome()) || "UNCHANGED".equals(x.outcome())));
    }

    @Transactional
    public AttendanceAdjustmentRowResult saveAdjustmentRow(AcademicReportingPeriod period, UUID classId,
                                                            AttendanceAdjustmentRowRequest row) {
        UUID school = TenantContext.get();
        try {
            teacherScope.assertClass(classId);
            assertRoster(period, classId, row.studentId());
            BigDecimal justified = nonNegative(row.justifiedAbsenceHours());
            BigDecimal unjustified = nonNegative(row.unjustifiedAbsenceHours());
            int late = Math.max(0, row.lateMinutes() == null ? 0 : row.lateMinutes());
            String reason = required(row.reason(), "Le motif de correction est obligatoire");
            String evidence = optional(row.evidenceReference(), 240);
            LatestAdjustment latest = latestAdjustment(period.getId(), row.studentId());
            if (latest == null) {
                UUID id = insertAdjustment(period, row.studentId(), justified, unjustified, late, reason, evidence,
                        null, null, null);
                historyAdjustment(id, null, "DRAFT", reason, evidence, 0);
                return success(row.studentId(), id, "DRAFT", 0);
            }
            if ("SUBMITTED".equals(latest.status())) {
                return error(row.studentId(), latest.id(), latest.status(), latest.version(),
                        "status", "La correction est déjà soumise à la revue.",
                        "The adjustment is already submitted for review.", true);
            }
            if (APPROVED_STATES.contains(latest.status())) {
                windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.CORRECTION);
                String correctionReason = optional(row.correctionReason(), 500);
                if (correctionReason == null) {
                    return error(row.studentId(), latest.id(), latest.status(), latest.version(),
                            "correctionReason", "Une correction explicite est obligatoire après approbation/publication.",
                            "An explicit correction reason is required after approval/publication.", false);
                }
                String correctionEvidence = optional(row.correctionEvidenceReference(), 240);
                UUID id = insertAdjustment(period, row.studentId(), justified, unjustified, late, reason, evidence,
                        latest.id(), correctionReason, correctionEvidence);
                historyAdjustment(id, null, "DRAFT", correctionReason, correctionEvidence, 0);
                return success(row.studentId(), id, "DRAFT", 0);
            }
            if (!OPEN_ADJUSTMENT_STATES.contains(latest.status())) {
                return error(row.studentId(), latest.id(), latest.status(), latest.version(),
                        "status", "Cette correction ne peut plus être modifiée dans son état actuel.",
                        "This adjustment cannot be edited in its current state.", false);
            }
            long expected = row.version() == null ? latest.version() : row.version();
            int changed = jdbc.update("""
                    UPDATE attendance_period_adjustment
                       SET justified_absence_hours=?,unjustified_absence_hours=?,late_minutes=?,reason=?,
                           evidence_reference=?,status='DRAFT',updated_at=now(),version=version+1
                     WHERE school_id=? AND id=? AND status IN ('DRAFT','RETURNED','REJECTED') AND version=?
                    """, justified, unjustified, late, reason, evidence, school, latest.id, expected);
            if (changed == 0) {
                LatestAdjustment current = latestAdjustment(period.getId(), row.studentId());
                return error(row.studentId(), latest.id, current == null ? latest.status() : current.status(),
                        current == null ? latest.version() : current.version(), "version",
                        "La ligne a été modifiée entre-temps. Rechargez-la avant de réessayer.",
                        "The row changed meanwhile. Reload it before retrying.", true);
            }
            historyAdjustment(latest.id, latest.status(), "DRAFT", reason, evidence, expected + 1);
            return success(row.studentId(), latest.id, "DRAFT", expected + 1);
        } catch (ApiException ex) {
            return error(row.studentId(), null, "", 0, "row", ex.getMessage(), ex.getMessage(), false);
        }
    }

    @Transactional
    public void transitionAdjustment(UUID adjustmentId, String toStatus, String reason, Long version) {
        LatestAdjustment current = adjustment(adjustmentId);
        if (current == null) throw ApiException.notFound("Correction d'assiduité");
        UUID reportingPeriodId = jdbc.query("SELECT reporting_period_id FROM attendance_period_adjustment WHERE school_id=? AND id=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, TenantContext.get(), adjustmentId);
        if (reportingPeriodId != null) windows.assertOpen(reportingPeriodId, AcademicWindowPolicyService.Action.REVIEW);
        String target = toStatus == null ? "" : toStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SUBMITTED", "APPROVED", "RETURNED", "REJECTED").contains(target))
            throw ApiException.badRequest("État de correction invalide");
        if (version != null && current.version() != version)
            throw ApiException.staleVersion("La correction a été modifiée entre-temps.", current.version(), version);
        if ("SUBMITTED".equals(target) && !OPEN_ADJUSTMENT_STATES.contains(current.status()))
            throw ApiException.conflict("Seul un brouillon retourné peut être soumis.");
        if (Set.of("APPROVED", "RETURNED", "REJECTED").contains(target)
                && !"SUBMITTED".equals(current.status()))
            throw ApiException.conflict("Seule une correction soumise peut être revue.");
        String normalized = "REJECTED".equals(target) ? "RETURNED" : target;
        int changed = jdbc.update("""
                UPDATE attendance_period_adjustment
                   SET status=?,submitted_by=CASE WHEN ?='SUBMITTED' THEN ? ELSE submitted_by END,
                       submitted_at=CASE WHEN ?='SUBMITTED' THEN now() ELSE submitted_at END,
                       reviewed_by=CASE WHEN ? IN ('APPROVED','RETURNED') THEN ? ELSE reviewed_by END,
                       reviewed_at=CASE WHEN ? IN ('APPROVED','RETURNED') THEN now() ELSE reviewed_at END,
                       returned_by=CASE WHEN ?='RETURNED' THEN ? ELSE returned_by END,
                       return_reason=CASE WHEN ?='RETURNED' THEN ? ELSE return_reason END,
                       version=version+1,updated_at=now()
                 WHERE school_id=? AND id=? AND version=?
                """, normalized, target, actorId(), target, target, actorId(), target,
                target, actorId(), target, trim(reason), TenantContext.get(), adjustmentId, current.version());
        if (changed == 0) throw ApiException.conflict("La correction a été modifiée entre-temps.");
        historyAdjustment(adjustmentId, current.status(), normalized, reason, null, current.version() + 1);
    }

    private void transitionConduct(UUID conductId, String toStatus, String reason, Long version) {
        LatestConduct current = jdbc.query("""
                SELECT id,status,version,decision_code,override_reason
                  FROM student_period_conduct WHERE school_id=? AND id=?
                """, rs -> rs.next() ? new LatestConduct(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getLong(3), rs.getString(4), rs.getString(5)) : null, TenantContext.get(), conductId);
        if (current == null) throw ApiException.notFound("Décision du conseil");
        String target = toStatus == null ? "" : toStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SUBMITTED", "APPROVED", "RETURNED", "REJECTED").contains(target))
            throw ApiException.badRequest("État de décision invalide");
        if (version != null && current.version() != version)
            throw ApiException.staleVersion("La fiche du conseil a été modifiée entre-temps.", current.version(), version);
        if ("SUBMITTED".equals(target) && !OPEN_ADJUSTMENT_STATES.contains(current.status()))
            throw ApiException.conflict("Seul un brouillon retourné peut être soumis.");
        if (Set.of("APPROVED", "RETURNED", "REJECTED").contains(target) && !"SUBMITTED".equals(current.status()))
            throw ApiException.conflict("Seule une décision soumise peut être revue.");
        String normalized = "REJECTED".equals(target) ? "RETURNED" : target;
        int changed = jdbc.update("""
                UPDATE student_period_conduct
                   SET status=?,submitted_by=CASE WHEN ?='SUBMITTED' THEN ? ELSE submitted_by END,
                       submitted_at=CASE WHEN ?='SUBMITTED' THEN now() ELSE submitted_at END,
                       reviewed_by=CASE WHEN ? IN ('APPROVED','RETURNED') THEN ? ELSE reviewed_by END,
                       reviewed_at=CASE WHEN ? IN ('APPROVED','RETURNED') THEN now() ELSE reviewed_at END,
                       returned_by=CASE WHEN ?='RETURNED' THEN ? ELSE returned_by END,
                       return_reason=CASE WHEN ?='RETURNED' THEN ? ELSE return_reason END,
                       version=version+1,updated_at=now()
                 WHERE school_id=? AND id=? AND version=?
                """, normalized, target, actorId(), target, target, actorId(), target,
                target, actorId(), target, trim(reason), TenantContext.get(), conductId, current.version());
        if (changed == 0) throw ApiException.conflict("La fiche du conseil a été modifiée entre-temps.");
        historyConduct(conductId, current.status(), normalized, reason, current.overrideReason(), current.version() + 1);
    }

    @Transactional(readOnly = true)
    public List<AttendanceWorkflowHistoryView> adjustmentHistory(UUID adjustmentId) {
        return jdbc.query("""
                SELECT id,adjustment_id,from_status,to_status,actor_user_id,actor_username,reason,
                       evidence_reference,source_version,occurred_at
                  FROM attendance_period_adjustment_history
                 WHERE school_id=? AND adjustment_id=? ORDER BY occurred_at,id
                """, (rs, n) -> new AttendanceWorkflowHistoryView(rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                        rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7),
                        rs.getString(8), rs.getLong(9), instant(rs.getObject(10))),
                TenantContext.get(), adjustmentId);
    }

    /** Lock approved report-card inputs only after the bulletin is published. */
    @Transactional
    public void lockForPublication(UUID reportingPeriodId, UUID studentId, UUID bulletinVersionId) {
        UUID school = TenantContext.get();
        List<UUID> adjustments = jdbc.query("""
                SELECT id FROM attendance_period_adjustment
                 WHERE school_id=? AND reporting_period_id=? AND student_id=? AND status='APPROVED'
                """, (rs, n) -> rs.getObject(1, UUID.class), school, reportingPeriodId, studentId);
        for (UUID id : adjustments) {
            LatestAdjustment current = adjustment(id);
            int changed = jdbc.update("""
                    UPDATE attendance_period_adjustment
                       SET status='LOCKED_BY_PUBLICATION',locked_by_publication_id=?,locked_at=now(),
                           version=version+1,updated_at=now()
                     WHERE school_id=? AND id=? AND status='APPROVED' AND version=?
                    """, bulletinVersionId, school, id, current.version());
            if (changed > 0) historyAdjustment(id, "APPROVED", "LOCKED_BY_PUBLICATION",
                    "Bulletin publié", null, current.version() + 1);
        }
        LatestConduct conduct = latestConduct(reportingPeriodId, studentId);
        if (conduct != null && "APPROVED".equals(conduct.status())) {
            int changed = jdbc.update("""
                    UPDATE student_period_conduct
                       SET status='LOCKED_BY_PUBLICATION',locked_by_publication_id=?,locked_at=now(),
                           version=version+1,updated_at=now()
                     WHERE school_id=? AND id=? AND status='APPROVED' AND version=?
                    """, bulletinVersionId, school, conduct.id(), conduct.version());
            if (changed > 0) historyConduct(conduct.id(), "APPROVED", "LOCKED_BY_PUBLICATION",
                    "Bulletin publié", conduct.overrideReason(), conduct.version() + 1);
        }
    }

    /**
     * Freeze the exact attendance evidence paired with a bulletin version.  A
     * correction inserts a new row; no prior official row is updated or deleted.
     */
    @Transactional
    public void freezeOfficialSnapshot(AcademicReportingPeriod period, UUID studentId,
                                       UUID bulletinVersionId, AttendanceSummaryView attendance) {
        EnrollmentInfo enrollment = enrollment(period, studentId);
        if (enrollment == null) return;
        UUID school = TenantContext.get();
        UUID previous = jdbc.query("""
                SELECT id FROM attendance_official_snapshot
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                 ORDER BY snapshot_version DESC,created_at DESC LIMIT 1
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                school, period.getId(), studentId);
        Long nextVersion = jdbc.queryForObject("""
                SELECT COALESCE(max(snapshot_version),0)+1 FROM attendance_official_snapshot
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                """, Long.class, school, period.getId(), studentId);
        String fingerprint = sha256(period.getId() + ":" + attendance.policyVersion() + ":"
                + attendance.sourceRollCallIds() + ":" + attendance.annualEvidenceVersion());
        jdbc.update("""
                INSERT INTO attendance_official_snapshot
                (school_id,academic_session_id,reporting_period_id,student_id,enrollment_id,class_id,
                 period_type,snapshot_version,bulletin_version_id,supersedes_snapshot_id,source_fingerprint,
                 policy_version,expected_session_count,expected_hours,finalized_session_count,finalized_hours,
                 coverage_percent,present_count,absent_count,excused_count,late_count,total_absence_minutes,
                 justified_absence_minutes,unjustified_absence_minutes,late_minutes,exclusion_days,
                 source_roll_call_ids,source_snapshot_ids,missing_sessions,approved_adjustments,raw_values,
                 display_values,blockers,warnings,created_by)
                VALUES (?,?,?,?,?,?,?,?,?, ?,?,?,?,?,?,?,?,?,?, ?,?,?,?,?,?,?, ?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?)
                """, school, period.getAcademicSessionId(), period.getId(), studentId, enrollment.id(),
                enrollment.classId(), period.getPeriodType(), nextVersion, bulletinVersionId, previous,
                fingerprint, attendance.policyVersion(), attendance.expectedSessionCount(),
                nvl(attendance.rawValues() == null ? attendance.expectedHours() : attendance.rawValues().expectedHours()),
                attendance.finalizedSessions(), nvl(attendance.rawValues() == null ? attendance.finalizedHours() : attendance.rawValues().finalizedHours()),
                nvl(attendance.rawValues() == null ? attendance.coveragePercent() : attendance.rawValues().coveragePercent()),
                attendance.presentCount(), attendance.absentCount(), attendance.excusedCount(), attendance.lateCount(),
                nvl(attendance.totalAbsenceMinutes()), nvl(attendance.justifiedAbsenceMinutes()),
                nvl(attendance.unjustifiedAbsenceMinutes()), nvl(BigDecimal.valueOf(attendance.lateMinutes())),
                attendance.exclusionDays(), json(attendance.sourceRollCallIds()), json(snapshotIds(attendance)),
                json(attendance.missingSessions()), json(attendance.approvedAdjustments()), json(attendance.rawValues()),
                json(attendance.displayValues()), json(attendance.blockers()), json(attendance.warnings()), actorId());
    }

    private AttendanceSummaryView periodAggregate(AcademicReportingPeriod period, UUID studentId,
                                                  EnrollmentInfo enrollment) {
        List<SessionRow> rows = sessionRows(period, enrollment.classId());
        Map<UUID, MarkRow> marks = marks(period, studentId);
        Accumulator a = new Accumulator();
        for (SessionRow row : rows) {
            DurationValue duration = duration(row, period.getAcademicSessionId());
            a.expectedSessions++;
            if (duration.minutes() != null && duration.minutes() > 0) {
                a.expectedMinutes = a.expectedMinutes.add(BigDecimal.valueOf(duration.minutes()));
            } else {
                a.durationMissing = true;
                a.issue(new AttendanceReadinessIssueView("ATTENDANCE_DURATION_MISSING", "BLOCKER", studentId,
                        row.date(), row.expectedId(), row.rollCallId(),
                        "La durée configurée de cette séance est manquante ou nulle.",
                        "The configured duration for this session is missing or zero.",
                        attendanceRepair(period.getId(), enrollment.classId(), row.date(), row.periodKey())));
            }
            MarkRow mark = row.rollCallId() == null ? null : marks.get(row.rollCallId());
            boolean finalized = "FINALIZED".equals(row.status());
            if (!finalized || mark == null || "UNMARKED".equals(mark.status())) {
                String code = row.rollCallId() == null ? "ATTENDANCE_ROLL_CALL_MISSING" : "ATTENDANCE_SESSION_UNFINALIZED";
                a.missing.add(new AttendanceSessionEvidenceView(row.expectedId(), row.rollCallId(), row.date(),
                        row.model(), row.periodKey(), row.subjectCode(), row.status(), row.cancelled(),
                        duration.minutes(), hours(duration.minutes()), code,
                        attendanceRepair(period.getId(), enrollment.classId(), row.date(), row.periodKey())));
                a.issue(new AttendanceReadinessIssueView("ATTENDANCE_COVERAGE_INCOMPLETE", "BLOCKER", studentId,
                        row.date(), row.expectedId(), row.rollCallId(),
                        "La séance attendue n'est pas finalisée pour cet élève.",
                        "The expected session is not finalized for this student.",
                        attendanceRepair(period.getId(), enrollment.classId(), row.date(), row.periodKey())));
                continue;
            }
            a.finalizedSessions++;
            if (duration.minutes() != null && duration.minutes() > 0)
                a.finalizedMinutes = a.finalizedMinutes.add(BigDecimal.valueOf(duration.minutes()));
            a.sourceRollCallIds.add(row.rollCallId());
            switch (mark.status()) {
                case "PRESENT" -> a.present++;
                case "ABSENT" -> { a.absent++; a.baseUnjustifiedMinutes = a.baseUnjustifiedMinutes.add(value(duration.minutes())); }
                case "EXCUSED" -> { a.excused++; a.baseJustifiedMinutes = a.baseJustifiedMinutes.add(value(duration.minutes())); }
                case "LATE" -> { a.late++; a.rawLateMinutes += Math.max(0, mark.lateMinutes()); }
                default -> { /* finalized unmarked is handled above */ }
            }
            if ("LATE".equals(mark.status())) a.rawLateMinutes += 0;
            if (mark.reason() == null && Set.of("ABSENT", "EXCUSED").contains(mark.status())) {
                a.warning(new AttendanceReadinessIssueView("ATTENDANCE_JUSTIFICATION_PENDING", "WARNING", studentId,
                        row.date(), row.expectedId(), row.rollCallId(),
                        "Le motif de l'absence est manquant.", "The absence reason is missing.",
                        attendanceRepair(period.getId(), enrollment.classId(), row.date(), row.periodKey())));
            }
        }
        if (a.expectedSessions == 0) {
            a.warning(new AttendanceReadinessIssueView("ATTENDANCE_NO_EXPECTED_SESSIONS", "WARNING", studentId,
                    null, null, null, "Aucune séance attendue n'est configurée pour cette période.",
                    "No expected attendance session is configured for this period.",
                    "/settings?section=calendar"));
        }
        AdjustmentTotals adjustments = adjustmentTotals(period.getId(), studentId);
        BigDecimal moved = adjustments.justifiedHours().multiply(SIXTY).min(a.baseUnjustifiedMinutes.max(BigDecimal.ZERO));
        BigDecimal additiveUnjustified = adjustments.unjustifiedHours().multiply(SIXTY);
        BigDecimal justifiedMinutes = a.baseJustifiedMinutes.add(moved);
        BigDecimal unjustifiedMinutes = a.baseUnjustifiedMinutes.subtract(moved).max(BigDecimal.ZERO).add(additiveUnjustified);
        BigDecimal totalAbsenceMinutes = justifiedMinutes.add(unjustifiedMinutes);
        addWorkflowIssues(a, period, studentId, enrollment.classId());
        return summary(a, adjustments, period, studentId, justifiedMinutes, unjustifiedMinutes, totalAbsenceMinutes,
                null, false);
    }

    private AttendanceSummaryView annualAggregate(AcademicReportingPeriod annual, UUID studentId,
                                                   EnrollmentInfo enrollment) {
        List<OfficialPart> parts = jdbc.query("""
                SELECT DISTINCT ON (o.reporting_period_id)
                       o.id,o.reporting_period_id,p.code,o.snapshot_version,o.source_fingerprint,
                       o.expected_session_count,o.expected_hours,o.finalized_session_count,o.finalized_hours,
                       o.coverage_percent,o.present_count,o.absent_count,o.excused_count,o.late_count,
                       o.total_absence_minutes,o.justified_absence_minutes,o.unjustified_absence_minutes,
                       o.late_minutes,o.exclusion_days,o.source_roll_call_ids,o.source_snapshot_ids,
                       o.missing_sessions,o.approved_adjustments,o.policy_version
                  FROM attendance_official_snapshot o
                  JOIN academic_reporting_period p ON p.id=o.reporting_period_id
                 WHERE o.school_id=? AND o.academic_session_id=? AND o.student_id=?
                   AND p.period_type='TERM_RESULT'
                   AND (p.code IN ('T1','T2','T3','T1_RESULT','T2_RESULT','T3_RESULT'))
                 ORDER BY o.reporting_period_id,o.snapshot_version DESC,o.created_at DESC
                """, (rs, n) -> new OfficialPart(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getLong(4), rs.getString(5), rs.getInt(6), rs.getBigDecimal(7),
                        rs.getInt(8), rs.getBigDecimal(9), rs.getBigDecimal(10), rs.getInt(11), rs.getInt(12),
                        rs.getInt(13), rs.getInt(14), rs.getBigDecimal(15), rs.getBigDecimal(16),
                        rs.getBigDecimal(17), rs.getBigDecimal(18), rs.getInt(19), rs.getString(20),
                        rs.getString(21), rs.getString(22), rs.getString(23), rs.getString(24)),
                TenantContext.get(), annual.getAcademicSessionId(), studentId);
        Map<String, OfficialPart> byTerm = new LinkedHashMap<>();
        for (OfficialPart part : parts) byTerm.put(termCode(part.code()), part);
        Accumulator a = new Accumulator();
        List<String> evidenceVersions = new ArrayList<>();
        List<UUID> sourceSnapshots = new ArrayList<>();
        for (String term : List.of("T1", "T2", "T3")) {
            OfficialPart part = byTerm.get(term);
            if (part == null) {
                a.issue(new AttendanceReadinessIssueView("ANNUAL_TERM_SNAPSHOT_MISSING", "BLOCKER", studentId,
                        null, null, null, "La preuve officielle de " + term + " est manquante.",
                        "The official " + term + " evidence is missing.",
                        "/academic?mode=inputs&reportingPeriodId=" + annual.getId() + "&classId=" + enrollment.classId()));
                continue;
            }
            a.expectedSessions += part.expectedSessions();
            a.expectedMinutes = a.expectedMinutes.add(hoursToMinutes(part.expectedHours()));
            if (part.expectedSessions() > 0 && nvl(part.expectedHours()).signum() == 0) a.durationMissing = true;
            a.finalizedSessions += part.finalizedSessions();
            a.finalizedMinutes = a.finalizedMinutes.add(hoursToMinutes(part.finalizedHours()));
            a.present += part.present(); a.absent += part.absent(); a.excused += part.excused(); a.late += part.late();
            a.rawLateMinutes += part.lateMinutes().setScale(0, RoundingMode.HALF_UP).intValue();
            a.totalOfficialAbsenceMinutes = a.totalOfficialAbsenceMinutes.add(part.totalAbsenceMinutes());
            a.justifiedOfficialMinutes = a.justifiedOfficialMinutes.add(part.justifiedMinutes());
            a.unjustifiedOfficialMinutes = a.unjustifiedOfficialMinutes.add(part.unjustifiedMinutes());
            a.exclusionDays += part.exclusionDays();
            a.sourceRollCallIds.addAll(uuidList(part.sourceRollCallIds()));
            a.missing.addAll(sessionEvidenceList(part.missingSessions()));
            evidenceVersions.add(term + ":v" + part.snapshotVersion());
            sourceSnapshots.add(part.id());
            a.policyVersions.add(part.policyVersion());
        }
        String evidenceVersion = String.join("|", evidenceVersions);
        String fingerprint = sha256(annual.getId() + ":" + String.join("|", a.policyVersions) + ":"
                + a.sourceRollCallIds + ":" + String.join("|", evidenceVersions) + ":" + sourceSnapshots);
        UUID existingAnnual = jdbc.query("""
                SELECT id FROM attendance_official_snapshot
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                 ORDER BY snapshot_version DESC,created_at DESC LIMIT 1
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), annual.getId(), studentId);
        String existingFingerprint = existingAnnual == null ? null : jdbc.queryForObject(
                "SELECT source_fingerprint FROM attendance_official_snapshot WHERE school_id=? AND id=?",
                String.class, TenantContext.get(), existingAnnual);
        boolean draftRequired = existingAnnual != null && !Objects.equals(existingFingerprint, fingerprint);
        if (draftRequired) a.issue(new AttendanceReadinessIssueView("ANNUAL_ATTENDANCE_DRAFT_REQUIRED", "BLOCKER", studentId,
                null, null, null, "Eine T1/T2/T3-Korrektur erfordert eine neue Jahresversion.",
                "A T1/T2/T3 correction requires a new annual draft/version.",
                "/academic?mode=inputs&reportingPeriodId=" + annual.getId() + "&classId=" + enrollment.classId()));
        BigDecimal total = a.totalOfficialAbsenceMinutes;
        BigDecimal justified = a.justifiedOfficialMinutes;
        BigDecimal unjustified = a.unjustifiedOfficialMinutes;
        addWorkflowIssues(a, annual, studentId, enrollment.classId());
        AdjustmentTotals noAdjustments = new AdjustmentTotals(BigDecimal.ZERO, BigDecimal.ZERO, 0, List.of());
        return summary(a, noAdjustments, annual, studentId, justified, unjustified, total,
                evidenceVersion, draftRequired, sourceSnapshots);
    }

    private AttendanceSummaryView summary(Accumulator a, AdjustmentTotals adjustments,
                                           AcademicReportingPeriod period, UUID studentId,
                                           BigDecimal justifiedMinutes, BigDecimal unjustifiedMinutes,
                                           BigDecimal totalAbsenceMinutes, String annualEvidenceVersion,
                                           boolean annualDraftRequired) {
        return summary(a, adjustments, period, studentId, justifiedMinutes, unjustifiedMinutes,
                totalAbsenceMinutes, annualEvidenceVersion, annualDraftRequired, List.of());
    }

    private AttendanceSummaryView summary(Accumulator a, AdjustmentTotals adjustments,
                                          AcademicReportingPeriod period, UUID studentId,
                                          BigDecimal justifiedMinutes, BigDecimal unjustifiedMinutes,
                                          BigDecimal totalAbsenceMinutes, String annualEvidenceVersion,
                                          boolean annualDraftRequired, List<UUID> sourceSnapshotIds) {
        BigDecimal expectedHours = a.durationMissing ? null
                : a.expectedMinutes.divide(SIXTY, 6, RoundingMode.HALF_UP);
        BigDecimal finalizedHours = a.finalizedMinutes.divide(SIXTY, 6, RoundingMode.HALF_UP);
        BigDecimal coverage = a.expectedSessions == 0 ? BigDecimal.ZERO.setScale(6)
                : BigDecimal.valueOf(a.finalizedSessions).multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(a.expectedSessions), 6, RoundingMode.HALF_UP);
        BigDecimal late = BigDecimal.valueOf(a.rawLateMinutes).add(BigDecimal.valueOf(adjustments.lateMinutes()));
        BigDecimal exclusion = BigDecimal.valueOf(a.exclusionDays);
        AttendanceMetricValues raw = new AttendanceMetricValues(expectedHours, finalizedHours, coverage,
                totalAbsenceMinutes, totalAbsenceMinutes.divide(SIXTY, 6, RoundingMode.HALF_UP),
                justifiedMinutes, justifiedMinutes.divide(SIXTY, 6, RoundingMode.HALF_UP),
                unjustifiedMinutes, unjustifiedMinutes.divide(SIXTY, 6, RoundingMode.HALF_UP), late, exclusion);
        AttendanceMetricValues display = display(raw);
        return new AttendanceSummaryView(a.finalizedSessions, a.present, a.absent, a.excused, a.late,
                late.setScale(0, RoundingMode.HALF_UP).intValue(), raw.justifiedAbsenceHours(),
                raw.unjustifiedAbsenceHours(), adjustments.justifiedHours(), adjustments.unjustifiedHours(),
                adjustments.lateMinutes(), a.expectedSessions, expectedHours, finalizedHours, coverage,
                a.missing, a.sourceRollCallIds, totalAbsenceMinutes, raw.totalAbsenceHours(),
                justifiedMinutes, unjustifiedMinutes, a.exclusionDays, adjustments.evidence(),
                String.join("|", a.policyVersions.isEmpty() ? List.of(policyVersion(period, studentId)) : a.policyVersions),
                a.blockers, a.warnings, raw, display, annualEvidenceVersion, annualDraftRequired, sourceSnapshotIds);
    }

    private AttendanceMetricValues display(AttendanceMetricValues raw) {
        if (raw == null) return null;
        return new AttendanceMetricValues(round(raw.expectedHours()), round(raw.finalizedHours()), round(raw.coveragePercent()),
                round(raw.totalAbsenceMinutes()), round(raw.totalAbsenceHours()), round(raw.justifiedAbsenceMinutes()),
                round(raw.justifiedAbsenceHours()), round(raw.unjustifiedAbsenceMinutes()), round(raw.unjustifiedAbsenceHours()),
                round(raw.lateMinutes()), round(raw.exclusionDays()));
    }

    private void addWorkflowIssues(Accumulator a, AcademicReportingPeriod period, UUID studentId, UUID classId) {
        LatestAdjustment latest = latestAdjustment(period.getId(), studentId);
        if (latest != null && Set.of("DRAFT", "SUBMITTED", "RETURNED", "REJECTED").contains(latest.status())) {
            a.issue(new AttendanceReadinessIssueView("ATTENDANCE_ADJUSTMENT_PENDING", "BLOCKER", studentId,
                    null, null, null, "Une correction d'assiduité doit être traitée par la revue.",
                    "An attendance adjustment is pending review.",
                    "/academic?mode=inputs&reportingPeriodId=" + period.getId() + "&classId=" + classId + "&studentId=" + studentId));
        }
        LatestConduct conduct = latestConduct(period.getId(), studentId);
        if (conduct == null || (!APPROVED_STATES.contains(conduct.status()))
                || (recommendationPolicy().requireDecisionCode() && (conduct.decisionCode() == null || conduct.decisionCode().isBlank()))) {
            a.issue(new AttendanceReadinessIssueView("COUNCIL_DECISION_INCOMPLETE", "BLOCKER", studentId,
                    null, null, null, "La décision approuvée du conseil de classe est incomplète.",
                    "The approved class-council decision is incomplete.",
                    "/academic?mode=inputs&reportingPeriodId=" + period.getId() + "&classId=" + classId + "&studentId=" + studentId));
        }
    }

    private List<SessionRow> sessionRows(AcademicReportingPeriod period, UUID classId) {
        UUID school = TenantContext.get();
        List<SessionRow> rows = jdbc.query("""
                SELECT e.id expected_id,a.id roll_call_id,e.session_date,e.model,e.period_key,
                       a.subject_code,COALESCE(a.status,'MISSING') status,
                       COALESCE(a.cancelled,e.cancelled) cancelled,COALESCE(a.version,0) version,
                       COALESCE(a.duration_minutes,0) duration_minutes
                  FROM expected_school_session e
                  LEFT JOIN attendance_session a ON a.expected_session_id=e.id AND a.school_id=e.school_id
                 WHERE e.school_id=? AND e.academic_session_id=? AND e.school_class_id=?
                   AND e.session_date BETWEEN ? AND ? AND e.cancelled=false
                   AND COALESCE(a.cancelled,false)=false
                 ORDER BY e.session_date,e.period_key
                """, (rs, n) -> new SessionRow(rs.getObject("expected_id", UUID.class),
                        rs.getObject("roll_call_id", UUID.class), rs.getObject("session_date", LocalDate.class),
                        rs.getString("model"), rs.getString("period_key"), rs.getString("subject_code"),
                        rs.getString("status"), rs.getBoolean("cancelled"), rs.getLong("version"),
                        rs.getInt("duration_minutes")), school, period.getAcademicSessionId(), classId,
                Date.valueOf(period.getStartDate()), Date.valueOf(period.getEndDate()));
        rows.addAll(jdbc.query("""
                SELECT a.expected_session_id,a.id,a.session_date,a.model,a.period_key,a.subject_code,
                       a.status,a.cancelled,a.version,a.duration_minutes
                  FROM attendance_session a
                 WHERE a.school_id=? AND a.academic_session_id=? AND a.school_class_id=?
                   AND a.session_date BETWEEN ? AND ? AND a.cancelled=false
                   AND NOT EXISTS (SELECT 1 FROM expected_school_session e WHERE e.id=a.expected_session_id)
                 ORDER BY a.session_date,a.period_key
                """, (rs, n) -> new SessionRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, LocalDate.class), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getBoolean(8), rs.getLong(9), rs.getInt(10)), school,
                period.getAcademicSessionId(), classId, Date.valueOf(period.getStartDate()), Date.valueOf(period.getEndDate())));
        rows.sort(Comparator.comparing(SessionRow::date).thenComparing(SessionRow::periodKey, Comparator.nullsFirst(String::compareTo)));
        return rows;
    }

    private Map<UUID, MarkRow> marks(AcademicReportingPeriod period, UUID studentId) {
        return jdbc.query("""
                SELECT m.attendance_session_id,m.status,m.late_minutes,m.source,m.reason,m.note
                  FROM attendance_mark m JOIN attendance_session a ON a.id=m.attendance_session_id
                 WHERE m.school_id=? AND m.student_id=? AND a.academic_session_id=?
                   AND a.session_date BETWEEN ? AND ?
                """, (rs, n) -> new MarkRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3),
                        rs.getString(4), rs.getString(5), rs.getString(6)), TenantContext.get(), studentId,
                period.getAcademicSessionId(), Date.valueOf(period.getStartDate()), Date.valueOf(period.getEndDate()))
                .stream().collect(Collectors.toMap(MarkRow::rollCallId, x -> x, (a, b) -> b));
    }

    private DurationValue duration(SessionRow row, UUID academicSessionId) {
        if ("DAILY".equalsIgnoreCase(row.model())) {
            DurationValue configured = jdbc.query("""
                    SELECT EXTRACT(EPOCH FROM (end_time-start_time))/60
                      FROM school_calendar_day
                     WHERE school_id=? AND academic_session_id=? AND day_of_week=? AND teaching_day
                       AND start_time IS NOT NULL AND end_time IS NOT NULL AND end_time>start_time
                    """, rs -> rs.next() && rs.getObject(1) != null ? new DurationValue(rs.getInt(1), "SCHOOL_CALENDAR_DAY") : null,
                    TenantContext.get(), academicSessionId, row.date().getDayOfWeek().getValue());
            return configured;
        }
        Integer slot = parsePeriod(row.periodKey());
        if (slot != null) {
            DurationValue configured = jdbc.query("""
                    SELECT EXTRACT(EPOCH FROM (end_time-start_time))/60
                      FROM timetable_period
                     WHERE school_id=? AND slot_idx=? AND active AND end_time>start_time
                    """, rs -> rs.next() && rs.getObject(1) != null ? new DurationValue(rs.getInt(1), "TIMETABLE_PERIOD") : null,
                    TenantContext.get(), slot);
            if (configured != null) return configured;
        }
        return null;
    }

    private List<AttendanceSourceBreakdownView> annualSourceBreakdown(AcademicReportingPeriod annual, UUID studentId) {
        return jdbc.query("""
                SELECT o.id,p.code,o.source_roll_call_ids
                  FROM attendance_official_snapshot o
                  JOIN academic_reporting_period p ON p.id=o.reporting_period_id
                 WHERE o.school_id=? AND o.academic_session_id=? AND o.student_id=?
                   AND p.period_type='TERM_RESULT'
                 ORDER BY p.display_order
                """, (rs, n) -> new AttendanceSourceBreakdownView(null, rs.getObject(1, UUID.class), studentId,
                        null, "OFFICIAL_SNAPSHOT", rs.getString(2), null, "OFFICIAL", "SNAPSHOT", 0,
                        BigDecimal.ZERO, 0, "OFFICIAL_SNAPSHOT", null, null, false, 0),
                TenantContext.get(), annual.getAcademicSessionId(), studentId);
    }

    private AdjustmentTotals adjustmentTotals(UUID periodId, UUID studentId) {
        List<AdjustmentRow> rows = jdbc.query("""
                SELECT a.id,a.justified_absence_hours,a.unjustified_absence_hours,a.late_minutes,a.reason,
                       a.evidence_reference,a.status,a.version,a.created_by,u.username,a.created_at,
                       a.corrects_adjustment_id
                  FROM attendance_period_adjustment a
                  LEFT JOIN app_user u ON u.id=a.created_by
                 WHERE a.school_id=? AND a.reporting_period_id=? AND a.student_id=?
                   AND a.status IN ('APPROVED','LOCKED_BY_PUBLICATION')
                   AND NOT EXISTS (SELECT 1 FROM attendance_period_adjustment newer
                                    WHERE newer.corrects_adjustment_id=a.id
                                      AND newer.status IN ('APPROVED','LOCKED_BY_PUBLICATION'))
                 ORDER BY a.created_at,a.id
                """, (rs, n) -> new AdjustmentRow(rs.getObject(1, UUID.class), rs.getBigDecimal(2),
                        rs.getBigDecimal(3), rs.getInt(4), rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getLong(8), rs.getObject(9, UUID.class), rs.getString(10), instant(rs.getObject(11)),
                        rs.getObject(12, UUID.class)), TenantContext.get(), periodId, studentId);
        BigDecimal justified = rows.stream().map(AdjustmentRow::justified).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unjustified = rows.stream().map(AdjustmentRow::unjustified).reduce(BigDecimal.ZERO, BigDecimal::add);
        int late = rows.stream().mapToInt(AdjustmentRow::late).sum();
        List<AttendanceAdjustmentEvidenceView> evidence = rows.stream().map(x -> new AttendanceAdjustmentEvidenceView(
                x.id(), x.justified(), x.unjustified(), x.late(), x.reason(), x.evidence(), x.status(), x.version(),
                x.actorId(), x.actorUsername(), x.createdAt(), x.justified().signum() > 0 && x.unjustified().signum() == 0,
                x.correctsId())).toList();
        return new AdjustmentTotals(justified, unjustified, late, evidence);
    }

    private LatestAdjustment latestAdjustment(UUID periodId, UUID studentId) {
        return jdbc.query("""
                SELECT id,status,version FROM attendance_period_adjustment
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                 ORDER BY created_at DESC,id DESC LIMIT 1
                """, rs -> rs.next() ? new LatestAdjustment(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)) : null,
                TenantContext.get(), periodId, studentId);
    }

    private LatestAdjustment adjustment(UUID id) {
        return jdbc.query("SELECT id,status,version FROM attendance_period_adjustment WHERE school_id=? AND id=?",
                rs -> rs.next() ? new LatestAdjustment(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)) : null,
                TenantContext.get(), id);
    }

    private LatestConduct latestConduct(UUID periodId, UUID studentId) {
        return jdbc.query("""
                SELECT id,status,version,decision_code,override_reason
                  FROM student_period_conduct
                 WHERE school_id=? AND reporting_period_id=? AND student_id=?
                """, rs -> rs.next() ? new LatestConduct(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getLong(3), rs.getString(4), rs.getString(5)) : null,
                TenantContext.get(), periodId, studentId);
    }

    private UUID insertAdjustment(AcademicReportingPeriod period, UUID studentId, BigDecimal justified,
                                  BigDecimal unjustified, int late, String reason, String evidence,
                                  UUID corrects, String correctionReason, String correctionEvidence) {
        return jdbc.queryForObject("""
                INSERT INTO attendance_period_adjustment
                (school_id,academic_session_id,reporting_period_id,student_id,justified_absence_hours,
                 unjustified_absence_hours,late_minutes,reason,evidence_reference,status,created_by,
                 corrects_adjustment_id,correction_reason,correction_evidence_reference)
                VALUES (?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?,?) RETURNING id
                """, UUID.class, TenantContext.get(), period.getAcademicSessionId(), period.getId(), studentId,
                justified, unjustified, late, reason, evidence, actorId(), corrects, correctionReason, correctionEvidence);
    }

    private void historyAdjustment(UUID id, String from, String to, String reason, String evidence, long version) {
        jdbc.update("""
                INSERT INTO attendance_period_adjustment_history
                (school_id,adjustment_id,from_status,to_status,actor_user_id,actor_username,reason,evidence_reference,source_version)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, TenantContext.get(), id, from, to, actorId(), actorUsername(), trim(reason), trim(evidence), version);
    }

    private void historyConduct(UUID id, String from, String to, String reason, String overrideReason, long version) {
        jdbc.update("""
                INSERT INTO student_period_conduct_history
                (school_id,conduct_id,from_status,to_status,actor_user_id,actor_username,reason,override_reason,source_version)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, TenantContext.get(), id, from, to, actorId(), actorUsername(), trim(reason), trim(overrideReason), version);
    }

    private boolean sameDecision(ReportCardInputUpsert input, ConductRecommendationView recommendation, int exclusionDays) {
        if (recommendation == null) return false;
        return input.workWarning() == recommendation.workWarning()
                && input.workBlame() == recommendation.workBlame()
                && input.conductWarning() == recommendation.conductWarning()
                && input.conductBlame() == recommendation.conductBlame()
                && input.honorRoll() == recommendation.honorRoll()
                && input.encouragement() == recommendation.encouragement()
                && input.congratulations() == recommendation.congratulations()
                && exclusionDays == recommendation.exclusionDays();
    }

    private boolean canOverrideCouncil() {
        Object principal = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(x -> x.getPrincipal()).orElse(null);
        if (!(principal instanceof AppUserPrincipal p)) return false;
        return Set.of("principal", "prefect", "dean_of_studies", "censor", "administrator", "admin", "school_admin").contains(p.roleCode());
    }

    private void assertRoster(AcademicReportingPeriod period, UUID classId, UUID studentId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM student_enrollment
                 WHERE school_id=? AND academic_session_id=? AND school_class_id=? AND student_id=? AND status='ACTIVE'
                """, Integer.class, TenantContext.get(), period.getAcademicSessionId(), classId, studentId);
        if (count == null || count == 0) throw ApiException.badRequest("L'élève n'est pas inscrit dans cette classe pour la session");
    }

    private EnrollmentInfo enrollment(AcademicReportingPeriod period, UUID studentId) {
        return jdbc.query("""
                SELECT e.id,e.school_class_id,c.name,c.level
                  FROM student_enrollment e LEFT JOIN school_class c ON c.id=e.school_class_id
                 WHERE e.school_id=? AND e.academic_session_id=? AND e.student_id=? AND e.status='ACTIVE'
                 ORDER BY e.enrolled_on DESC LIMIT 1
                """, rs -> rs.next() ? new EnrollmentInfo(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4)) : null, TenantContext.get(), period.getAcademicSessionId(), studentId);
    }

    private AcademicReportingPeriod period(UUID id) {
        return periods.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période de résultat"));
    }

    private String modelFor(String level) {
        String normalized = level == null ? "" : level.toLowerCase(Locale.ROOT);
        return "secondary".equals(normalized) ? "PERIOD" : "DAILY";
    }

    private String policyVersion(AcademicReportingPeriod period, UUID studentId) {
        return jdbc.query("""
                SELECT COALESCE(p.policy_version,'attendance-policy-v1')
                  FROM student_enrollment e JOIN school_class c ON c.id=e.school_class_id
                  LEFT JOIN attendance_policy p ON p.school_id=e.school_id AND p.level=lower(c.level)
                 WHERE e.school_id=? AND e.academic_session_id=? AND e.student_id=? AND e.status='ACTIVE'
                 LIMIT 1
                """, rs -> rs.next() ? rs.getString(1) : "attendance-policy-v1",
                TenantContext.get(), period.getAcademicSessionId(), studentId);
    }

    private PolicyRecommendation recommendationPolicy() {
        return jdbc.query("""
                SELECT policy_version,absence_warning_percent,late_warning_minutes,honor_max_absence_percent,
                       honor_max_late_minutes,require_decision_code,work_blame_absence_percent,
                       discipline_warning_count,discipline_blame_count,award_min_coverage_percent
                  FROM conduct_recommendation_policy WHERE school_id=?
                """, rs -> rs.next() ? new PolicyRecommendation(rs.getString(1), rs.getBigDecimal(2),
                        rs.getInt(3), rs.getBigDecimal(4), rs.getInt(5), rs.getBoolean(6),
                        rs.getBigDecimal(7), rs.getInt(8), rs.getInt(9), rs.getBigDecimal(10))
                        : new PolicyRecommendation("conduct-policy-v1", BigDecimal.valueOf(20), 60,
                        BigDecimal.valueOf(5), 15, true, BigDecimal.valueOf(40), 1, 2, BigDecimal.valueOf(80)),
                TenantContext.get());
    }

    private String attendanceRepair(UUID periodId, UUID classId, LocalDate date, String periodKey) {
        return "/presence?classId=" + classId + "&date=" + date
                + (periodKey == null ? "" : "&periodKey=" + periodKey);
    }

    private AttendanceAdjustmentRowResult success(UUID studentId, UUID id, String status, long version) {
        return new AttendanceAdjustmentRowResult(studentId, "SAVED", id, status, version,
                Map.of(), "Enregistrement effectué.", "Adjustment saved.", true);
    }

    private AttendanceAdjustmentRowResult error(UUID studentId, UUID id, String status, long version,
                                                String field, String fr, String en, boolean retryable) {
        return new AttendanceAdjustmentRowResult(studentId, "INVALID", id, status, version,
                Map.of(field, fr), fr, en, retryable);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        BigDecimal result = value == null ? BigDecimal.ZERO : value;
        if (result.signum() < 0) throw ApiException.badRequest("Les heures d'absence ne peuvent pas être négatives");
        return result;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw ApiException.badRequest(message);
        return trim(value);
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw ApiException.badRequest("Le texte dépasse la longueur autorisée");
        return result;
    }

    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static BigDecimal nvl(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static BigDecimal value(Integer value) { return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value); }
    private static BigDecimal hours(Integer minutes) { return minutes == null ? null : BigDecimal.valueOf(minutes).divide(SIXTY, 6, RoundingMode.HALF_UP); }
    private static BigDecimal hoursToMinutes(BigDecimal hours) { return nvl(hours).multiply(SIXTY); }
    private static BigDecimal round(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.HALF_UP); }
    private Integer parsePeriod(String periodKey) {
        if (periodKey == null || !periodKey.toUpperCase(Locale.ROOT).startsWith("P")) return null;
        try { return Integer.parseInt(periodKey.substring(1)) - 1; } catch (NumberFormatException ignored) { return null; }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value == null ? List.of() : value); }
        catch (Exception ex) { throw new IllegalStateException("Unable to serialize attendance evidence", ex); }
    }

    private List<UUID> snapshotIds(AttendanceSummaryView attendance) {
        return attendance == null ? List.of() : attendance.sourceSnapshotIds();
    }

    private List<UUID> uuidList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode node = mapper.readTree(json);
            List<UUID> result = new ArrayList<>();
            if (node.isArray()) for (JsonNode item : node) if (item.isTextual()) {
                try { result.add(UUID.fromString(item.asText())); } catch (IllegalArgumentException ignored) { }
            }
            return result;
        } catch (Exception ignored) { return List.of(); }
    }

    private List<AttendanceSessionEvidenceView> sessionEvidenceList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            AttendanceSessionEvidenceView[] values = mapper.readValue(json, AttendanceSessionEvidenceView[].class);
            return values == null ? List.of() : List.of(values);
        } catch (Exception ignored) { return List.of(); }
    }

    private String termCode(String code) {
        if (code == null) return "";
        String value = code.toUpperCase(Locale.ROOT);
        return value.endsWith("_RESULT") ? value.substring(0, value.length() - 7) : value;
    }

    private String sha256(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private UUID actorId() {
        Object principal = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(x -> x.getPrincipal()).orElse(null);
        return principal instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private String actorUsername() {
        Object principal = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(x -> x.getPrincipal()).orElse(null);
        return principal instanceof AppUserPrincipal p ? p.username() : "system";
    }

    private Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof OffsetDateTime o) return o.toInstant();
        if (value instanceof Timestamp t) return t.toInstant();
        if (value instanceof java.time.LocalDateTime l) return l.toInstant(java.time.ZoneOffset.UTC);
        String text = value.toString().trim();
        try { return Instant.parse(text); }
        catch (java.time.format.DateTimeParseException ignored) {
            return java.time.LocalDateTime.parse(text.replace(' ', 'T'))
                    .toInstant(java.time.ZoneOffset.UTC);
        }
    }

    private record EnrollmentInfo(UUID id, UUID classId, String className, String level) {}
    private record SessionRow(UUID expectedId, UUID rollCallId, LocalDate date, String model, String periodKey,
                              String subjectCode, String status, boolean cancelled, long version, int durationMinutes) {}
    private record MarkRow(UUID rollCallId, String status, int lateMinutes, String source, String reason, String note) {}
    private record DurationValue(Integer minutes, String source) {}
    private record LatestAdjustment(UUID id, String status, long version) {}
    private record LatestConduct(UUID id, String status, long version, String decisionCode, String overrideReason) {}
    private record AdjustmentRow(UUID id, BigDecimal justified, BigDecimal unjustified, int late, String reason,
                                 String evidence, String status, long version, UUID actorId, String actorUsername,
                                 Instant createdAt, UUID correctsId) {}
    private record AdjustmentTotals(BigDecimal justifiedHours, BigDecimal unjustifiedHours, int lateMinutes,
                                    List<AttendanceAdjustmentEvidenceView> evidence) {}
    private record PolicyRecommendation(String policyVersion, BigDecimal absenceWarningPercent,
                                        int lateWarningMinutes, BigDecimal honorMaxAbsencePercent,
                                        int honorMaxLateMinutes, boolean requireDecisionCode,
                                        BigDecimal workBlameAbsencePercent, int disciplineWarningCount,
                                        int disciplineBlameCount, BigDecimal awardMinCoveragePercent) {}
    private record OfficialPart(UUID id, UUID reportingPeriodId, String code, long snapshotVersion,
                                String sourceFingerprint, int expectedSessions, BigDecimal expectedHours,
                                int finalizedSessions, BigDecimal finalizedHours, BigDecimal coverage,
                                int present, int absent, int excused, int late, BigDecimal totalAbsenceMinutes,
                                BigDecimal justifiedMinutes, BigDecimal unjustifiedMinutes, BigDecimal lateMinutes,
                                int exclusionDays, String sourceRollCallIds, String sourceSnapshotIds,
                                String missingSessions, String approvedAdjustments, String policyVersion) {}

    private static final class Accumulator {
        int expectedSessions;
        boolean durationMissing;
        BigDecimal expectedMinutes = BigDecimal.ZERO;
        int finalizedSessions;
        BigDecimal finalizedMinutes = BigDecimal.ZERO;
        int present;
        int absent;
        int excused;
        int late;
        int rawLateMinutes;
        int exclusionDays;
        BigDecimal baseJustifiedMinutes = BigDecimal.ZERO;
        BigDecimal baseUnjustifiedMinutes = BigDecimal.ZERO;
        BigDecimal totalOfficialAbsenceMinutes = BigDecimal.ZERO;
        BigDecimal justifiedOfficialMinutes = BigDecimal.ZERO;
        BigDecimal unjustifiedOfficialMinutes = BigDecimal.ZERO;
        final List<UUID> sourceRollCallIds = new ArrayList<>();
        final List<AttendanceSessionEvidenceView> missing = new ArrayList<>();
        final List<AttendanceReadinessIssueView> blockers = new ArrayList<>();
        final List<AttendanceReadinessIssueView> warnings = new ArrayList<>();
        final List<String> policyVersions = new ArrayList<>();
        void issue(AttendanceReadinessIssueView issue) { if ("BLOCKER".equals(issue.severity())) blockers.add(issue); else warnings.add(issue); }
        void warning(AttendanceReadinessIssueView issue) { warnings.add(issue); }
    }
}
