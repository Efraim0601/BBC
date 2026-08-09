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
    private final TeacherScopeService teacherScope;
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
                             JdbcTemplate jdbc) {
        this.periods = periods; this.assessments = assessments; this.grades = grades;
        this.comments = comments; this.packets = packets; this.enrollments = enrollments;
        this.students = students; this.subjects = subjects; this.classes = classes;
        this.windows = windows; this.teacherScope = teacherScope; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public GradeEntryView view(UUID periodId, UUID classId, String requestedSubject) {
        AcademicReportingPeriod period = period(periodId);
        SchoolClass schoolClass = schoolClass(classId);
        teacherScope.assertClass(classId);
        List<GradeEntrySubjectView> available = availableSubjects(period.getAcademicSessionId(), classId, schoolClass.getName());
        if (available.isEmpty()) {
            throw ApiException.conflict("Aucune matière n'est affectée à cette classe. Configurez Paramètres → Matières par classe avant la saisie.");
        }
        String subjectCode = requestedSubject == null || requestedSubject.isBlank()
                ? available.get(0).code() : requestedSubject.trim().toUpperCase(Locale.ROOT);
        GradeEntrySubjectView subject = available.stream().filter(x -> x.code().equalsIgnoreCase(subjectCode)).findFirst()
                .orElseThrow(() -> ApiException.forbidden("Cette matière n'est pas affectée à la classe ou ne vous est pas attribuée"));
        assertSubjectAccess(classId, schoolClass.getName(), subjectCode);

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
        List<String> blockers = blockers(definition, rows);
        int completed = (int) rows.stream().filter(r -> rowComplete(definition, r)).count();
        return new GradeEntryView(period.getAcademicSessionId(), periodId, classId, schoolClass.getName(),
                subject.code(), subject.label(), subject.coefficient(), subject.teacherId(), subject.teacherName(),
                packet == null ? "DRAFT" : packet.getStatus(), packet == null ? 0 : packet.getVersion(),
                definition, rows, rows.size(), completed, blockers, available);
    }

    @Transactional
    public GradeEntryView save(GradeEntrySaveRequest in) {
        AcademicReportingPeriod period = period(in.reportingPeriodId());
        SchoolClass schoolClass = schoolClass(in.classId());
        teacherScope.assertClass(in.classId());
        String subjectCode = in.subjectCode().trim().toUpperCase(Locale.ROOT);
        GradeEntrySubjectView subject = availableSubjects(period.getAcademicSessionId(), in.classId(), schoolClass.getName()).stream()
                .filter(x -> x.code().equalsIgnoreCase(subjectCode)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("La matière n'est pas affectée à cette classe"));
        assertSubjectAccess(in.classId(), schoolClass.getName(), subjectCode);
        invalidateValidatedBulletins(period.getId(), period.getAcademicSessionId(), in.classId());
        AcademicGradePacket packet = packet(period, in.classId(), subjectCode, subject.teacherId());
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
        packet.setStatus("DRAFT"); packets.saveAndFlush(packet);
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
        SchoolClass schoolClass = schoolClass(in.classId());
        teacherScope.assertClass(in.classId());
        String subjectCode = in.subjectCode().trim().toUpperCase(Locale.ROOT);
        GradeEntrySubjectView subject = availableSubjects(period.getAcademicSessionId(), in.classId(), schoolClass.getName()).stream().filter(x -> x.code().equalsIgnoreCase(subjectCode)).findFirst().orElseThrow(() -> ApiException.badRequest("La matière n'est pas affectée à cette classe"));
        assertSubjectAccess(in.classId(), schoolClass.getName(), subjectCode);
        AcademicGradePacket packet = packet(period, in.classId(), subjectCode, subject.teacherId());
        if (in.packetVersion() != null && packet.getId() != null && in.packetVersion() != packet.getVersion()) throw ApiException.conflict("La feuille de saisie a été modifiée entre-temps. Rechargez-la.");
        if ("SUBMIT".equalsIgnoreCase(in.action())) {
            windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.GRADE_ENTRY);
            GradeEntryView current = view(period.getId(), in.classId(), subjectCode);
            if (!current.blockers().isEmpty()) throw ApiException.conflict("La saisie est incomplète : " + String.join("; ", current.blockers()));
            packet.setStatus("SUBMITTED"); packet.setSubmittedBy(currentUserId()); packet.setSubmittedAt(Instant.now());
            updateWorkflow(period.getId(), in.classId(), subjectCode, "SUBMITTED");
        } else {
            requireReviewer();
            windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.REVIEW);
            String action = in.action().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("ACCEPT", "RETURN").contains(action)) throw ApiException.badRequest("Action de revue invalide : utilisez ACCEPT ou RETURN");
            packet.setStatus("ACCEPT".equals(action) ? "ACCEPTED" : "RETURNED"); packet.setReviewReason(in.reason());
            packet.setReviewedBy(currentUserId()); packet.setReviewedAt(Instant.now());
            updateWorkflow(period.getId(), in.classId(), subjectCode, packet.getStatus());
        }
        packets.saveAndFlush(packet);
        return view(period.getId(), in.classId(), subjectCode);
    }

    private void updateWorkflow(UUID periodId, UUID classId, String subjectCode, String state) {
        List<UUID> ids = roster(period(periodId).getAcademicSessionId(), classId).stream().map(RosterStudent::id).toList();
        if (!ids.isEmpty()) jdbc.update("UPDATE academic_grade SET workflow_status=? WHERE school_id=? AND reporting_period_id=? AND subject_code=? AND student_id = ANY(?)", state, TenantContext.get(), periodId, subjectCode, jdbc.getDataSource() == null ? new UUID[0] : ids.toArray(UUID[]::new));
        // The PostgreSQL ANY update above is intentionally paired with a no-op-safe
        // comment update below; comments are not required for calculation.
        if (!ids.isEmpty()) jdbc.update("UPDATE subject_result_comment SET workflow_status=? WHERE school_id=? AND reporting_period_id=? AND subject_code=? AND student_id = ANY(?)", state, TenantContext.get(), periodId, subjectCode, ids.toArray(UUID[]::new));
    }

    private List<GradeEntrySubjectView> availableSubjects(UUID sessionId, UUID classId, String className) {
        return jdbc.query("""
                SELECT DISTINCT ON (s.code) s.code, COALESCE(s.label->>'fr', s.label->>'en', s.code),
                       COALESCE(cur.coefficient, scc.coef, s.coef), COALESCE(ast.employee_id, e.id), COALESCE(ast_e.name, e.name)
                  FROM subject s
                  LEFT JOIN subject_class_coef scc ON scc.subject_id=s.id AND scc.class_id=? AND scc.school_id=?
                  LEFT JOIN academic_curriculum_subject cur ON cur.subject_id=s.id AND cur.class_id=? AND cur.school_id=? AND cur.academic_session_id=?
                  LEFT JOIN academic_class_subject_teacher ast ON ast.subject_id=s.id AND ast.class_id=? AND ast.school_id=? AND ast.academic_session_id=? AND ast.role='RESPONSIBLE' AND ast.active
                  LEFT JOIN employee ast_e ON ast_e.id=ast.employee_id
                  LEFT JOIN teacher_class tc ON tc.class_id=?
                  LEFT JOIN teacher_subject ts ON ts.employee_id=tc.employee_id AND ts.subject_id=s.id
                  LEFT JOIN employee e ON e.id=tc.employee_id
                 WHERE s.school_id=? AND (cur.id IS NOT NULL OR scc.id IS NOT NULL)
                 ORDER BY s.code, ast_e.name NULLS LAST, e.name NULLS LAST
                """, (rs, n) -> new GradeEntrySubjectView(rs.getString(1), rs.getString(2), rs.getInt(3),
                        rs.getObject(4, UUID.class), rs.getString(5)), classId, TenantContext.get(), classId, TenantContext.get(), sessionId,
                classId, TenantContext.get(), sessionId, classId, TenantContext.get());
    }

    private void assertSubjectAccess(UUID classId, String className, String subjectCode) {
        UUID employeeId = currentEmployeeId();
        if (employeeId == null) return;
        Boolean restricted = jdbc.queryForObject("SELECT ? IN ('teacher','form_teacher')", Boolean.class, currentRole());
        if (!Boolean.TRUE.equals(restricted)) return;
        Integer allowed = jdbc.queryForObject("""
                SELECT count(*) FROM teacher_class tc
                 JOIN teacher_subject ts ON ts.employee_id=tc.employee_id
                 JOIN subject s ON s.id=ts.subject_id
                WHERE tc.employee_id=? AND tc.class_id=? AND upper(s.code)=upper(?)
                   OR tc.employee_id=? AND tc.class_id=? AND (SELECT form_class FROM employee WHERE id=?)=?
                """, Integer.class, employeeId, classId, subjectCode, employeeId, classId, employeeId, className);
        if (allowed == null || allowed == 0) throw ApiException.forbidden("Vous n'êtes pas affecté à cette matière dans cette classe");
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

    private AcademicGradePacket packet(AcademicReportingPeriod period, UUID classId, String subjectCode, UUID teacherId) {
        return packets.findBySchoolIdAndReportingPeriodIdAndClassIdAndSubjectCode(TenantContext.get(), period.getId(), classId, subjectCode).orElseGet(() -> {
            AcademicGradePacket p = new AcademicGradePacket(); p.setSchoolId(TenantContext.get()); p.setAcademicSessionId(period.getAcademicSessionId());
            p.setReportingPeriodId(period.getId()); p.setClassId(classId); p.setSubjectCode(subjectCode); p.setTeacherId(teacherId); return p;
        });
    }

    private List<String> blockers(List<GradeEntryAssessmentView> definition, List<GradeEntryStudentView> rows) {
        if (definition.isEmpty()) return List.of("Aucune évaluation n'est configurée pour cette période");
        List<String> result = new ArrayList<>();
        for (GradeEntryStudentView row : rows) for (int i = 0; i < definition.size(); i++) {
            GradeEntryAssessmentView a = definition.get(i); GradeEntryCellView c = row.values().get(i);
            if (a.mandatory() && !"SCORED".equals(c.valueStatus()) && !"ABSENT".equals(c.valueStatus()) && !"EXEMPT".equals(c.valueStatus())) {
                result.add(row.studentName() + " · " + a.label());
            }
        }
        return result.size() > 12 ? new ArrayList<>(result.subList(0, 12)) {{ add("… et " + (result.size() - 12) + " autre(s)"); }} : result;
    }

    private boolean rowComplete(List<GradeEntryAssessmentView> definition, GradeEntryStudentView row) {
        for (int i = 0; i < definition.size(); i++) if (definition.get(i).mandatory() && Set.of("MISSING", "").contains(row.values().get(i).valueStatus())) return false;
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
