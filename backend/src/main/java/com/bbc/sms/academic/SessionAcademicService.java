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
    private final AcademicAccessPolicyService accessPolicy;
    private final BulletinVersionRepository bulletinVersions;
    private final SchoolClassRepository classes;
    private final SubjectRepository subjects;
    private final CurriculumQueryService curriculum;
    private final JdbcTemplate jdbc;

    public SessionAcademicService(AcademicReportingPeriodRepository periods, AcademicAssessmentRepository assessments,
                                  AcademicGradeRepository grades, SubjectResultCommentRepository comments,
                                  StudentEnrollmentRepository enrollments, StudentRepository students,
                                  AcademicWindowPolicyService windows, AcademicAccessPolicyService accessPolicy,
                                  BulletinVersionRepository bulletinVersions,
                                  SchoolClassRepository classes, SubjectRepository subjects,
                                  CurriculumQueryService curriculum, JdbcTemplate jdbc) {
        this.periods = periods; this.assessments = assessments; this.grades = grades; this.comments = comments;
        this.enrollments = enrollments; this.students = students; this.windows = windows; this.accessPolicy = accessPolicy; this.bulletinVersions = bulletinVersions;
        this.classes = classes; this.subjects = subjects; this.curriculum = curriculum; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AssessmentView> assessments(UUID periodId, UUID classId, String subjectCode) {
        AcademicReportingPeriod reportingPeriod = period(periodId);
        if (!AcademicPeriodRules.isSequence(reportingPeriod)) return List.of();
        if (classId == null && accessPolicy.restrictedTeacher()) {
            throw ApiException.coded(org.springframework.http.HttpStatus.FORBIDDEN,
                    "ACADEMIC_CLASS_ACCESS_DENIED", "La classe est obligatoire pour votre périmètre académique.");
        }
        String normalizedSubject = subjectCode == null || subjectCode.isBlank()
                ? null : subjectCode.trim().toUpperCase(Locale.ROOT);
        if (classId != null && normalizedSubject != null) {
            accessPolicy.require(AcademicAccessPolicyService.Capability.ASSESSMENT_VIEW,
                    reportingPeriod.getAcademicSessionId(), classId, normalizedSubject, null,
                    reportingPeriod.getStartDate());
        }
        List<AcademicAssessment> rows = classId == null
                ? assessments.findBySchoolIdAndReportingPeriodIdOrderByDisplayOrder(TenantContext.get(), periodId)
                : (normalizedSubject == null
                    ? assessments.findApplicableForClass(TenantContext.get(), periodId, classId)
                    : assessments.findApplicable(TenantContext.get(), periodId, classId, normalizedSubject));
        return rows
                .stream()
                .filter(row -> classId == null || normalizedSubject != null
                        || accessPolicy.can(AcademicAccessPolicyService.Capability.ASSESSMENT_VIEW,
                        reportingPeriod.getAcademicSessionId(), classId, row.getSubjectCode(), null,
                        reportingPeriod.getStartDate()))
                .map(this::assessmentView).toList();
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
        if (a.getClassId() != null && a.getSubjectCode() != null) {
            accessPolicy.require(AcademicAccessPolicyService.Capability.ASSESSMENT_MANAGE,
                    period.getAcademicSessionId(), a.getClassId(), a.getSubjectCode(), null,
                    period.getStartDate());
        }
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
        if (a.getClassId() != null && a.getSubjectCode() != null) {
            accessPolicy.require(AcademicAccessPolicyService.Capability.ASSESSMENT_MANAGE,
                    period.getAcademicSessionId(), a.getClassId(), a.getSubjectCode(), null,
                    period.getStartDate());
        }
        assessments.delete(a);
    }

    @Transactional(readOnly = true)
    public List<AcademicGradeView> grades(UUID studentId, UUID periodId) {
        AcademicReportingPeriod period = period(periodId);
        AcademicPeriodRules.assertRawGradePeriod(period); assertStudent(studentId);
        StudentEnrollment enrollment = resolveEnrollment(studentId, period.getAcademicSessionId(), null,
                period.getStartDate());
        accessPolicy.require(AcademicAccessPolicyService.Capability.CLASS_RESULTS_VIEW,
                period.getAcademicSessionId(), enrollment.getSchoolClassId(), null, studentId,
                period.getStartDate());
        return grades.findBySchoolIdAndStudentIdAndReportingPeriodIdOrderBySubjectCodeAscAssessmentIdAsc(TenantContext.get(), studentId, periodId)
                .stream().map(this::gradeView).toList();
    }

    @Transactional
    public AcademicGradeView upsertGrade(AcademicGradeUpsert in) {
        AcademicReportingPeriod period = period(in.reportingPeriodId());
        AcademicPeriodRules.assertRawGradePeriod(period);
        windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.GRADE_ENTRY);
        invalidatePublished(in.studentId(), period.getId());
        AcademicAssessment assessment = assessments.findById(in.assessmentId())
                .filter(a -> a.getSchoolId().equals(TenantContext.get()) && a.getReportingPeriodId().equals(period.getId()))
                .orElseThrow(() -> ApiException.badRequest("L'évaluation n'appartient pas à cette période"));
        assertStudent(in.studentId());
        StudentEnrollment enrollment = resolveEnrollment(in.studentId(), period.getAcademicSessionId(), in.enrollmentId(),
                period.getStartDate());
        String subjectCode = in.subjectCode().trim().toUpperCase(Locale.ROOT);
        UUID scopeClassId = assessment.getClassId() == null ? enrollment.getSchoolClassId() : assessment.getClassId();
        String scopedSubjectCode = assessment.getSubjectCode() == null ? subjectCode : assessment.getSubjectCode();
        accessPolicy.require(AcademicAccessPolicyService.Capability.SUBJECT_GRADE_EDIT,
                period.getAcademicSessionId(), scopeClassId, scopedSubjectCode, in.studentId(), period.getStartDate());
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
        AcademicReportingPeriod period = period(in.reportingPeriodId());
        AcademicPeriodRules.assertRawGradePeriod(period);
        windows.assertOpen(period.getId(), AcademicWindowPolicyService.Action.GRADE_ENTRY);
        invalidatePublished(in.studentId(), period.getId());
        assertStudent(in.studentId());
        StudentEnrollment enrollment = resolveEnrollment(in.studentId(), period.getAcademicSessionId(), in.enrollmentId(),
                period.getStartDate());
        String subjectCode = in.subjectCode().trim().toUpperCase(Locale.ROOT);
        accessPolicy.require(AcademicAccessPolicyService.Capability.SUBJECT_GRADE_EDIT,
                period.getAcademicSessionId(), enrollment.getSchoolClassId(), subjectCode, in.studentId(),
                period.getStartDate());
        if (in.comment() != null && in.comment().length() > 500) throw ApiException.badRequest("La remarque ne peut pas dépasser 500 caractères");
        SubjectResultComment c = comments.findBySchoolIdAndStudentIdAndReportingPeriodIdAndProgrammeClassIdAndSubjectCode(
                TenantContext.get(), in.studentId(), period.getId(), enrollment.getSchoolClassId(), subjectCode).orElseGet(SubjectResultComment::new);
        if (in.version() != null && c.getId() != null && in.version() != c.getVersion()) throw ApiException.conflict("Cette remarque a été modifiée par un autre utilisateur");
        c.setSchoolId(TenantContext.get()); c.setAcademicSessionId(period.getAcademicSessionId()); c.setReportingPeriodId(period.getId()); c.setStudentId(in.studentId()); c.setEnrollmentId(enrollment.getId()); c.setProgrammeClassId(enrollment.getSchoolClassId()); c.setSubjectCode(subjectCode); c.setComment(in.comment() == null ? null : in.comment().trim()); c.setAppreciationCode(in.appreciationCode()); c.setWorkflowStatus("DRAFT");
        return commentView(comments.save(c));
    }

    private AcademicReportingPeriod period(UUID id) { return periods.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Période de résultat")); }
    private void assertAssessmentScope(AcademicReportingPeriod period, UUID classId, String rawSubjectCode) {
        if (classId == null) throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST,
                "CLASS_REQUIRED", "Une évaluation doit être rattachée à une classe.", "classId", "Sélectionnez une classe.");
        String subjectCode = rawSubjectCode == null ? "" : rawSubjectCode.trim().toUpperCase(Locale.ROOT);
        accessPolicy.require(AcademicAccessPolicyService.Capability.ASSESSMENT_MANAGE,
                period.getAcademicSessionId(), classId, subjectCode, null, period.getStartDate());
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
    private StudentEnrollment resolveEnrollment(UUID studentId, UUID sessionId, UUID enrollmentId,
                                                java.time.LocalDate effectiveDate) {
        if (enrollmentId != null) return enrollments.findByIdAndSchoolId(enrollmentId, TenantContext.get())
                .filter(e -> e.getStudentId().equals(studentId) && e.getAcademicSessionId().equals(sessionId)
                        && !e.getEnrolledOn().isAfter(effectiveDate)
                        && (e.getExitedOn() == null || !e.getExitedOn().isBefore(effectiveDate)))
                .orElseThrow(() -> ApiException.badRequest("L'inscription ne correspond pas à l'élève, à la session ou à la date."));
        return enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                        TenantContext.get(), studentId, sessionId, "ACTIVE")
                .filter(e -> !e.getEnrolledOn().isAfter(effectiveDate)
                        && (e.getExitedOn() == null || !e.getExitedOn().isBefore(effectiveDate)))
                .orElseThrow(() -> ApiException.conflict("Aucune inscription active pour cet élève à cette date."));
    }

    private StudentEnrollment resolveEnrollment(UUID studentId, UUID sessionId, UUID enrollmentId) {
        if (enrollmentId != null) return enrollments.findByIdAndSchoolId(enrollmentId, TenantContext.get()).filter(e -> e.getStudentId().equals(studentId) && e.getAcademicSessionId().equals(sessionId)).orElseThrow(() -> ApiException.badRequest("L'inscription ne correspond pas à l'élève et à la session"));
        return enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(TenantContext.get(), studentId, sessionId, "ACTIVE").orElseThrow(() -> ApiException.conflict("Aucune inscription active pour cet élève dans cette session"));
    }
    private AssessmentView assessmentView(AcademicAssessment a) { return new AssessmentView(a.getId(), a.getAcademicSessionId(), a.getReportingPeriodId(), a.getCode(), a.getLabel(), a.getAssessmentType(), a.getMaxScore(), a.getWeight(), a.isMandatory(), a.getDisplayOrder(), a.getVersion(), a.getClassId(), a.getSubjectCode()); }
    private AcademicGradeView gradeView(AcademicGrade g) { return new AcademicGradeView(g.getId(), g.getAcademicSessionId(), g.getReportingPeriodId(), g.getAssessmentId(), g.getStudentId(), g.getEnrollmentId(), g.getSubjectCode(), g.getMark(), g.getValueStatus(), g.getWorkflowStatus(), g.getVersion()); }
    private SubjectResultCommentView commentView(SubjectResultComment c) { return new SubjectResultCommentView(c.getId(), c.getAcademicSessionId(), c.getReportingPeriodId(), c.getStudentId(), c.getEnrollmentId(), c.getSubjectCode(), c.getComment(), c.getAppreciationCode(), c.getWorkflowStatus(), c.getVersion()); }
    private UUID currentUserId() { var auth = SecurityContextHolder.getContext().getAuthentication(); return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null; }
}
