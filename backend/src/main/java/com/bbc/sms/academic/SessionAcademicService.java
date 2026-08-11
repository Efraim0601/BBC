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
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class SessionAcademicService {
    private final AcademicReportingPeriodRepository periods;
    private final AcademicAssessmentRepository assessments;
    private final AcademicGradeRepository grades;
    private final SubjectResultCommentRepository comments;
    private final StudentEnrollmentRepository enrollments;
    private final StudentRepository students;
    private final AcademicWindowPolicyService windows;
    private final TeacherScopeService teacherScope;
    private final BulletinVersionRepository bulletinVersions;
    private final SchoolClassRepository classes;
    private final SubjectRepository subjects;
    private final CurriculumQueryService curriculum;
    private final JdbcTemplate jdbc;

    public SessionAcademicService(AcademicReportingPeriodRepository periods, AcademicAssessmentRepository assessments,
                                  AcademicGradeRepository grades, SubjectResultCommentRepository comments,
                                  StudentEnrollmentRepository enrollments, StudentRepository students,
                                  AcademicWindowPolicyService windows, TeacherScopeService teacherScope,
                                  BulletinVersionRepository bulletinVersions,
                                  SchoolClassRepository classes, SubjectRepository subjects,
                                  CurriculumQueryService curriculum, JdbcTemplate jdbc) {
        this.periods = periods; this.assessments = assessments; this.grades = grades; this.comments = comments;
        this.enrollments = enrollments; this.students = students; this.windows = windows; this.teacherScope = teacherScope; this.bulletinVersions = bulletinVersions;
        this.classes = classes; this.subjects = subjects; this.curriculum = curriculum; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AssessmentView> assessments(UUID periodId, UUID classId, String subjectCode) {
        AcademicReportingPeriod reportingPeriod = period(periodId);
        if (!AcademicPeriodRules.isSequence(reportingPeriod)) return List.of();
        List<AcademicAssessment> rows = classId == null
                ? assessments.findBySchoolIdAndReportingPeriodIdOrderByDisplayOrder(TenantContext.get(), periodId)
                : (subjectCode == null || subjectCode.isBlank()
                    ? assessments.findApplicableForClass(TenantContext.get(), periodId, classId)
                    : assessments.findApplicable(TenantContext.get(), periodId, classId, subjectCode.trim()));
        return rows
                .stream().map(this::assessmentView).toList();
    }

    @Transactional
    public AssessmentView createAssessment(AssessmentUpsert in) {
        AcademicReportingPeriod period = period(in.reportingPeriodId());
        AcademicPeriodRules.assertRawGradePeriod(period);
        windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.GRADE_ENTRY);
        assertAssessmentScope(period, in.classId(), in.subjectCode());
        if (in.classId() == null) throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST,
                "CLASS_REQUIRED", "Une évaluation doit être rattachée à une classe.", "classId", "Sélectionnez une classe.");
        String subjectCode = in.subjectCode() == null || in.subjectCode().isBlank()
                ? null : in.subjectCode().trim().toUpperCase(Locale.ROOT);
        String code = in.code().trim().toUpperCase(Locale.ROOT);
        if (assessments.existsScoped(TenantContext.get(), period.getId(), in.classId(), subjectCode, code))
            throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT,
                    "ASSESSMENT_CODE_ALREADY_EXISTS",
                    "Ce code d'évaluation existe déjà pour cette matière et cette séquence.");
        AcademicAssessment a = new AcademicAssessment();
        a.setSchoolId(TenantContext.get()); a.setAcademicSessionId(period.getAcademicSessionId()); a.setReportingPeriodId(period.getId());
        a.setClassId(in.classId()); a.setSubjectCode(subjectCode);
        CanonicalSubject canonical = canonicalSubject(period.getAcademicSessionId(), in.classId(), subjectCode);
        a.setCurriculumVersionId(canonical.versionId()); a.setCurriculumSubjectId(canonical.subjectId());
        a.setCode(code); a.setLabel(in.label().trim()); a.setAssessmentType(in.assessmentType() == null ? "EVALUATION" : in.assessmentType().trim().toUpperCase(Locale.ROOT));
        a.setMaxScore(in.maxScore()); a.setWeight(in.weight()); a.setMandatory(in.mandatory()); a.setDisplayOrder(in.displayOrder());
        a.setAssessmentType("SEQUENCE_EVALUATION"); a.setSource("MANUAL");
        return assessmentView(assessments.save(a));
    }

    @Transactional
    public AssessmentView updateAssessment(UUID id, AssessmentUpsert in) {
        AcademicAssessment a = assessments.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Évaluation"));
        AcademicReportingPeriod period = period(in.reportingPeriodId());
        AcademicPeriodRules.assertRawGradePeriod(period);
        if (!a.getReportingPeriodId().equals(period.getId())) {
            throw ApiException.badRequest("L'évaluation n'appartient pas à cette séquence.");
        }
        if (in.version() != null && in.version() != a.getVersion()) {
            throw ApiException.conflict("Cette évaluation a été modifiée par un autre utilisateur. Rechargez-la avant de continuer.");
        }
        Integer gradeCount = jdbcCount("SELECT count(*) FROM academic_grade WHERE school_id=? AND assessment_id=?", id);
        if (gradeCount > 0) throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT,
                "ASSESSMENT_HAS_GRADES", "Cette évaluation possède déjà des notes et ne peut plus être modifiée.");
        assertAssessmentScope(period, in.classId(), in.subjectCode());
        String subjectCode = in.subjectCode().trim().toUpperCase(Locale.ROOT);
        String code = in.code().trim().toUpperCase(Locale.ROOT);
        if (assessments.existsScoped(TenantContext.get(), period.getId(), in.classId(), subjectCode, code)
                && !(a.getCode().equalsIgnoreCase(code) && Objects.equals(a.getClassId(), in.classId())
                && a.getSubjectCode().equalsIgnoreCase(subjectCode))) {
            throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT,
                    "ASSESSMENT_CODE_ALREADY_EXISTS",
                    "Ce code d'évaluation existe déjà pour cette matière et cette séquence.");
        }
        a.setClassId(in.classId()); a.setSubjectCode(subjectCode); a.setCode(code); a.setLabel(in.label().trim());
        a.setMaxScore(in.maxScore()); a.setWeight(in.weight()); a.setMandatory(in.mandatory());
        a.setDisplayOrder(in.displayOrder());
        return assessmentView(assessments.saveAndFlush(a));
    }

    @Transactional
    public void deleteAssessment(UUID id, Long version) {
        AcademicAssessment a = assessments.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Évaluation"));
        AcademicReportingPeriod period = period(a.getReportingPeriodId());
        AcademicPeriodRules.assertRawGradePeriod(period);
        if (version != null && version != a.getVersion()) {
            throw ApiException.conflict("Cette évaluation a été modifiée par un autre utilisateur. Rechargez-la avant de continuer.");
        }
        Integer gradeCount = jdbcCount("SELECT count(*) FROM academic_grade WHERE school_id=? AND assessment_id=?", id);
        if (gradeCount > 0) throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT,
                "ASSESSMENT_HAS_GRADES", "Cette évaluation possède déjà des notes et ne peut pas être supprimée.");
        assessments.delete(a);
    }

    @Transactional(readOnly = true)
    public List<AcademicGradeView> grades(UUID studentId, UUID periodId) {
        teacherScope.assertStudent(studentId); AcademicReportingPeriod period = period(periodId);
        AcademicPeriodRules.assertRawGradePeriod(period); assertStudent(studentId);
        return grades.findBySchoolIdAndStudentIdAndReportingPeriodIdOrderBySubjectCodeAscAssessmentIdAsc(TenantContext.get(), studentId, periodId)
                .stream().map(this::gradeView).toList();
    }

    @Transactional
    public AcademicGradeView upsertGrade(AcademicGradeUpsert in) {
        teacherScope.assertStudent(in.studentId());
        AcademicReportingPeriod period = period(in.reportingPeriodId());
        AcademicPeriodRules.assertRawGradePeriod(period);
        windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.GRADE_ENTRY);
        invalidatePublished(in.studentId(), period.getId());
        AcademicAssessment assessment = assessments.findById(in.assessmentId())
                .filter(a -> a.getSchoolId().equals(TenantContext.get()) && a.getReportingPeriodId().equals(period.getId()))
                .orElseThrow(() -> ApiException.badRequest("L'évaluation n'appartient pas à cette période"));
        assertStudent(in.studentId());
        StudentEnrollment enrollment = resolveEnrollment(in.studentId(), period.getAcademicSessionId(), in.enrollmentId());
        String subjectCode = in.subjectCode().trim().toUpperCase(Locale.ROOT);
        if (assessment.getClassId() != null && !assessment.getClassId().equals(enrollment.getSchoolClassId())) {
            throw ApiException.badRequest("L'évaluation est limitée à une autre classe");
        }
        if (assessment.getSubjectCode() != null && !assessment.getSubjectCode().equalsIgnoreCase(subjectCode)) {
            throw ApiException.badRequest("L'évaluation est limitée à une autre matière");
        }
        String status = in.valueStatus() == null || in.valueStatus().isBlank() ? "SCORED" : in.valueStatus().trim().toUpperCase(Locale.ROOT);
        if (!List.of("SCORED", "MISSING", "ABSENT", "EXEMPT").contains(status)) throw ApiException.badRequest("Statut de note invalide");
        if ("SCORED".equals(status)) {
            if (in.mark() == null) throw ApiException.badRequest("Une note est obligatoire pour le statut SCORED");
            if (in.mark().compareTo(BigDecimal.ZERO) < 0 || in.mark().compareTo(assessment.getMaxScore()) > 0)
                throw ApiException.badRequest("La note doit être comprise entre 0 et " + assessment.getMaxScore());
        }
        AcademicGrade grade = grades.findBySchoolIdAndStudentIdAndAssessmentIdAndSubjectCode(
                TenantContext.get(), in.studentId(), assessment.getId(), subjectCode).orElseGet(AcademicGrade::new);
        if (in.version() != null && grade.getId() != null && in.version() != grade.getVersion()) throw ApiException.conflict("Cette note a été modifiée par un autre utilisateur");
        grade.setSchoolId(TenantContext.get()); grade.setAcademicSessionId(period.getAcademicSessionId()); grade.setReportingPeriodId(period.getId()); grade.setAssessmentId(assessment.getId());
        grade.setStudentId(in.studentId()); grade.setEnrollmentId(enrollment.getId()); grade.setSubjectCode(subjectCode);
        grade.setMark("SCORED".equals(status) ? in.mark() : null); grade.setValueStatus(status); grade.setWorkflowStatus("DRAFT"); grade.setEnteredBy(currentUserId());
        return gradeView(grades.save(grade));
    }

    @Transactional
    public SubjectResultCommentView upsertComment(SubjectResultCommentUpsert in) {
        teacherScope.assertStudent(in.studentId());
        AcademicReportingPeriod period = period(in.reportingPeriodId());
        AcademicPeriodRules.assertRawGradePeriod(period);
        windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.GRADE_ENTRY);
        invalidatePublished(in.studentId(), period.getId());
        assertStudent(in.studentId());
        StudentEnrollment enrollment = resolveEnrollment(in.studentId(), period.getAcademicSessionId(), in.enrollmentId());
        if (in.comment() != null && in.comment().length() > 500) throw ApiException.badRequest("La remarque ne peut pas dépasser 500 caractères");
        SubjectResultComment c = comments.findBySchoolIdAndStudentIdAndReportingPeriodIdAndSubjectCode(TenantContext.get(), in.studentId(), period.getId(), in.subjectCode().trim().toUpperCase(Locale.ROOT)).orElseGet(SubjectResultComment::new);
        if (in.version() != null && c.getId() != null && in.version() != c.getVersion()) throw ApiException.conflict("Cette remarque a été modifiée par un autre utilisateur");
        c.setSchoolId(TenantContext.get()); c.setAcademicSessionId(period.getAcademicSessionId()); c.setReportingPeriodId(period.getId()); c.setStudentId(in.studentId()); c.setEnrollmentId(enrollment.getId()); c.setSubjectCode(in.subjectCode().trim().toUpperCase(Locale.ROOT)); c.setComment(in.comment() == null ? null : in.comment().trim()); c.setAppreciationCode(in.appreciationCode()); c.setWorkflowStatus("DRAFT");
        return commentView(comments.save(c));
    }

    private AcademicReportingPeriod period(UUID id) { return periods.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Période de résultat")); }
    private CanonicalSubject canonicalSubject(UUID sessionId, UUID classId, String subjectCode) {
        return jdbc.query("""
            SELECT c.id,c.curriculum_version_id FROM academic_curriculum_subject c
              JOIN subject s ON s.id=c.subject_id
              JOIN academic_curriculum_version v ON v.id=c.curriculum_version_id AND v.state='PUBLISHED'
             WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=? AND upper(s.code)=upper(?)
             ORDER BY v.version_number DESC LIMIT 1
            """, rs -> rs.next() ? new CanonicalSubject(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)) : null,
                TenantContext.get(), sessionId, classId, subjectCode);
    }

    private record CanonicalSubject(UUID subjectId, UUID versionId) {}

    private void assertAssessmentScope(AcademicReportingPeriod period, UUID classId, String rawSubjectCode) {
        if (classId == null) throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST,
                "CLASS_REQUIRED", "Une évaluation doit être rattachée à une classe.", "classId", "Sélectionnez une classe.");
        teacherScope.assertClass(classId);
        String subjectCode = rawSubjectCode == null ? "" : rawSubjectCode.trim().toUpperCase(Locale.ROOT);
        if (subjectCode.isBlank()) throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST,
                "SUBJECT_REQUIRED", "Une évaluation doit être rattachée à une matière affectée à la classe.",
                "subjectCode", "Sélectionnez une matière affectée à la classe.");
        CurriculumQueryService.Scope scope = curriculum.scope(period.getAcademicSessionId(), classId);
        boolean assigned = curriculum.applicable(scope, period).stream()
                .anyMatch(row -> row.subjectCode().equalsIgnoreCase(subjectCode));
        if (!assigned) throw ApiException.coded(org.springframework.http.HttpStatus.BAD_REQUEST,
                "SUBJECT_NOT_ASSIGNED_TO_CLASS",
                "Cette matière n'est pas affectée à la classe pour cette séquence.");
    }
    private int jdbcCount(String sql, UUID id) {
        Integer count = jdbc.queryForObject(sql, Integer.class, TenantContext.get(), id);
        return count == null ? 0 : count;
    }
    private void invalidatePublished(UUID studentId, UUID periodId) {
        List<BulletinVersion> published = bulletinVersions.findBySchoolIdAndStudentIdAndReportingPeriodIdAndState(
                TenantContext.get(), studentId, periodId, "PUBLISHED");
        if (published.isEmpty()) return;
        windows.assertOpen(periodId, AcademicWindowPolicyService.Action.CORRECTION);
        published.forEach(v -> v.setState("SUPERSEDED"));
        bulletinVersions.saveAllAndFlush(published);
    }
    private void assertStudent(UUID id) { students.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Élève")); }
    private StudentEnrollment resolveEnrollment(UUID studentId, UUID sessionId, UUID enrollmentId) {
        if (enrollmentId != null) return enrollments.findByIdAndSchoolId(enrollmentId, TenantContext.get()).filter(e -> e.getStudentId().equals(studentId) && e.getAcademicSessionId().equals(sessionId)).orElseThrow(() -> ApiException.badRequest("L'inscription ne correspond pas à l'élève et à la session"));
        return enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(TenantContext.get(), studentId, sessionId, "ACTIVE").orElseThrow(() -> ApiException.conflict("Aucune inscription active pour cet élève dans cette session"));
    }
    private AssessmentView assessmentView(AcademicAssessment a) { return new AssessmentView(a.getId(), a.getAcademicSessionId(), a.getReportingPeriodId(), a.getCode(), a.getLabel(), a.getAssessmentType(), a.getMaxScore(), a.getWeight(), a.isMandatory(), a.getDisplayOrder(), a.getVersion(), a.getClassId(), a.getSubjectCode()); }
    private AcademicGradeView gradeView(AcademicGrade g) { return new AcademicGradeView(g.getId(), g.getAcademicSessionId(), g.getReportingPeriodId(), g.getAssessmentId(), g.getStudentId(), g.getEnrollmentId(), g.getSubjectCode(), g.getMark(), g.getValueStatus(), g.getWorkflowStatus(), g.getVersion()); }
    private SubjectResultCommentView commentView(SubjectResultComment c) { return new SubjectResultCommentView(c.getId(), c.getAcademicSessionId(), c.getReportingPeriodId(), c.getStudentId(), c.getEnrollmentId(), c.getSubjectCode(), c.getComment(), c.getAppreciationCode(), c.getWorkflowStatus(), c.getVersion()); }
    private UUID currentUserId() { var auth = SecurityContextHolder.getContext().getAuthentication(); return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null; }
}
