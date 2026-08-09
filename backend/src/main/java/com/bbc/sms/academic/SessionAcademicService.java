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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
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

    public SessionAcademicService(AcademicReportingPeriodRepository periods, AcademicAssessmentRepository assessments,
                                  AcademicGradeRepository grades, SubjectResultCommentRepository comments,
                                  StudentEnrollmentRepository enrollments, StudentRepository students,
                                  AcademicWindowPolicyService windows, TeacherScopeService teacherScope,
                                  BulletinVersionRepository bulletinVersions,
                                  SchoolClassRepository classes, SubjectRepository subjects) {
        this.periods = periods; this.assessments = assessments; this.grades = grades; this.comments = comments;
        this.enrollments = enrollments; this.students = students; this.windows = windows; this.teacherScope = teacherScope; this.bulletinVersions = bulletinVersions;
        this.classes = classes; this.subjects = subjects;
    }

    @Transactional(readOnly = true)
    public List<AssessmentView> assessments(UUID periodId, UUID classId, String subjectCode) {
        period(periodId);
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
        windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.GRADE_ENTRY);
        if (in.classId() != null) {
            classes.findByIdAndSchoolId(in.classId(), TenantContext.get())
                    .orElseThrow(() -> ApiException.notFound("Classe d'évaluation"));
        }
        String subjectCode = in.subjectCode() == null || in.subjectCode().isBlank()
                ? null : in.subjectCode().trim().toUpperCase(Locale.ROOT);
        if (subjectCode != null) {
            subjects.findBySchoolIdAndCode(TenantContext.get(), subjectCode)
                    .orElseThrow(() -> ApiException.notFound("Matière d'évaluation"));
        }
        String code = in.code().trim().toUpperCase(Locale.ROOT);
        if (assessments.existsScoped(TenantContext.get(), period.getId(), in.classId(), subjectCode, code))
            throw ApiException.conflict("Ce code d'évaluation existe déjà dans cette portée");
        AcademicAssessment a = new AcademicAssessment();
        a.setSchoolId(TenantContext.get()); a.setAcademicSessionId(period.getAcademicSessionId()); a.setReportingPeriodId(period.getId());
        a.setClassId(in.classId()); a.setSubjectCode(subjectCode);
        a.setCode(code); a.setLabel(in.label().trim()); a.setAssessmentType(in.assessmentType() == null ? "EVALUATION" : in.assessmentType().trim().toUpperCase(Locale.ROOT));
        a.setMaxScore(in.maxScore()); a.setWeight(in.weight()); a.setMandatory(in.mandatory()); a.setDisplayOrder(in.displayOrder());
        return assessmentView(assessments.save(a));
    }

    @Transactional(readOnly = true)
    public List<AcademicGradeView> grades(UUID studentId, UUID periodId) {
        teacherScope.assertStudent(studentId); period(periodId); assertStudent(studentId);
        return grades.findBySchoolIdAndStudentIdAndReportingPeriodIdOrderBySubjectCodeAscAssessmentIdAsc(TenantContext.get(), studentId, periodId)
                .stream().map(this::gradeView).toList();
    }

    @Transactional
    public AcademicGradeView upsertGrade(AcademicGradeUpsert in) {
        teacherScope.assertStudent(in.studentId());
        AcademicReportingPeriod period = period(in.reportingPeriodId());
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
