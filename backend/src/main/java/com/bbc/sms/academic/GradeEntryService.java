package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.academic.security.AcademicAccessPolicyService;
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
import com.bbc.sms.timetable.TeachingAssignmentResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GradeEntryService {
    private final AcademicReportingPeriodRepository periods;
    private final AcademicAssessmentRepository assessments;
    private final AcademicGradeRepository grades;
    private final SubjectResultCommentRepository comments;
    private final AcademicGradePacketRepository packets;
    private final StudentEnrollmentRepository enrollments;
    private final StudentRepository students;
    private final SubjectRepository subjects;
    private final SchoolClassRepository classes;
    private final AcademicWindowPolicyService windows;
    private final AcademicAccessPolicyService accessPolicy;
    private final TeachingAssignmentResolver assignments;
    private final JdbcTemplate jdbc;

    public GradeEntryService(AcademicReportingPeriodRepository periods,
                             AcademicAssessmentRepository assessments,
                             AcademicGradeRepository grades,
                             SubjectResultCommentRepository comments,
                             AcademicGradePacketRepository packets,
                             StudentEnrollmentRepository enrollments,
                             StudentRepository students,
                             SubjectRepository subjects,
                             SchoolClassRepository classes,
                             AcademicWindowPolicyService windows,
                             AcademicAccessPolicyService accessPolicy,
                             TeachingAssignmentResolver assignments,
                             JdbcTemplate jdbc) {
        this.periods = periods; this.assessments = assessments; this.grades = grades;
        this.comments = comments; this.packets = packets; this.enrollments = enrollments;
        this.students = students; this.subjects = subjects; this.classes = classes;
        this.windows = windows; this.accessPolicy = accessPolicy; this.assignments = assignments; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public GradeEntryView view(UUID periodId, UUID classId, String requestedSubject) {
        AcademicReportingPeriod period = period(periodId);
        AcademicPeriodRules.assertRawGradePeriod(period);
        SchoolClass schoolClass = schoolClass(classId);
        List<GradeEntrySubjectView> available = availableSubjects(period.getAcademicSessionId(), classId, schoolClass.getName(), period.getStartDate());
        if (available.isEmpty()) {
            accessPolicy.require(AcademicAccessPolicyService.Capability.SUBJECT_GRADE_VIEW,
                    period.getAcademicSessionId(), classId, requestedSubject, null, period.getStartDate());
            throw ApiException.conflict("Aucune matière n'est affectée à cette classe. Configurez Paramètres → Matières par classe avant la saisie.");
        }
        String subjectCode = requestedSubject == null || requestedSubject.isBlank()
                ? available.get(0).code() : requestedSubject.trim().toUpperCase(Locale.ROOT);
        GradeEntrySubjectView subject = available.stream().filter(x -> x.code().equalsIgnoreCase(subjectCode)).findFirst()
                .orElseThrow(() -> ApiException.forbidden("Cette matière n'est pas affectée à la classe ou ne vous est pas attribuée"));
        assertSubjectAccess(classId, schoolClass.getName(), period.getAcademicSessionId(), period.getStartDate(), subjectCode);

        List<GradeEntryAssessmentView> definition = assessments.findApplicable(
                TenantContext.get(), periodId, classId, subjectCode).stream().map(this::assessmentView).toList();
        List<RosterStudent> roster = roster(period.getAcademicSessionId(), classId);
        List<UUID> studentIds = roster.stream().map(RosterStudent::id).toList();
        Map<UUID, List<AcademicGrade>> gradeByStudent = studentIds.isEmpty() ? Map.of() : grades
                .findBySchoolIdAndReportingPeriodIdAndStudentIdInAndSubjectCodeOrderByStudentIdAscAssessmentIdAsc(
                        TenantContext.get(), periodId, studentIds, subjectCode)
                .stream().collect(Collectors.groupingBy(AcademicGrade::getStudentId));
        Map<UUID, SubjectResultComment> commentByStudent = studentIds.isEmpty() ? Map.of() : comments
                .findBySchoolIdAndReportingPeriodIdAndStudentIdInAndSubjectCode(TenantContext.get(), periodId, studentIds, subjectCode)
                .stream().collect(Collectors.toMap(SubjectResultComment::getStudentId, Function.identity()));
        AcademicGradePacket packet = packets.findBySchoolIdAndReportingPeriodIdAndClassIdAndSubjectCode(
                TenantContext.get(), periodId, classId, subjectCode).orElse(null);
        List<GradeEntryStudentView> rows = roster.stream().map(r -> {
            List<GradeEntryCellView> values = new ArrayList<>();
            Map<UUID, AcademicGrade> byAssessment = gradeByStudent.getOrDefault(r.id(), List.of()).stream()
                    .collect(Collectors.toMap(AcademicGrade::getAssessmentId, Function.identity(), (a, b) -> b));
            for (GradeEntryAssessmentView a : definition) {
                AcademicGrade g = byAssessment.get(a.id());
                values.add(new GradeEntryCellView(a.id(), g == null ? null : g.getMark(),
                        g == null ? "MISSING" : g.getValueStatus(), g == null ? 0 : g.getVersion()));
            }
            SubjectResultComment c = commentByStudent.get(r.id());
            String workflow = byAssessment.values().stream().map(AcademicGrade::getWorkflowStatus).filter(Objects::nonNull).findFirst()
                    .orElse(packet == null ? "DRAFT" : packet.getStatus());
            return new GradeEntryStudentView(r.id(), r.matricule(), r.name(), values,
                    c == null ? null : c.getComment(), workflow);
        }).toList();
        List<String> blockers = blockers(definition, rows, subject.remarkRequired());
        List<GradeEntryBlockerView> completionBlockers = completionBlockers(blockers, subjectCode);
        List<GradeEntryBlockerView> submissionBlockers = new ArrayList<>(completionBlockers);
        TeacherAssignmentReadinessView readiness = subject.assignmentReadiness();
        if (!subjectReady(subject)) submissionBlockers.add(new GradeEntryBlockerView(
                subject.errorCode() == null ? "ASSIGNMENT_MISSING" : subject.errorCode(), subject.code(), null,
                subject.message() == null ? "A responsible teacher assignment is required before submission." : subject.message(),
                subject.message() == null ? "A responsible teacher assignment is required before submission." : subject.message(),
                "class-subjects", "BLOCKER"));
        boolean restricted = restrictedTeacher();
        boolean editableStatus = packet == null || Set.of("DRAFT", "RETURNED").contains(packet.getStatus());
        boolean editScope = accessPolicy.can(AcademicAccessPolicyService.Capability.SUBJECT_GRADE_EDIT,
                period.getAcademicSessionId(), classId, subjectCode, null, period.getStartDate())
                && (!restricted || subjectReady(subject) || delegatedAccess(period, classId, subjectCode,
                AcademicAccessPolicyService.Capability.SUBJECT_GRADE_EDIT));
        boolean canEdit = editableStatus && editScope;
        boolean submissionWindowOpen = false;
        AcademicWindowPolicyService.WindowView submissionWindow = null;
        boolean submitScope = subjectReady(subject) || delegatedAccess(period, classId, subjectCode,
                AcademicAccessPolicyService.Capability.SUBJECT_GRADE_SUBMIT);
        if (submitScope) {
            submissionWindow = windows.effective(period.getId(), AcademicWindowPolicyService.Action.TEACHER_SUBMISSION);
            submissionWindowOpen = submissionWindow.open();
            if (!submissionWindowOpen) {
                String code = Set.of("SCHEDULED", "CLOSED").contains(submissionWindow.state())
                        ? "WINDOW_CLOSED" : "WINDOW_NOT_CONFIGURED";
                submissionBlockers.add(new GradeEntryBlockerView(code, subject.code(), null,
                        "La fenêtre de soumission des enseignants n'est pas ouverte.",
                        "The teacher-submission window is not open.", "academic-sessions", "BLOCKER"));
            }
        }
        boolean canSubmit = canEdit && accessPolicy.can(AcademicAccessPolicyService.Capability.SUBJECT_GRADE_SUBMIT,
                period.getAcademicSessionId(), classId, subjectCode, null, period.getStartDate())
                && submissionWindowOpen && submissionBlockers.isEmpty();
        boolean canReview = accessPolicy.can(AcademicAccessPolicyService.Capability.GRADE_PACKET_REVIEW,
                period.getAcademicSessionId(), classId, subjectCode, null, period.getStartDate());
        int completed = (int) rows.stream().filter(r -> rowComplete(definition, r, subject.remarkRequired())).count();
        return new GradeEntryView(period.getAcademicSessionId(), periodId, classId, schoolClass.getName(),
                subject.code(), subject.label(), subject.coefficient(), subject.teacherId(), subject.teacherName(),
                packet == null ? "DRAFT" : packet.getStatus(), packet == null ? 0 : packet.getVersion(),
                definition, rows, rows.size(), completed, blockers, available, completionBlockers,
                submissionBlockers, List.of(), readiness,
                new GradeEntryCapabilitiesView(canEdit, canSubmit, canReview, restricted,
                        restricted && !subjectReady(subject) && !delegatedAccess(period, classId, subjectCode,
                                AcademicAccessPolicyService.Capability.SUBJECT_GRADE_EDIT)
                                ? "Repair the responsible teacher assignment or create a dated delegation before editing or submitting." : null));
    }

    @Transactional
    public GradeEntryView save(GradeEntrySaveRequest in) {
        AcademicReportingPeriod period = period(in.reportingPeriodId());
        AcademicPeriodRules.assertRawGradePeriod(period);
        SchoolClass schoolClass = schoolClass(in.classId());
        String subjectCode = in.subjectCode().trim().toUpperCase(Locale.ROOT);
        GradeEntrySubjectView subject = availableSubjects(period.getAcademicSessionId(), in.classId(), schoolClass.getName(), period.getStartDate()).stream()
                .filter(x -> x.code().equalsIgnoreCase(subjectCode)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("La matière n'est pas affectée à cette classe"));
        assertSubjectAccess(in.classId(), schoolClass.getName(), period.getAcademicSessionId(), period.getStartDate(), subjectCode);
        accessPolicy.require(AcademicAccessPolicyService.Capability.SUBJECT_GRADE_EDIT,
                period.getAcademicSessionId(), in.classId(), subjectCode, null, period.getStartDate());
        invalidateValidatedBulletins(period.getId(), period.getAcademicSessionId(), in.classId());
        AcademicGradePacket packet = packet(period, in.classId(), subjectCode, subject);
        String previousPacketStatus = packet.getStatus();
        adoptAssignment(packet, subject);
        if ("ACCEPTED".equals(packet.getStatus()) || "LOCKED".equals(packet.getStatus())) {
            windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.CORRECTION);
            packet.setStatus("DRAFT");
        } else {
            windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.GRADE_ENTRY);
        }
        if (in.packetVersion() != null && packet.getId() != null && in.packetVersion() != packet.getVersion()) {
            throw ApiException.conflict("La feuille de saisie a été modifiée par un autre utilisateur. Rechargez-la avant d'enregistrer.");
        }
        Map<UUID, AcademicAssessment> definition = assessments.findApplicable(
                TenantContext.get(), period.getId(), in.classId(), subjectCode).stream()
                .collect(Collectors.toMap(AcademicAssessment::getId, Function.identity()));
        Set<UUID> rosterIds = roster(period.getAcademicSessionId(), in.classId()).stream().map(RosterStudent::id).collect(Collectors.toSet());
        for (GradeEntryStudentUpsert row : in.students()) {
            if (!rosterIds.contains(row.studentId())) throw ApiException.badRequest("L'élève ne fait pas partie de la classe pour cette session");
            StudentEnrollment enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                    TenantContext.get(), row.studentId(), period.getAcademicSessionId(), "ACTIVE")
                    .filter(e -> in.classId().equals(e.getSchoolClassId()))
                    .orElseThrow(() -> ApiException.badRequest("Inscription active introuvable pour l'élève"));
            for (GradeEntryCellUpsert cell : row.values() == null ? List.<GradeEntryCellUpsert>of() : row.values()) {
                AcademicAssessment assessment = definition.get(cell.assessmentId());
                if (assessment == null) throw ApiException.badRequest("L'évaluation n'appartient pas à cette période");
                String status = normalizeStatus(cell.valueStatus());
                validateMark(cell.mark(), status, assessment);
                AcademicGrade grade = grades.findBySchoolIdAndStudentIdAndAssessmentIdAndSubjectCode(
                        TenantContext.get(), row.studentId(), assessment.getId(), subjectCode).orElseGet(AcademicGrade::new);
                if (cell.version() != null && grade.getId() != null && cell.version() != grade.getVersion()) {
                    throw ApiException.conflict("Une note de " + studentName(row.studentId()) + " a été modifiée par un autre utilisateur");
                }
                grade.setSchoolId(TenantContext.get()); grade.setAcademicSessionId(period.getAcademicSessionId()); grade.setReportingPeriodId(period.getId());
                grade.setAssessmentId(assessment.getId()); grade.setStudentId(row.studentId()); grade.setEnrollmentId(enrollment.getId());
                grade.setSubjectCode(subjectCode); grade.setMark("SCORED".equals(status) ? cell.mark() : null);
                grade.setValueStatus(status); grade.setWorkflowStatus("DRAFT"); grade.setEnteredBy(currentUserId()); grade.setTeacherId(subject.teacherId());
                grades.save(grade);
            }
            SubjectResultComment comment = comments.findBySchoolIdAndStudentIdAndReportingPeriodIdAndSubjectCode(
                    TenantContext.get(), row.studentId(), period.getId(), subjectCode).orElseGet(SubjectResultComment::new);
            if (row.comment() != null && row.comment().length() > 500) throw ApiException.badRequest("La remarque ne peut pas dépasser 500 caractères");
            comment.setSchoolId(TenantContext.get()); comment.setAcademicSessionId(period.getAcademicSessionId()); comment.setReportingPeriodId(period.getId());
            comment.setStudentId(row.studentId()); comment.setEnrollmentId(enrollment.getId()); comment.setSubjectCode(subjectCode);
            comment.setComment(row.comment() == null ? null : row.comment().trim()); comment.setWorkflowStatus("DRAFT"); comment.setTeacherId(subject.teacherId());
            comments.save(comment);
        }
        packet.setStatus("DRAFT"); packet.setLastSavedBy(currentUserId()); packet.setLastSavedAt(Instant.now()); packets.saveAndFlush(packet);
        recordPacketTransition(packet, previousPacketStatus, "DRAFT", "Correction draft saved");
        return view(period.getId(), in.classId(), subjectCode);
    }

    /** Grade edits after validation require an explicit correction draft. */
    private void invalidateValidatedBulletins(UUID periodId, UUID sessionId, UUID classId) {
        Integer blocked = jdbc.queryForObject("""
            SELECT count(*) FROM bulletin_version v
             WHERE v.school_id=? AND v.reporting_period_id=? AND v.state IN ('VALIDATED','PUBLISHED')
               AND v.student_id IN (SELECT e.student_id FROM student_enrollment e
                                      WHERE e.school_id=? AND e.academic_session_id=? AND e.school_class_id=? AND e.status='ACTIVE')
               AND NOT EXISTS (SELECT 1 FROM bulletin_version correction
                                 WHERE correction.school_id=v.school_id
                                   AND correction.corrects_bulletin_version_id=v.id
                                   AND correction.state='DRAFT')
            """, Integer.class, TenantContext.get(), periodId, TenantContext.get(), sessionId, classId);
        if (blocked != null && blocked > 0) {
            windows.assertOpen(periodId, AcademicWindowPolicyService.Action.CORRECTION);
            throw ApiException.conflict("Un bulletin validé ou publié existe encore. Ouvrez d'abord une correction explicite depuis le bulletin concerné.");
        }
        int published = jdbc.queryForObject("""
            SELECT count(*) FROM bulletin_version v
             WHERE v.school_id=? AND v.reporting_period_id=? AND v.state='PUBLISHED'
               AND v.student_id IN (SELECT e.student_id FROM student_enrollment e
                                      WHERE e.school_id=? AND e.academic_session_id=? AND e.school_class_id=? AND e.status='ACTIVE')
            """, Integer.class, TenantContext.get(), periodId, TenantContext.get(), sessionId, classId);
        if (published > 0) windows.assertOpen(periodId, AcademicWindowPolicyService.Action.CORRECTION);
    }

    @Transactional
    public GradeEntryView submit(GradeEntryReviewRequest in) {
        AcademicReportingPeriod period = period(in.reportingPeriodId());
        AcademicPeriodRules.assertRawGradePeriod(period);
        SchoolClass schoolClass = schoolClass(in.classId());
        String subjectCode = in.subjectCode().trim().toUpperCase(Locale.ROOT);
        GradeEntrySubjectView subject = availableSubjects(period.getAcademicSessionId(), in.classId(), schoolClass.getName(), period.getStartDate()).stream().filter(x -> x.code().equalsIgnoreCase(subjectCode)).findFirst().orElseThrow(() -> ApiException.badRequest("La matière n'est pas affectée à cette classe"));
        assertSubjectAccess(in.classId(), schoolClass.getName(), period.getAcademicSessionId(), period.getStartDate(), subjectCode);
        AcademicGradePacket packet = packet(period, in.classId(), subjectCode, subject);
        String previousPacketStatus = packet.getStatus();
        if (in.packetVersion() != null && packet.getId() != null && in.packetVersion() != packet.getVersion()) throw ApiException.conflict("La feuille de saisie a été modifiée entre-temps. Rechargez-la.");
        if ("SUBMIT".equalsIgnoreCase(in.action())) {
            accessPolicy.require(AcademicAccessPolicyService.Capability.SUBJECT_GRADE_SUBMIT,
                    period.getAcademicSessionId(), in.classId(), subjectCode, null, period.getStartDate());
            if (!Set.of("DRAFT", "RETURNED").contains(previousPacketStatus)) {
                throw ApiException.conflict("Cette feuille doit être en brouillon ou retournée avant une nouvelle soumission.");
            }
            windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.TEACHER_SUBMISSION);
            GradeEntryView current = view(period.getId(), in.classId(), subjectCode);
            if (!current.submissionBlockers().isEmpty()) throw ApiException.conflict(
                    "La saisie ne peut pas être soumise tant que les éléments requis ne sont pas corrigés.");
            adoptAssignment(packet, subject);
            packet.setStatus("SUBMITTED"); packet.setSubmittedBy(currentUserId()); packet.setSubmittedAt(Instant.now());
            updateWorkflow(period.getId(), in.classId(), subjectCode, "SUBMITTED");
        } else {
            accessPolicy.require(AcademicAccessPolicyService.Capability.GRADE_PACKET_REVIEW,
                    period.getAcademicSessionId(), in.classId(), subjectCode, null, period.getStartDate());
            windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.REVIEW);
            String action = in.action().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("ACCEPT", "RETURN").contains(action)) throw ApiException.badRequest("Action de revue invalide : utilisez ACCEPT ou RETURN");
            if (!"SUBMITTED".equals(previousPacketStatus)) {
                throw ApiException.conflict("Seule une feuille soumise peut être acceptée ou retournée.");
            }
            if ("RETURN".equals(action) && (in.reason() == null || in.reason().isBlank())) {
                throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "RETURN_REASON_REQUIRED",
                        "Le motif du retour est obligatoire.", "reason", "Explain what the teacher must correct.");
            }
            packet.setStatus("ACCEPT".equals(action) ? "ACCEPTED" : "RETURNED"); packet.setReviewReason(in.reason());
            packet.setReviewedBy(currentUserId()); packet.setReviewedAt(Instant.now());
            updateWorkflow(period.getId(), in.classId(), subjectCode, packet.getStatus());
        }
        packet.setLastSavedBy(currentUserId()); packet.setLastSavedAt(Instant.now());
        packets.saveAndFlush(packet);
        recordPacketTransition(packet, previousPacketStatus, packet.getStatus(), in.reason());
        return view(period.getId(), in.classId(), subjectCode);
    }

    private void updateWorkflow(UUID periodId, UUID classId, String subjectCode, String state) {
        List<UUID> ids = roster(period(periodId).getAcademicSessionId(), classId).stream().map(RosterStudent::id).toList();
        if (!ids.isEmpty()) jdbc.update("UPDATE academic_grade SET workflow_status=? WHERE school_id=? AND reporting_period_id=? AND subject_code=? AND student_id = ANY(?)", state, TenantContext.get(), periodId, subjectCode, jdbc.getDataSource() == null ? new UUID[0] : ids.toArray(UUID[]::new));
        // The PostgreSQL ANY update above is intentionally paired with a no-op-safe
        // comment update below; comments are not required for calculation.
        if (!ids.isEmpty()) jdbc.update("UPDATE subject_result_comment SET workflow_status=? WHERE school_id=? AND reporting_period_id=? AND subject_code=? AND student_id = ANY(?)", state, TenantContext.get(), periodId, subjectCode, ids.toArray(UUID[]::new));
    }

    private List<GradeEntrySubjectView> availableSubjects(UUID sessionId, UUID classId, String className,
                                                           java.time.LocalDate effectiveDate) {
        List<GradeEntrySubjectView> all = jdbc.query("""
                SELECT s.code, COALESCE(s.label->>'fr', s.label->>'en', s.code), cur.coefficient, cur.remark_required
                  FROM academic_curriculum_subject cur
                  JOIN subject s ON s.id=cur.subject_id
                 WHERE cur.school_id=? AND cur.academic_session_id=? AND cur.class_id=?
                   AND (cur.active_from IS NULL OR cur.active_from<=?)
                   AND (cur.active_to IS NULL OR cur.active_to>=?)
                 ORDER BY cur.display_order, s.code
                """, (rs, n) -> {
                    String code = rs.getString(1);
                    TeachingAssignmentResolver.Resolution resolved = assignments.resolve(sessionId, classId, code, effectiveDate);
                    return new GradeEntrySubjectView(code, rs.getString(2), rs.getInt(3), resolved.teacherId(),
                            resolved.teacherName(), resolved.status(), resolved.code(),
                            resolved.messageFr(), rs.getBoolean(4), assignmentReadiness(resolved));
                }, TenantContext.get(), sessionId, classId, effectiveDate, effectiveDate);
        return all.stream().filter(subject -> accessPolicy.can(
                AcademicAccessPolicyService.Capability.SUBJECT_GRADE_VIEW,
                sessionId, classId, subject.code(), null, effectiveDate)).toList();
    }

    private void assertSubjectAccess(UUID classId, String className, UUID sessionId,
                                     java.time.LocalDate effectiveDate, String subjectCode) {
        accessPolicy.require(AcademicAccessPolicyService.Capability.SUBJECT_GRADE_VIEW,
                sessionId, classId, subjectCode, null, effectiveDate);
        /*
        if (!restrictedTeacher()) return;
        UUID employeeId = currentEmployeeId();
        if (employeeId == null) throw ApiException.forbidden("Votre compte enseignant n'est pas relié à un employé actif.");
        TeachingAssignmentResolver.Resolution resolved = assignments.resolve(sessionId, classId, subjectCode, effectiveDate);
        if (!resolved.available()) {
            throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT, resolved.code(),
                    resolved.messageFr() + " / " + resolved.messageEn());
        }
        if (!employeeId.equals(resolved.teacherId())) {
            throw ApiException.forbidden("Vous n’êtes pas affecté à cette matière dans cette classe.");
        }
        }

        */
    }
    private boolean subjectReady(GradeEntrySubjectView subject) {
        return "RESOLVED".equals(subject.status()) && subject.teacherId() != null;
    }

    private boolean restrictedTeacher() {
        return Set.of("teacher", "form_teacher").contains(currentRole().toLowerCase(Locale.ROOT));
    }

    private TeacherAssignmentReadinessView assignmentReadiness(TeachingAssignmentResolver.Resolution resolved) {
        return new TeacherAssignmentReadinessView(resolved.status(), resolved.code(), resolved.teacherId(),
                resolved.teacherName(), resolved.teacherCode(), resolved.assignmentId(), resolved.assignmentVersion(),
                resolved.source(), resolved.source(), null, null, resolved.messageFr(), resolved.messageEn(),
                !resolved.available());
    }

    private List<GradeEntryBlockerView> completionBlockers(List<String> blockers, String subjectCode) {
        return blockers.stream().map(message -> new GradeEntryBlockerView(
                "GRADE_ENTRY_INCOMPLETE", subjectCode, null, message, message, "grade-entry", "BLOCKER")).toList();
    }

    private List<RosterStudent> roster(UUID sessionId, UUID classId) {
        return jdbc.query("""
                SELECT s.id, s.matricule, trim(s.last_name || ' ' || s.first_name)
                  FROM student_enrollment se JOIN student s ON s.id=se.student_id
                  JOIN academic_session session ON session.id=se.academic_session_id AND session.school_id=se.school_id
                 WHERE se.school_id=? AND se.academic_session_id=? AND se.school_class_id=?
                   AND se.status='ACTIVE' AND s.active=true
                   AND se.enrolled_on<=session.end_date AND (se.exited_on IS NULL OR se.exited_on>=session.start_date)
                 ORDER BY s.last_name, s.first_name, s.matricule
                """, (rs, n) -> new RosterStudent(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                TenantContext.get(), sessionId, classId);
    }

    private AcademicGradePacket packet(AcademicReportingPeriod period, UUID classId, String subjectCode,
                                       GradeEntrySubjectView subject) {
        return packets.findBySchoolIdAndReportingPeriodIdAndClassIdAndSubjectCode(TenantContext.get(), period.getId(), classId, subjectCode).orElseGet(() -> {
            AcademicGradePacket p = new AcademicGradePacket(); p.setSchoolId(TenantContext.get()); p.setAcademicSessionId(period.getAcademicSessionId());
            p.setReportingPeriodId(period.getId()); p.setClassId(classId); p.setSubjectCode(subjectCode); return p;
        });
    }

    /** Assignment identity is adopted only by a mutable draft. Historical
     * accepted/locked packets retain their provenance and cannot be silently
     * reassigned when setup changes. */
    private void adoptAssignment(AcademicGradePacket packet, GradeEntrySubjectView subject) {
        if (packet.getId() != null && Set.of("ACCEPTED", "LOCKED").contains(packet.getStatus())) {
            if (subjectReady(subject)
                    && (!Objects.equals(packet.getTeacherId(), subject.teacherId())
                    || !Objects.equals(packet.getResponsibleAssignmentId(), subject.assignmentReadiness().assignmentId())
                    || !Objects.equals(packet.getResponsibleAssignmentVersion(), subject.assignmentReadiness().assignmentVersion()))) {
                throw ApiException.conflict("Cette feuille historique conserve l'affectation utilisée lors de sa validation. Ouvrez une correction explicite avant toute nouvelle affectation.");
            }
            return;
        }
        UUID owner = subject.teacherId() == null ? accessPolicy.currentEmployeeId() : subject.teacherId();
        packet.setTeacherId(owner);
        packet.setResponsibleAssignmentId(subject.assignmentReadiness() == null ? null : subject.assignmentReadiness().assignmentId());
        packet.setResponsibleAssignmentVersion(subject.assignmentReadiness() == null ? null : subject.assignmentReadiness().assignmentVersion());
        if (packet.getId() != null && owner != null) syncMutableDraftOwnership(packet, owner);
    }

    private boolean delegatedAccess(AcademicReportingPeriod period, UUID classId, String subjectCode,
                                    AcademicAccessPolicyService.Capability capability) {
        AcademicAccessPolicyService.AccessDecision decision = accessPolicy.resolve(capability,
                period.getAcademicSessionId(), classId, subjectCode, null, period.getStartDate());
        return decision.allowed() && decision.delegated();
    }

    private void syncMutableDraftOwnership(AcademicGradePacket packet, UUID teacherId) {
        jdbc.update("""
                UPDATE academic_grade g SET teacher_id=?
                 WHERE g.school_id=? AND g.academic_session_id=? AND g.reporting_period_id=?
                   AND g.subject_code=? AND g.workflow_status IN ('DRAFT','RETURNED')
                   AND g.student_id IN (SELECT e.student_id FROM student_enrollment e
                                         WHERE e.school_id=? AND e.academic_session_id=?
                                           AND e.school_class_id=? AND e.status='ACTIVE')
                """, teacherId, TenantContext.get(), packet.getAcademicSessionId(), packet.getReportingPeriodId(),
                packet.getSubjectCode(), TenantContext.get(), packet.getAcademicSessionId(), packet.getClassId());
        jdbc.update("""
                UPDATE subject_result_comment c SET teacher_id=?
                 WHERE c.school_id=? AND c.academic_session_id=? AND c.reporting_period_id=?
                   AND c.subject_code=? AND c.workflow_status IN ('DRAFT','RETURNED')
                   AND c.student_id IN (SELECT e.student_id FROM student_enrollment e
                                         WHERE e.school_id=? AND e.academic_session_id=?
                                           AND e.school_class_id=? AND e.status='ACTIVE')
                """, teacherId, TenantContext.get(), packet.getAcademicSessionId(), packet.getReportingPeriodId(),
                packet.getSubjectCode(), TenantContext.get(), packet.getAcademicSessionId(), packet.getClassId());
    }

    private void recordPacketTransition(AcademicGradePacket packet, String from, String to, String reason) {
        if (packet.getId() == null || Objects.equals(from, to)) return;
        jdbc.update("""
            INSERT INTO academic_grade_packet_transition
                (school_id,packet_id,from_status,to_status,reason,actor_user_id)
            VALUES (?,?,?,?,?,?)
            """, TenantContext.get(), packet.getId(), from, to,
                reason == null || reason.isBlank() ? null : reason.trim(), currentUserId());
    }

    private List<String> blockers(List<GradeEntryAssessmentView> definition, List<GradeEntryStudentView> rows,
                                  boolean remarkRequired) {
        if (definition.isEmpty()) return List.of("Aucune évaluation n'est configurée pour cette période");
        List<String> result = new ArrayList<>();
        for (GradeEntryStudentView row : rows) for (int i = 0; i < definition.size(); i++) {
            GradeEntryAssessmentView a = definition.get(i); GradeEntryCellView c = row.values().get(i);
            if (a.mandatory() && !"SCORED".equals(c.valueStatus()) && !"ABSENT".equals(c.valueStatus()) && !"EXEMPT".equals(c.valueStatus())) {
                result.add(row.studentName() + " · " + a.label());
            }
        }
        if (remarkRequired) for (GradeEntryStudentView row : rows) {
            if (row.comment() == null || row.comment().isBlank()) result.add(row.studentName() + " · remarque obligatoire");
        }
        return result.size() > 12 ? new ArrayList<>(result.subList(0, 12)) {{ add("… et " + (result.size() - 12) + " autre(s)"); }} : result;
    }

    private boolean rowComplete(List<GradeEntryAssessmentView> definition, GradeEntryStudentView row, boolean remarkRequired) {
        for (int i = 0; i < definition.size(); i++) if (definition.get(i).mandatory() && Set.of("MISSING", "").contains(row.values().get(i).valueStatus())) return false;
        if (remarkRequired && (row.comment() == null || row.comment().isBlank())) return false;
        return true;
    }
    private String normalizeStatus(String raw) { String s = raw == null || raw.isBlank() ? "SCORED" : raw.trim().toUpperCase(Locale.ROOT); if (!Set.of("SCORED","MISSING","ABSENT","EXEMPT").contains(s)) throw ApiException.badRequest("Statut de note invalide"); return s; }
    private void validateMark(BigDecimal mark, String status, AcademicAssessment a) { if ("SCORED".equals(status) && mark == null) throw ApiException.badRequest("Une note est obligatoire pour une valeur saisie"); if ("SCORED".equals(status) && (mark.compareTo(BigDecimal.ZERO) < 0 || mark.compareTo(a.getMaxScore()) > 0)) throw ApiException.badRequest("La note doit être comprise entre 0 et " + a.getMaxScore()); }
    private AcademicReportingPeriod period(UUID id) { return periods.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Période de résultat")); }
    private SchoolClass schoolClass(UUID id) { return classes.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Classe")); }
    private GradeEntryAssessmentView assessmentView(AcademicAssessment a) { return new GradeEntryAssessmentView(a.getId(), a.getCode(), a.getLabel(), a.getMaxScore(), a.getWeight(), a.isMandatory(), a.getDisplayOrder()); }
    private String studentName(UUID id) { return students.findByIdAndSchoolId(id, TenantContext.get()).map(s -> s.getLastName() + " " + s.getFirstName()).orElse("élève"); }
    private UUID currentUserId() { var auth = SecurityContextHolder.getContext().getAuthentication(); return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null; }
    private UUID currentEmployeeId() { UUID user = currentUserId(); return user == null ? null : jdbc.query("SELECT employee_id FROM app_user WHERE id=?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, user); }
    private String currentRole() { var auth = SecurityContextHolder.getContext().getAuthentication(); return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.roleCode() : ""; }
    private void requireReviewer() { if (!Set.of("admin","principal","dean_of_studies","censor").contains(currentRole())) throw ApiException.forbidden("Seule la direction peut accepter ou retourner une feuille de notes"); }
    private record RosterStudent(UUID id, String matricule, String name) {}
}
