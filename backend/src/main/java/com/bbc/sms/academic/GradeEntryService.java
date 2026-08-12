package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.foundation.session.AcademicReportingPeriodRepository;
import com.bbc.sms.foundation.session.AcademicWindowPolicyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import com.bbc.sms.timetable.TeachingAssignmentResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
    private final TeacherScopeService teacherScope;
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
                             TeacherScopeService teacherScope,
                             TeachingAssignmentResolver assignments,
                             JdbcTemplate jdbc) {
        this.periods = periods; this.assessments = assessments; this.grades = grades;
        this.comments = comments; this.packets = packets; this.enrollments = enrollments;
        this.students = students; this.subjects = subjects; this.classes = classes;
        this.windows = windows; this.teacherScope = teacherScope; this.assignments = assignments; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public GradePacketQueueView queue() {
        List<GradePacketQueueItem> all = jdbc.query("""
            SELECT p.id,p.reporting_period_id,rp.code,rp.label,p.class_id,c.name,p.subject_code,
                   COALESCE(s.label->>'fr',s.label->>'en',p.subject_code),p.status,p.revision_number,
                   p.teacher_id,e.name,p.version,p.review_reason,
                   (SELECT count(*) FROM student_enrollment se WHERE se.school_id=p.school_id
                     AND se.academic_session_id=p.academic_session_id AND se.school_class_id=p.class_id AND se.status='ACTIVE'),
                   (SELECT count(*) FROM student_enrollment se WHERE se.school_id=p.school_id
                     AND se.academic_session_id=p.academic_session_id AND se.school_class_id=p.class_id AND se.status='ACTIVE'
                     AND NOT EXISTS (SELECT 1 FROM academic_assessment a
                       WHERE a.school_id=p.school_id AND a.reporting_period_id=p.reporting_period_id
                         AND (a.class_id IS NULL OR a.class_id=p.class_id)
                         AND upper(a.subject_code)=upper(p.subject_code) AND a.mandatory=true
                         AND NOT EXISTS (SELECT 1 FROM academic_grade g WHERE g.packet_id=p.id
                           AND g.student_id=se.student_id AND g.assessment_id=a.id
                           AND g.value_status IN ('SCORED','ABSENT','EXEMPT')))
                     AND NOT EXISTS (SELECT 1 FROM academic_curriculum_subject cs JOIN subject ss ON ss.id=cs.subject_id
                       WHERE cs.school_id=p.school_id AND cs.academic_session_id=p.academic_session_id
                         AND cs.class_id=p.class_id AND upper(ss.code)=upper(p.subject_code)
                         AND cs.remark_required=true
                         AND NOT EXISTS (SELECT 1 FROM subject_result_comment sc WHERE sc.packet_id=p.id
                           AND sc.student_id=se.student_id AND nullif(trim(sc.comment),'') IS NOT NULL)))
              FROM academic_grade_packet p
              JOIN academic_reporting_period rp ON rp.id=p.reporting_period_id
              JOIN school_class c ON c.id=p.class_id
              LEFT JOIN subject s ON upper(s.code)=upper(p.subject_code) AND s.school_id=p.school_id
              LEFT JOIN employee e ON e.id=p.teacher_id
             WHERE p.school_id=?
               AND p.revision_number=(SELECT max(px.revision_number) FROM academic_grade_packet px
                                      WHERE px.school_id=p.school_id AND px.reporting_period_id=p.reporting_period_id
                                        AND px.class_id=p.class_id AND upper(px.subject_code)=upper(p.subject_code))
             ORDER BY rp.start_date DESC,c.name,p.subject_code
            """, (rs, n) -> queueItem(rs, n), TenantContext.get()).stream().map(this::withWindow).toList();
        UUID employee = currentEmployeeId();
        List<GradePacketQueueItem> teacher = all.stream()
                .filter(i -> !restrictedTeacher() || (employee != null && employee.equals(i.teacherId())))
                .map(this::withActionsForTeacher).toList();
        List<GradePacketQueueItem> reviewer = isReviewer() ? all.stream()
                .filter(i -> Set.of("SUBMITTED", "IN_REVIEW", "RETURNED").contains(i.packetStatus())
                        || "READY".equals(i.completionState()))
                .map(this::withActionsForReviewer).toList() : List.of();
        return new GradePacketQueueView(teacher, reviewer);
    }

    @Transactional(readOnly = true)
    public GradePacketHistoryView history(UUID packetId) {
        AcademicGradePacket packet = packets.findById(packetId)
                .filter(p -> TenantContext.get().equals(p.getSchoolId()))
                .orElseThrow(() -> ApiException.notFound("Feuille de notes"));
        List<GradePacketTransitionView> transitions = jdbc.query("""
            SELECT id,event_type,from_status,to_status,reason,actor_user_id,created_at,affected_rows::text
              FROM academic_grade_packet_transition WHERE school_id=? AND packet_id=? ORDER BY created_at,id
            """, (rs, n) -> new GradePacketTransitionView(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getObject(6, UUID.class),
                rs.getTimestamp(7).toInstant(), parseUuidArray(rs.getString(8))), TenantContext.get(), packetId);
        List<SubjectCommentHistoryView> commentsHistory = jdbc.query("""
            SELECT h.id,h.comment_id,h.comment,h.appreciation_code,h.workflow_status,h.author_user_id,
                   h.source_version,h.changed_by,h.changed_at
              FROM subject_result_comment_history h JOIN subject_result_comment c ON c.id=h.comment_id
             WHERE h.school_id=? AND c.packet_id=? ORDER BY h.changed_at,h.id
            """, (rs, n) -> new SubjectCommentHistoryView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getObject(6, UUID.class), rs.getLong(7),
                rs.getObject(8, UUID.class), rs.getTimestamp(9).toInstant()), TenantContext.get(), packetId);
        return new GradePacketHistoryView(packet.getId(), packet.getRevisionNumber(), packet.getSupersedesPacketId(),
                packet.getTeacherId(), packet.getResponsibleAssignmentId(), packet.getResponsibleAssignmentVersion(),
                transitions, commentsHistory);
    }

    private GradePacketQueueItem queueItem(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        int total = rs.getInt(15), completed = rs.getInt(16);
        String status = rs.getString(9);
        return new GradePacketQueueItem(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7), rs.getString(8),
                status, rs.getInt(10), total, completed, completed >= total ? "READY" : "INCOMPLETE",
                null, null, null, rs.getString(14), rs.getObject(11, UUID.class), rs.getString(12), rs.getLong(13), List.of());
    }

    private GradePacketQueueItem withWindow(GradePacketQueueItem item) {
        var window = windows.effective(item.reportingPeriodId(), AcademicWindowPolicyService.Action.TEACHER_SUBMISSION);
        return new GradePacketQueueItem(item.packetId(), item.reportingPeriodId(), item.reportingPeriodCode(), item.reportingPeriodLabel(),
                item.classId(), item.className(), item.subjectCode(), item.subjectLabel(), item.packetStatus(), item.revisionNumber(),
                item.totalStudents(), item.completedStudents(), item.completionState(), window.state(), window.opensAt(), window.closesAt(),
                item.returnedReason(), item.teacherId(), item.teacherName(), item.packetVersion(), item.actions());
    }

    private GradePacketQueueItem withActionsForTeacher(GradePacketQueueItem item) {
        return new GradePacketQueueItem(item.packetId(), item.reportingPeriodId(), item.reportingPeriodCode(), item.reportingPeriodLabel(),
                item.classId(), item.className(), item.subjectCode(), item.subjectLabel(), item.packetStatus(), item.revisionNumber(),
                item.totalStudents(), item.completedStudents(), item.completionState(), item.windowState(), item.windowOpensAt(),
                item.windowClosesAt(), item.returnedReason(), item.teacherId(), item.teacherName(), item.packetVersion(),
                Set.of("DRAFT", "RETURNED").contains(item.packetStatus()) ? ("READY".equals(item.completionState()) ? List.of("EDIT", "SUBMIT") : List.of("EDIT")) : List.of());
    }

    private GradePacketQueueItem withActionsForReviewer(GradePacketQueueItem item) {
        List<String> actions = "SUBMITTED".equals(item.packetStatus()) ? List.of("CLAIM")
                : "IN_REVIEW".equals(item.packetStatus()) ? List.of("ACCEPT", "RETURN") : List.of();
        return new GradePacketQueueItem(item.packetId(), item.reportingPeriodId(), item.reportingPeriodCode(), item.reportingPeriodLabel(),
                item.classId(), item.className(), item.subjectCode(), item.subjectLabel(), item.packetStatus(), item.revisionNumber(),
                item.totalStudents(), item.completedStudents(), item.completionState(), item.windowState(), item.windowOpensAt(),
                item.windowClosesAt(), item.returnedReason(), item.teacherId(), item.teacherName(), item.packetVersion(), actions);
    }

    private List<UUID> parseUuidArray(String raw) {
        if (raw == null || raw.length() < 2) return List.of();
        return Arrays.stream(raw.substring(1, raw.length() - 1).split(","))
                .map(String::trim).map(s -> s.replace("\"", ""))
                .filter(s -> !s.isBlank()).map(s -> { try { return UUID.fromString(s); } catch (IllegalArgumentException ex) { return null; } })
                .filter(Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public GradeEntryView view(UUID periodId, UUID classId, String requestedSubject) {
        AcademicReportingPeriod period = period(periodId);
        AcademicPeriodRules.assertRawGradePeriod(period);
        SchoolClass schoolClass = schoolClass(classId);
        teacherScope.assertClass(classId);
        List<GradeEntrySubjectView> available = availableSubjects(period.getAcademicSessionId(), classId, schoolClass.getName(), period.getStartDate());
        if (available.isEmpty()) {
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
        AcademicGradePacket packet = packets.findTopBySchoolIdAndReportingPeriodIdAndClassIdAndSubjectCodeOrderByRevisionNumberDesc(
                TenantContext.get(), periodId, classId, subjectCode).orElse(null);
        Map<UUID, List<AcademicGrade>> gradeByStudent = studentIds.isEmpty() ? Map.of() : grades
                .findByPacketIdAndStudentIdInAndSubjectCodeOrderByStudentIdAscAssessmentIdAsc(
                        packet == null ? UUID.randomUUID() : packet.getId(), studentIds, subjectCode)
                .stream().collect(Collectors.groupingBy(AcademicGrade::getStudentId));
        Map<UUID, SubjectResultComment> commentByStudent = studentIds.isEmpty() ? Map.of() : comments
                .findByPacketIdAndStudentIdInAndSubjectCode(packet == null ? UUID.randomUUID() : packet.getId(), studentIds, subjectCode)
                .stream().collect(Collectors.toMap(SubjectResultComment::getStudentId, Function.identity()));
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
                    c == null ? null : c.getComment(), c == null ? null : c.getAppreciationCode(), workflow);
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
        boolean returnedOwner = packet == null || !"RETURNED".equals(packet.getStatus()) ||
                (restricted && currentEmployeeId() != null && currentEmployeeId().equals(subject.teacherId()));
        boolean canEdit = editableStatus && returnedOwner && (!restricted || subjectReady(subject));
        boolean submissionWindowOpen = false;
        AcademicWindowPolicyService.WindowView submissionWindow = null;
        if (subjectReady(subject)) {
            submissionWindow = windows.effective(period.getId(), AcademicWindowPolicyService.Action.TEACHER_SUBMISSION);
            submissionWindowOpen = submissionWindow.open();
            if (!submissionWindowOpen) {
                String code = Set.of("SCHEDULED", "CLOSED").contains(submissionWindow.state())
                        ? "WINDOW_CLOSED" : "WINDOW_NOT_CONFIGURED";
                submissionBlockers.add(new GradeEntryBlockerView(code, subject.code(), null,
                        "La fenÃªtre de soumission des enseignants n'est pas ouverte.",
                        "The teacher-submission window is not open.", "academic-sessions", "BLOCKER"));
            }
        }
        boolean canSubmit = canEdit && submissionWindowOpen && submissionBlockers.isEmpty();
        boolean canReview = Set.of("admin", "principal", "dean_of_studies", "censor").contains(currentRole());
        int completed = (int) rows.stream().filter(r -> rowComplete(definition, r, subject.remarkRequired())).count();
        return new GradeEntryView(period.getAcademicSessionId(), periodId, classId, schoolClass.getName(),
                subject.code(), subject.label(), subject.coefficient(), subject.teacherId(), subject.teacherName(),
                packet == null ? "DRAFT" : packet.getStatus(), packet == null ? 0 : packet.getVersion(),
                definition, rows, rows.size(), completed, blockers, available, completionBlockers,
                submissionBlockers, List.of(), readiness,
                new GradeEntryCapabilitiesView(canEdit, canSubmit, canReview, restricted,
                        restricted && !subjectReady(subject) ? "Repair the responsible teacher assignment before editing or submitting." : null), List.of());
    }

    @Transactional
    public GradeEntryView save(GradeEntrySaveRequest in) {
        AcademicReportingPeriod period = period(in.reportingPeriodId());
        AcademicPeriodRules.assertRawGradePeriod(period);
        SchoolClass schoolClass = schoolClass(in.classId());
        teacherScope.assertClass(in.classId());
        String subjectCode = in.subjectCode().trim().toUpperCase(Locale.ROOT);
        GradeEntrySubjectView subject = availableSubjects(period.getAcademicSessionId(), in.classId(), schoolClass.getName(), period.getStartDate()).stream()
                .filter(x -> x.code().equalsIgnoreCase(subjectCode)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("La matière n'est pas affectée à cette classe"));
        assertSubjectAccess(in.classId(), schoolClass.getName(), period.getAcademicSessionId(), period.getStartDate(), subjectCode);
        invalidateValidatedBulletins(period.getId(), period.getAcademicSessionId(), in.classId());
        AcademicGradePacket packet = preparePacketForSave(packet(period, in.classId(), subjectCode, subject), period, in.classId(), subject);
        packet = ensurePacketSaved(packet);
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
                AcademicGrade grade = packet.getId() == null ? null : grades.findByPacketIdAndStudentIdAndAssessmentIdAndSubjectCode(
                        packet.getId(), row.studentId(), assessment.getId(), subjectCode).orElse(null);
                if (grade == null) grade = grades.findBySchoolIdAndStudentIdAndAssessmentIdAndSubjectCode(
                        TenantContext.get(), row.studentId(), assessment.getId(), subjectCode).orElse(null);
                if (grade == null) grade = new AcademicGrade();
                if (cell.version() != null && grade.getId() != null && cell.version() != grade.getVersion()) {
                    throw ApiException.conflict("Une note de " + studentName(row.studentId()) + " a été modifiée par un autre utilisateur");
                }
                grade.setSchoolId(TenantContext.get()); grade.setAcademicSessionId(period.getAcademicSessionId()); grade.setReportingPeriodId(period.getId());
                grade.setAssessmentId(assessment.getId()); grade.setStudentId(row.studentId()); grade.setEnrollmentId(enrollment.getId());
                grade.setSubjectCode(subjectCode); grade.setMark("SCORED".equals(status) ? cell.mark() : null);
                grade.setValueStatus(status); grade.setWorkflowStatus("DRAFT"); grade.setEnteredBy(currentUserId()); grade.setTeacherId(subject.teacherId());
                grade.setPacketId(packet.getId()); grade.setPacketRevision(packet.getRevisionNumber());
                grades.save(grade);
            }
            SubjectResultComment comment = comments.findByPacketIdAndStudentIdAndReportingPeriodIdAndSubjectCode(
                    packet.getId(), row.studentId(), period.getId(), subjectCode).orElseGet(SubjectResultComment::new);
            comment.setSchoolId(TenantContext.get()); comment.setAcademicSessionId(period.getAcademicSessionId()); comment.setReportingPeriodId(period.getId());
            comment.setStudentId(row.studentId()); comment.setEnrollmentId(enrollment.getId()); comment.setSubjectCode(subjectCode);
            comment.setComment(SubjectCommentPolicy.sanitize(row.comment())); comment.setAppreciationCode(SubjectCommentPolicy.appreciation(row.appreciationCode()));
            comment.setWorkflowStatus("DRAFT"); comment.setTeacherId(subject.teacherId()); comment.setAuthorUserId(currentUserId());
            comment.setPacketId(packet.getId()); comment.setPacketRevision(packet.getRevisionNumber());
            comments.save(comment);
        }
        packet.setStatus("DRAFT"); packet.setLastSavedBy(currentUserId()); packet.setLastSavedAt(Instant.now()); savePacket(packet);
        recordPacketTransition(packet, previousPacketStatus, "DRAFT", "Correction draft saved");
        return view(period.getId(), in.classId(), subjectCode);
    }

    /**
     * Row-safe save contract: validation/conflict is isolated to a cell, so a
     * bad row never rolls back successful rows in the same request. The request
     * UUID is persisted on both the request ledger and each changed grade.
     */
    @Transactional
    public GradeEntryView saveRowSafe(GradeEntrySaveRequest in) {
        AcademicReportingPeriod period = period(in.reportingPeriodId());
        AcademicPeriodRules.assertRawGradePeriod(period);
        SchoolClass schoolClass = schoolClass(in.classId());
        teacherScope.assertClass(in.classId());
        String subjectCode = in.subjectCode().trim().toUpperCase(Locale.ROOT);
        GradeEntrySubjectView subject = availableSubjects(period.getAcademicSessionId(), in.classId(), schoolClass.getName(), period.getStartDate()).stream()
                .filter(x -> x.code().equalsIgnoreCase(subjectCode)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("La matière n'est pas affectée à cette classe"));
        assertSubjectAccess(in.classId(), schoolClass.getName(), period.getAcademicSessionId(), period.getStartDate(), subjectCode);
        windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.GRADE_ENTRY);
        UUID requestId = parseRequestId(in.requestId());
        jdbc.update("INSERT INTO academic_grade_save_request(id,school_id,actor_user_id) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                requestId, TenantContext.get(), currentUserId());
        AcademicGradePacket packet = preparePacketForSave(packet(period, in.classId(), subjectCode, subject), period, in.classId(), subject);
        packet = ensurePacketSaved(packet);
        if (in.packetVersion() != null && packet.getId() != null && in.packetVersion() != packet.getVersion())
            throw ApiException.conflict("La feuille de saisie a été modifiée. Rechargez-la avant d'enregistrer.");
        Map<UUID, AcademicAssessment> definition = assessments.findApplicable(TenantContext.get(), period.getId(), in.classId(), subjectCode)
                .stream().collect(Collectors.toMap(AcademicAssessment::getId, Function.identity()));
        CanonicalSubject canonical = canonicalSubject(period.getAcademicSessionId(), in.classId(), subjectCode);
        List<GradeEntryRowResult> results = new ArrayList<>();
        Set<UUID> rosterIds = roster(period.getAcademicSessionId(), in.classId()).stream().map(RosterStudent::id).collect(Collectors.toSet());
        for (GradeEntryStudentUpsert row : in.students()) {
            List<GradeEntryCellUpsert> cells = row.values() == null ? List.of() : row.values();
            if (!rosterIds.contains(row.studentId())) {
                for (GradeEntryCellUpsert cell : cells) results.add(result(row.studentId(), cell.assessmentId(), "FORBIDDEN", null, "MISSING", 0, Map.of("student", "Student is not in this class for this session."), false));
                continue;
            }
            StudentEnrollment enrollment;
            try {
                enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(TenantContext.get(), row.studentId(), period.getAcademicSessionId(), "ACTIVE")
                        .filter(e -> in.classId().equals(e.getSchoolClassId())).orElseThrow(() -> ApiException.badRequest("Inscription active introuvable pour l'élève"));
            } catch (ApiException ex) {
                for (GradeEntryCellUpsert cell : cells) results.add(result(row.studentId(), cell.assessmentId(), "INVALID", null, "MISSING", 0, Map.of("enrollment", ex.getMessage()), false));
                continue;
            }
            for (GradeEntryCellUpsert cell : cells) {
                AcademicGrade grade = null;
                try {
                    AcademicAssessment assessment = definition.get(cell.assessmentId());
                    if (assessment == null) throw ApiException.badRequest("L'évaluation n'appartient pas à cette période");
                    String status = normalizeStatus(cell.valueStatus());
                    validateMark(cell.mark(), status, assessment);
                    grade = grades.findByPacketIdAndStudentIdAndAssessmentIdAndSubjectCode(
                            packet.getId(), row.studentId(), assessment.getId(), subjectCode).orElse(null);
                    if (grade == null) grade = grades.findBySchoolIdAndStudentIdAndAssessmentIdAndSubjectCode(
                            TenantContext.get(), row.studentId(), assessment.getId(), subjectCode).orElse(null);
                    if (cell.version() != null && grade != null && cell.version() != grade.getVersion()) {
                        results.add(result(row.studentId(), assessment.getId(), "CONFLICT", grade.getMark(), grade.getValueStatus(), grade.getVersion(), Map.of("version", "The row changed on the server."), true));
                        continue;
                    }
                    if (grade != null && Objects.equals(grade.getMark(), "SCORED".equals(status) ? cell.mark() : null)
                            && Objects.equals(grade.getValueStatus(), status)) {
                        results.add(result(row.studentId(), assessment.getId(), "UNCHANGED", grade.getMark(), grade.getValueStatus(), grade.getVersion(), Map.of(), false));
                        continue;
                    }
                    if (grade == null) grade = new AcademicGrade();
                    grade.setSchoolId(TenantContext.get()); grade.setAcademicSessionId(period.getAcademicSessionId()); grade.setReportingPeriodId(period.getId());
                    grade.setAssessmentId(assessment.getId()); grade.setStudentId(row.studentId()); grade.setEnrollmentId(enrollment.getId());
                    grade.setSubjectCode(subjectCode); grade.setMark("SCORED".equals(status) ? cell.mark() : null); grade.setValueStatus(status);
                    grade.setWorkflowStatus("DRAFT"); grade.setEnteredBy(currentUserId()); grade.setTeacherId(subject.teacherId());
                    grade.setCurriculumVersionId(canonical.versionId()); grade.setCurriculumSubjectId(canonical.subjectId()); grade.setResponsibleAssignmentId(canonical.assignmentId()); grade.setLastRequestId(requestId);
                    grade.setPacketId(packet.getId()); grade.setPacketRevision(packet.getRevisionNumber());
                    assessment.setCurriculumVersionId(canonical.versionId()); assessment.setCurriculumSubjectId(canonical.subjectId());
                    grades.saveAndFlush(grade);
                    jdbc.update("UPDATE academic_grade SET policy_decision=?::jsonb WHERE id=? AND school_id=?", "{\"window\":\"GRADE_ENTRY\",\"requestId\":\"" + requestId + "\"}", grade.getId(), TenantContext.get());
                    results.add(result(row.studentId(), assessment.getId(), "SAVED", grade.getMark(), grade.getValueStatus(), grade.getVersion(), Map.of(), false));
                } catch (ApiException ex) {
                    results.add(result(row.studentId(), cell.assessmentId(), ex.getCode().equals("FORBIDDEN") ? "FORBIDDEN" : "INVALID",
                            grade == null ? null : grade.getMark(), grade == null ? "MISSING" : grade.getValueStatus(), grade == null ? 0 : grade.getVersion(), Map.of("value", ex.getMessage()), false));
                }
            }
            saveCommentRowSafe(period, in.classId(), subjectCode, subject.teacherId(), packet, row, enrollment, results);
        }
        String previousPacketStatus = packet.getStatus();
        packet.setStatus("DRAFT"); packet.setLastSavedBy(currentUserId()); packet.setLastSavedAt(Instant.now()); savePacket(packet);
        recordPacketTransition(packet, previousPacketStatus, "DRAFT", "Draft saved", "DRAFT_SAVE", results.stream().map(GradeEntryRowResult::studentId).distinct().toList());
        persistSaveResults(requestId, subjectCode, results);
        return view(period.getId(), in.classId(), subjectCode).withSaveResults(results);
    }

    private void saveCommentRowSafe(AcademicReportingPeriod period, UUID classId, String subjectCode, UUID teacherId,
                                    AcademicGradePacket packet,
                                    GradeEntryStudentUpsert row, StudentEnrollment enrollment, List<GradeEntryRowResult> results) {
        if (row.comment() != null && row.comment().length() > 500) {
            results.add(result(row.studentId(), null, "INVALID", null, "MISSING", 0, Map.of("comment", "Comment cannot exceed 500 characters."), false));
            return;
        }
        SubjectResultComment comment = comments.findByPacketIdAndStudentIdAndReportingPeriodIdAndSubjectCode(
                packet.getId(), row.studentId(), period.getId(), subjectCode).orElseGet(SubjectResultComment::new);
        comment.setSchoolId(TenantContext.get()); comment.setAcademicSessionId(period.getAcademicSessionId()); comment.setReportingPeriodId(period.getId());
        comment.setStudentId(row.studentId()); comment.setEnrollmentId(enrollment.getId()); comment.setSubjectCode(subjectCode);
        comment.setComment(SubjectCommentPolicy.sanitize(row.comment())); comment.setAppreciationCode(SubjectCommentPolicy.appreciation(row.appreciationCode())); comment.setWorkflowStatus("DRAFT"); comment.setTeacherId(teacherId);
        comment.setAuthorUserId(currentUserId());
        comment.setPacketId(packet.getId()); comment.setPacketRevision(packet.getRevisionNumber());
        comments.saveAndFlush(comment);
    }

    private GradeEntryRowResult result(UUID studentId, UUID assessmentId, String outcome, BigDecimal mark, String status,
                                       long version, Map<String,String> errors, boolean retryable) {
        return new GradeEntryRowResult(studentId, assessmentId, outcome, mark, status, version, errors, retryable);
    }

    private void persistSaveResults(UUID requestId, String subjectCode, List<GradeEntryRowResult> results) {
        for (GradeEntryRowResult row : results) {
            jdbc.update("""
                    INSERT INTO academic_grade_save_result
                        (request_id,school_id,student_id,assessment_id,subject_code,outcome,current_value,field_errors,retryable)
                    VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb,?)
                    ON CONFLICT (request_id,student_id,assessment_id,subject_code) DO NOTHING
                    """, requestId, TenantContext.get(), row.studentId(), row.assessmentId(), subjectCode, row.outcome(),
                    currentValueJson(row), fieldErrorsJson(row.fieldErrors()), row.retryable());
        }
    }

    private String currentValueJson(GradeEntryRowResult row) {
        return "{\"mark\":" + (row.currentMark() == null ? "null" : row.currentMark().toPlainString())
                + ",\"valueStatus\":" + jsonString(row.currentValueStatus())
                + ",\"version\":" + row.currentVersion() + "}";
    }

    private String fieldErrorsJson(Map<String, String> errors) {
        return errors.entrySet().stream()
                .map(e -> jsonString(e.getKey()) + ":" + jsonString(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private UUID parseRequestId(String raw) {
        if (raw == null || raw.isBlank()) return UUID.randomUUID();
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ex) { throw ApiException.badRequest("requestId doit être un UUID"); }
    }

    private CanonicalSubject canonicalSubject(UUID sessionId, UUID classId, String subjectCode) {
        return jdbc.query("""
            SELECT c.id,c.curriculum_version_id,t.id
              FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id
              JOIN academic_curriculum_version v ON v.id=c.curriculum_version_id AND v.state='PUBLISHED'
              LEFT JOIN LATERAL (SELECT ast.id FROM academic_class_subject_teacher ast
                    WHERE ast.school_id=c.school_id AND ast.academic_session_id=c.academic_session_id
                      AND ast.class_id=c.class_id AND ast.subject_id=c.subject_id AND ast.role='RESPONSIBLE' AND ast.active=true
                    ORDER BY ast.effective_from DESC NULLS LAST,ast.created_at DESC LIMIT 1) t ON true
             WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=? AND upper(s.code)=upper(?)
             ORDER BY v.version_number DESC LIMIT 1
            """, rs -> rs.next() ? new CanonicalSubject(rs.getObject(1,UUID.class), rs.getObject(2,UUID.class), rs.getObject(3,UUID.class)) : null,
                TenantContext.get(), sessionId, classId, subjectCode);
    }

    private record CanonicalSubject(UUID subjectId, UUID versionId, UUID assignmentId) {}

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
        teacherScope.assertClass(in.classId());
        String subjectCode = in.subjectCode().trim().toUpperCase(Locale.ROOT);
        GradeEntrySubjectView subject = availableSubjects(period.getAcademicSessionId(), in.classId(), schoolClass.getName(), period.getStartDate()).stream().filter(x -> x.code().equalsIgnoreCase(subjectCode)).findFirst().orElseThrow(() -> ApiException.badRequest("La matière n'est pas affectée à cette classe"));
        assertSubjectAccess(in.classId(), schoolClass.getName(), period.getAcademicSessionId(), period.getStartDate(), subjectCode);
        AcademicGradePacket packet = packet(period, in.classId(), subjectCode, subject);
        String previousPacketStatus = packet.getStatus();
        if (in.packetVersion() != null && packet.getId() != null && in.packetVersion() != packet.getVersion()) throw ApiException.conflict("La feuille de saisie a été modifiée entre-temps. Rechargez-la.");
        String action = in.action().trim().toUpperCase(Locale.ROOT);
        if ("SUBMIT".equals(action)) {
            if (!Set.of("DRAFT", "RETURNED").contains(previousPacketStatus)) {
                throw ApiException.conflict("Cette feuille doit être en brouillon ou retournée avant une nouvelle soumission.");
            }
            windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.TEACHER_SUBMISSION);
            GradeEntryView current = view(period.getId(), in.classId(), subjectCode);
            if (!current.submissionBlockers().isEmpty()) throw ApiException.conflict(
                    "La saisie ne peut pas être soumise tant que les éléments requis ne sont pas corrigés.");
            adoptAssignment(packet, subject);
            packet.setStatus("SUBMITTED"); packet.setSubmittedBy(currentUserId()); packet.setSubmittedAt(Instant.now());
            updateWorkflow(packet, "SUBMITTED");
        } else {
            requireReviewer();
            windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.REVIEW);
            if (!Set.of("CLAIM", "OPEN", "ACCEPT", "RETURN").contains(action)) {
                throw ApiException.badRequest("Action de revue invalide : utilisez CLAIM, ACCEPT ou RETURN");
            }
            if (Set.of("CLAIM", "OPEN").contains(action)) {
                if (!"SUBMITTED".equals(previousPacketStatus)) {
                    throw ApiException.conflict("Seule une feuille soumise peut être ouverte en revue.");
                }
                packet.setStatus("IN_REVIEW"); packet.setClaimedBy(currentUserId()); packet.setClaimedAt(Instant.now());
                updateWorkflow(packet, "IN_REVIEW");
            } else {
                if (!"IN_REVIEW".equals(previousPacketStatus)) {
                    throw ApiException.conflict("La feuille doit d'abord être ouverte par un reviewer.");
                }
                if ("RETURN".equals(action) && (in.reason() == null || in.reason().isBlank())) {
                throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "RETURN_REASON_REQUIRED",
                        "Le motif du retour est obligatoire.", "reason", "Explain what the teacher must correct.");
                }
                packet.setStatus("ACCEPT".equals(action) ? "ACCEPTED" : "RETURNED");
                packet.setReviewReason(in.reason() == null ? null : in.reason().trim());
                packet.setReturnedAt("RETURN".equals(action) ? Instant.now() : null);
                packet.setReviewedBy(currentUserId()); packet.setReviewedAt(Instant.now());
                updateWorkflow(packet, packet.getStatus());
            }
        }
        packet.setLastSavedBy(currentUserId()); packet.setLastSavedAt(Instant.now());
        savePacket(packet);
        List<UUID> affected = roster(period.getAcademicSessionId(), in.classId()).stream().map(RosterStudent::id).toList();
        recordPacketTransition(packet, previousPacketStatus, packet.getStatus(), in.reason(),
                Set.of("CLAIM", "OPEN").contains(action) ? "CLAIM" : action, affected);
        return view(period.getId(), in.classId(), subjectCode);
    }

    private void updateWorkflow(AcademicGradePacket packet, String state) {
        List<UUID> ids = roster(packet.getAcademicSessionId(), packet.getClassId()).stream().map(RosterStudent::id).toList();
        if (!ids.isEmpty()) jdbc.update("UPDATE academic_grade SET workflow_status=? WHERE school_id=? AND packet_id=? AND student_id = ANY(?)", state, TenantContext.get(), packet.getId(), ids.toArray(UUID[]::new));
        // The PostgreSQL ANY update above is intentionally paired with a no-op-safe
        // comment update below; comments are not required for calculation.
        if (!ids.isEmpty()) jdbc.update("UPDATE subject_result_comment SET workflow_status=? WHERE school_id=? AND packet_id=? AND student_id = ANY(?)", state, TenantContext.get(), packet.getId(), ids.toArray(UUID[]::new));
    }

    private List<GradeEntrySubjectView> availableSubjects(UUID sessionId, UUID classId, String className,
                                                           java.time.LocalDate effectiveDate) {
        return jdbc.query("""
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
    }

    private void assertSubjectAccess(UUID classId, String className, UUID sessionId,
                                     java.time.LocalDate effectiveDate, String subjectCode) {
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
        return blockers.stream().map(message -> {
            int separator = message == null ? -1 : message.indexOf(" · ");
            String student = separator > 0 ? message.substring(0, separator) : null;
            String detail = separator > 0 ? message.substring(separator + 3) : message;
            return new GradeEntryBlockerView("GRADE_ENTRY_INCOMPLETE", subjectCode, student,
                    detail, detail, "grade-entry", "BLOCKER");
        }).toList();
    }

    private List<RosterStudent> roster(UUID sessionId, UUID classId) {
        return jdbc.query("""
                SELECT s.id, s.matricule, trim(s.last_name || ' ' || s.first_name)
                  FROM student_enrollment se JOIN student s ON s.id=se.student_id
                 WHERE se.school_id=? AND se.academic_session_id=? AND se.school_class_id=?
                   AND se.status='ACTIVE' AND s.active=true
                 ORDER BY s.last_name, s.first_name, s.matricule
                """, (rs, n) -> new RosterStudent(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                TenantContext.get(), sessionId, classId);
    }

    private AcademicGradePacket packet(AcademicReportingPeriod period, UUID classId, String subjectCode,
                                       GradeEntrySubjectView subject) {
        return packets.findTopBySchoolIdAndReportingPeriodIdAndClassIdAndSubjectCodeOrderByRevisionNumberDesc(
                TenantContext.get(), period.getId(), classId, subjectCode).orElseGet(() -> {
            AcademicGradePacket p = new AcademicGradePacket(); p.setSchoolId(TenantContext.get()); p.setAcademicSessionId(period.getAcademicSessionId());
            p.setReportingPeriodId(period.getId()); p.setClassId(classId); p.setSubjectCode(subjectCode); return p;
        });
    }

    private AcademicGradePacket preparePacketForSave(AcademicGradePacket current, AcademicReportingPeriod period,
                                                      UUID classId, GradeEntrySubjectView subject) {
        if (current.getId() != null && Set.of("SUBMITTED", "IN_REVIEW").contains(current.getStatus())) {
            throw ApiException.conflict("Cette feuille est en revue et ne peut pas être modifiée. Attendez un retour ou rechargez son état.");
        }
        if (current.getId() != null && Set.of("ACCEPTED", "LOCKED").contains(current.getStatus())) {
            windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.CORRECTION);
            assertCorrectionOwner(subject);
            AcademicGradePacket revision = new AcademicGradePacket();
            revision.setSchoolId(current.getSchoolId()); revision.setAcademicSessionId(current.getAcademicSessionId());
            revision.setReportingPeriodId(current.getReportingPeriodId()); revision.setClassId(current.getClassId());
            revision.setSubjectCode(current.getSubjectCode()); revision.setTeacherId(subject.teacherId());
            revision.setResponsibleAssignmentId(subject.assignmentReadiness().assignmentId());
            revision.setResponsibleAssignmentVersion(subject.assignmentReadiness().assignmentVersion());
            revision.setRevisionNumber(current.getRevisionNumber() + 1); revision.setSupersedesPacketId(current.getId());
            revision.setStatus("DRAFT");
            return packets.saveAndFlush(revision);
        }
        if ("RETURNED".equals(current.getStatus())) assertCorrectionOwner(subject);
        adoptAssignment(current, subject);
        return current;
    }

    private void assertCorrectionOwner(GradeEntrySubjectView subject) {
        if (!restrictedTeacher() || currentEmployeeId() == null || !currentEmployeeId().equals(subject.teacherId())) {
            throw ApiException.forbidden("Seul l'enseignant responsable effectif ou son remplacement autorisé peut corriger cette feuille.");
        }
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
        packet.setTeacherId(subject.teacherId());
        packet.setResponsibleAssignmentId(subject.assignmentReadiness() == null ? null : subject.assignmentReadiness().assignmentId());
        packet.setResponsibleAssignmentVersion(subject.assignmentReadiness() == null ? null : subject.assignmentReadiness().assignmentVersion());
        if (packet.getId() != null && subjectReady(subject)) syncMutableDraftOwnership(packet, subject.teacherId());
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
        recordPacketTransition(packet, from, to, reason, "STATE_CHANGE", List.of());
    }

    private void recordPacketTransition(AcademicGradePacket packet, String from, String to, String reason,
                                        String eventType, List<UUID> affectedRows) {
        if (packet.getId() == null || Objects.equals(from, to)) return;
        jdbc.update("""
            INSERT INTO academic_grade_packet_transition
                (school_id,packet_id,from_status,to_status,reason,actor_user_id,event_type,affected_rows,reviewer_user_id,reviewed_at)
            VALUES (?,?,?,?,?,?,?,?::jsonb,?,?)
            """, TenantContext.get(), packet.getId(), from, to,
                reason == null || reason.isBlank() ? null : reason.trim(), currentUserId(), eventType,
                affectedRowsJson(affectedRows), packet.getReviewedBy(),
                packet.getReviewedAt() == null ? null : java.sql.Timestamp.from(packet.getReviewedAt()));
    }

    private String affectedRowsJson(List<UUID> rows) {
        return rows == null ? "[]" : rows.stream().filter(Objects::nonNull)
                .map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private void savePacket(AcademicGradePacket packet) {
        try {
            packets.saveAndFlush(packet);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw ApiException.conflict("Cette feuille a été modifiée par un autre reviewer. Rechargez-la avant de réessayer.");
        }
    }

    /**
     * Grade and comment rows retain the packet id as their workflow anchor.
     * Persist a brand-new draft before inserting those rows; otherwise the
     * JPA entity has no id yet and the rows become detached from the packet.
     */
    private AcademicGradePacket ensurePacketSaved(AcademicGradePacket packet) {
        if (packet.getId() != null) return packet;
        try {
            return packets.saveAndFlush(packet);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw ApiException.conflict("Cette feuille a été modifiée par un autre utilisateur. Rechargez-la avant d'enregistrer.");
        }
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
    private boolean isReviewer() { return Set.of("admin", "principal", "dean_of_studies", "censor").contains(currentRole()); }
    private void requireReviewer() { if (!Set.of("admin","principal","dean_of_studies","censor").contains(currentRole())) throw ApiException.forbidden("Seule la direction peut accepter ou retourner une feuille de notes"); }
    private record RosterStudent(UUID id, String matricule, String name) {}
}
