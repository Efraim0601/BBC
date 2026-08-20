package com.bbc.sms.academic.secondary;

import com.bbc.sms.academic.AcademicPeriodRules;
import com.bbc.sms.academic.security.AcademicAccessPolicyService;
import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.foundation.session.AcademicReportingPeriodRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.TeachingAssignmentResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static com.bbc.sms.academic.secondary.SecondaryCompetencyDtos.*;

/** Versioned, session-scoped competency evidence for secondary report cards. */
@Service
public class SecondaryCompetencyService {
    private final JdbcTemplate jdbc;
    private final AcademicReportingPeriodRepository periods;
    private final TeachingAssignmentResolver assignments;
    private final AcademicAccessPolicyService accessPolicy;

    public SecondaryCompetencyService(JdbcTemplate jdbc, AcademicReportingPeriodRepository periods,
                                      TeachingAssignmentResolver assignments, AcademicAccessPolicyService accessPolicy) {
        this.jdbc = jdbc; this.periods = periods; this.assignments = assignments; this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public List<ModelView> list(UUID reportingPeriodId, UUID classId, UUID subjectId, String locale) {
        AcademicReportingPeriod period = periods.findByIdAndSchoolId(reportingPeriodId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période de compétence"));
        Scope scope = scope(period.getAcademicSessionId(), reportingPeriodId, classId, subjectId);
        assertCurriculum(scope);
        accessPolicy.require(AcademicAccessPolicyService.Capability.ASSESSMENT_VIEW,
                period.getAcademicSessionId(), classId, scope.subjectCode(), null, period.getStartDate());
        String normalized = contentLanguage(classId);
        return jdbc.query("""
                SELECT id,academic_session_id,reporting_period_id,class_id,subject_id,locale,
                       name,version,status,source
                  FROM secondary_competency_model
                 WHERE school_id=? AND reporting_period_id=? AND class_id=? AND subject_id=?
                   AND locale=?
                 ORDER BY version DESC
                """, (rs, n) -> model(rs.getObject("id", UUID.class),
                        rs.getObject("academic_session_id", UUID.class),
                        rs.getObject("reporting_period_id", UUID.class),
                        rs.getObject("class_id", UUID.class), rs.getObject("subject_id", UUID.class),
                        rs.getString("locale"), rs.getString("name"), rs.getInt("version"),
                        rs.getString("status"), rs.getString("source")),
                TenantContext.get(), reportingPeriodId, classId, subjectId, normalized);
    }

    @Transactional(readOnly = true)
    public ModelView get(UUID id) {
        ModelView found = jdbc.query("""
                SELECT id,academic_session_id,reporting_period_id,class_id,subject_id,locale,
                       name,version,status,source
                  FROM secondary_competency_model WHERE school_id=? AND id=?
                """, rs -> rs.next() ? model(rs.getObject("id", UUID.class),
                        rs.getObject("academic_session_id", UUID.class),
                        rs.getObject("reporting_period_id", UUID.class),
                        rs.getObject("class_id", UUID.class), rs.getObject("subject_id", UUID.class),
                        rs.getString("locale"), rs.getString("name"), rs.getInt("version"),
                        rs.getString("status"), rs.getString("source")) : null,
                TenantContext.get(), id);
        if (found == null) throw ApiException.notFound("Compétence secondaire");
        Scope scope = scope(found.academicSessionId(), found.reportingPeriodId(), found.classId(), found.subjectId());
        if (!accessPolicy.can(AcademicAccessPolicyService.Capability.CLASS_RESULTS_VIEW,
                found.academicSessionId(), found.classId(), null, null, scope.period().getStartDate())) {
            accessPolicy.require(AcademicAccessPolicyService.Capability.ASSESSMENT_VIEW,
                    found.academicSessionId(), found.classId(), scope.subjectCode(), null, scope.period().getStartDate());
        }
        return found;
    }

    @Transactional
    public ModelView create(ModelRequest request) {
        UUID school = TenantContext.get();
        Scope scope = scope(request.academicSessionId(), request.reportingPeriodId(), request.classId(), request.subjectId());
        AcademicPeriodRules.assertRawGradePeriod(scope.period());
        assertCurriculum(scope);
        assertClassSubjectAccess(scope);
        String locale = scope.contentLanguage();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO secondary_competency_model
                    (id,school_id,academic_session_id,reporting_period_id,class_id,subject_id,locale,name,version,status,source)
                VALUES (?,?,?,?,?,?,?, ?,1,'DRAFT','MANUAL')
                """, id, school, request.academicSessionId(), request.reportingPeriodId(),
                request.classId(), request.subjectId(), locale, request.name().trim());
        insertCompetencies(school, id, request.competencies());
        return get(id);
    }

    @Transactional
    public ModelView copy(UUID modelId, String reason) {
        ModelView previous = get(modelId);
        Scope previousScope = scope(previous.academicSessionId(), previous.reportingPeriodId(), previous.classId(), previous.subjectId());
        AcademicPeriodRules.assertRawGradePeriod(previousScope.period());
        assertCurriculum(previousScope);
        assertClassSubjectAccess(previousScope);
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("Le motif de nouvelle version est obligatoire");
        UUID school = TenantContext.get();
        String locale = contentLanguage(previous.classId());
        int next = jdbc.queryForObject("SELECT coalesce(max(version),0)+1 FROM secondary_competency_model WHERE school_id=? AND academic_session_id=? AND reporting_period_id=? AND class_id=? AND subject_id=? AND locale=?",
                Integer.class, school, previous.academicSessionId(), previous.reportingPeriodId(), previous.classId(), previous.subjectId(), locale);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO secondary_competency_model (id,school_id,academic_session_id,reporting_period_id,class_id,subject_id,locale,name,version,status,source) VALUES (?,?,?,?,?,?,?, ?,?,'DRAFT','MANUAL')",
                id, school, previous.academicSessionId(), previous.reportingPeriodId(), previous.classId(), previous.subjectId(), locale, previous.name(), next);
        insertCompetencies(school, id, previous.competencies().stream().map(x -> new CompetencyInput(x.code(), x.description(), x.maxScore(), x.displayOrder())).toList());
        return get(id);
    }

    @Transactional
    public ModelView publish(UUID id) {
        ModelView model = get(id);
        Scope scope = scope(model.academicSessionId(), model.reportingPeriodId(), model.classId(), model.subjectId());
        AcademicPeriodRules.assertRawGradePeriod(scope.period());
        assertCurriculum(scope);
        assertClassSubjectAccess(scope);
        String locale = contentLanguage(model.classId());
        jdbc.update("UPDATE secondary_competency_model SET status='RETIRED' WHERE school_id=? AND academic_session_id=? AND reporting_period_id=? AND class_id=? AND subject_id=? AND locale=? AND status='PUBLISHED'",
                TenantContext.get(), model.academicSessionId(), model.reportingPeriodId(), model.classId(), model.subjectId(), locale);
        jdbc.update("UPDATE secondary_competency_model SET status='PUBLISHED',published_at=now() WHERE school_id=? AND id=?", TenantContext.get(), id);
        return get(id);
    }

    @Transactional
    public MarkView saveMark(MarkRequest request) {
        UUID school = TenantContext.get();
        ModelView model = get(request.modelId());
        Scope scope = scope(model.academicSessionId(), request.reportingPeriodId(), model.classId(), model.subjectId());
        AcademicPeriodRules.assertRawGradePeriod(scope.period());
        if (!model.reportingPeriodId().equals(request.reportingPeriodId())) {
            throw ApiException.badRequest("Le modèle de compétence n'appartient pas à cette séquence.");
        }
        assertCurriculum(scope);
        accessPolicy.require(AcademicAccessPolicyService.Capability.SUBJECT_GRADE_EDIT,
                scope.period().getAcademicSessionId(), scope.classId(), scope.subjectCode(),
                request.studentId(), scope.period().getStartDate());
        UUID teacherId = resolvedTeacher(scope);
        UUID enrollmentId = resolveEnrollment(scope, request.studentId(), request.enrollmentId());
        CompetencyRow competency = jdbc.query("SELECT max_score FROM secondary_competency WHERE school_id=? AND id=? AND model_id=?",
                rs -> rs.next() ? new CompetencyRow(rs.getBigDecimal(1)) : null, school, request.competencyId(), request.modelId());
        if (competency == null) throw ApiException.notFound("Compétence secondaire");
        String status = normalizeStatus(request.valueStatus(), request.mark());
        if (request.mark() != null && (request.mark().compareTo(BigDecimal.ZERO) < 0 || request.mark().compareTo(competency.maxScore()) > 0))
            throw ApiException.badRequest("La note dépasse le barème de la compétence");
        MarkView current = findMark(request.modelId(), request.competencyId(), request.reportingPeriodId(), request.studentId());
        if (current != null && request.version() != null && request.version() != current.version()) throw ApiException.conflict("La note a changé entre-temps");
        UUID assessmentId = ensureCanonicalAssessment(model, request.competencyId(), competency.maxScore(), scope.subjectCode());
        UUID canonicalGradeId = jdbc.query("""
                SELECT id FROM academic_grade
                 WHERE school_id=? AND student_id=? AND assessment_id=? AND upper(subject_code)=upper(?)
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                school, request.studentId(), assessmentId, scope.subjectCode());
        if (canonicalGradeId == null) {
            jdbc.update("""
                    INSERT INTO academic_grade
                        (school_id,academic_session_id,reporting_period_id,assessment_id,
                         student_id,enrollment_id,subject_code,teacher_id,mark,value_status,workflow_status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,'DRAFT')
                    """, school, scope.period().getAcademicSessionId(), request.reportingPeriodId(), assessmentId,
                    request.studentId(), enrollmentId, scope.subjectCode(), teacherId,
                    "SCORED".equals(status) ? request.mark() : null, status);
        } else {
            jdbc.update("""
                    UPDATE academic_grade SET mark=?,value_status=?,enrollment_id=?,teacher_id=?,workflow_status='DRAFT',version=version+1,updated_at=now()
                     WHERE school_id=? AND id=?
                    """, "SCORED".equals(status) ? request.mark() : null, status,
                    enrollmentId, teacherId, school, canonicalGradeId);
        }
        // Keep the old row as a compatibility mirror; academic_grade above is authoritative.
        if (current == null) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO secondary_competency_mark (id,school_id,model_id,competency_id,reporting_period_id,student_id,enrollment_id,teacher_id,mark,value_status) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    id, school, request.modelId(), request.competencyId(), request.reportingPeriodId(), request.studentId(), enrollmentId, teacherId, request.mark(), status);
        } else {
            jdbc.update("UPDATE secondary_competency_mark SET mark=?,value_status=?,enrollment_id=?,teacher_id=?,version=version+1,updated_at=now() WHERE school_id=? AND id=?",
                    request.mark(), status, enrollmentId, teacherId, school, current.id());
        }
        return findMark(request.modelId(), request.competencyId(), request.reportingPeriodId(), request.studentId());
    }

    @Transactional
    public List<MarkView> importMarks(ImportRequest request) {
        ModelView model = get(request.modelId());
        Map<String, UUID> competencies = model.competencies().stream().collect(java.util.stream.Collectors.toMap(x -> x.code().toUpperCase(Locale.ROOT), CompetencyView::id));
        List<MarkView> result = new ArrayList<>();
        for (ImportRow row : request.rows()) {
            UUID competencyId = competencies.get(row.competencyCode().trim().toUpperCase(Locale.ROOT));
            if (competencyId == null) throw ApiException.badRequest("Compétence inconnue : " + row.competencyCode());
                result.add(saveMark(new MarkRequest(request.modelId(), competencyId, request.reportingPeriodId(), row.studentId(), row.enrollmentId(), null, row.mark(), row.valueStatus(), null)));
        }
        jdbc.update("UPDATE secondary_competency_model SET source='IMPORT' WHERE school_id=? AND id=?", TenantContext.get(), request.modelId());
        return result;
    }

    @Transactional(readOnly = true)
    public List<MarkView> marks(UUID modelId, UUID reportingPeriodId, UUID studentId) {
        ModelView model = get(modelId);
        if (!model.reportingPeriodId().equals(reportingPeriodId)) {
            throw ApiException.badRequest("Le modèle de compétence n'appartient pas à cette séquence.");
        }
        // PostgreSQL cannot infer the type of a nullable JDBC placeholder in the optional
        // student filter.  Cast the first placeholder explicitly so the all-students view
        // (the normal Settings workflow) is a valid prepared statement as well.
        return jdbc.query("SELECT id,model_id,competency_id,reporting_period_id,student_id,enrollment_id,teacher_id,mark,value_status,version FROM secondary_competency_mark WHERE school_id=? AND model_id=? AND reporting_period_id=? AND (CAST(? AS uuid) IS NULL OR student_id=?) ORDER BY student_id,competency_id",
                (rs,n) -> mark(rs), TenantContext.get(), modelId, reportingPeriodId, studentId, studentId);
    }

    private ModelView model(UUID id, UUID session, UUID period, UUID clazz, UUID subject, String locale, String name, int version, String status, String source) {
        List<CompetencyView> competencies = jdbc.query("SELECT id,model_id,code,description,max_score,display_order,active FROM secondary_competency WHERE school_id=? AND model_id=? ORDER BY display_order,code",
                (rs,n) -> new CompetencyView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getInt(6), rs.getBoolean(7)), TenantContext.get(), id);
        return new ModelView(id, session, period, clazz, subject, contentLanguage(clazz), name, version, status, source, competencies);
    }

    private void insertCompetencies(UUID school, UUID model, List<CompetencyInput> inputs) {
        Set<String> codes = new HashSet<>();
        int order = 1;
        for (CompetencyInput input : inputs) {
            String code = input.code().trim();
            if (!codes.add(code.toUpperCase(Locale.ROOT))) throw ApiException.badRequest("Codes de compétence dupliqués");
            jdbc.update("INSERT INTO secondary_competency (school_id,model_id,code,description,max_score,display_order) VALUES (?,?,?,?,?,?)",
                    school, model, code, input.description().trim(), input.maxScore(), input.displayOrder() > 0 ? input.displayOrder() : order++);
        }
        if (inputs.isEmpty()) throw ApiException.badRequest("Au moins une compétence est requise");
    }

    private MarkView findMark(UUID model, UUID competency, UUID period, UUID student) {
        return jdbc.query("SELECT id,model_id,competency_id,reporting_period_id,student_id,enrollment_id,teacher_id,mark,value_status,version FROM secondary_competency_mark WHERE school_id=? AND model_id=? AND competency_id=? AND reporting_period_id=? AND student_id=?",
                rs -> rs.next() ? mark(rs) : null, TenantContext.get(), model, competency, period, student);
    }
    private static MarkView mark(java.sql.ResultSet rs) throws java.sql.SQLException { return new MarkView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class), rs.getObject(7, UUID.class), rs.getBigDecimal(8), rs.getString(9), rs.getLong(10)); }
    private record CompetencyRow(BigDecimal maxScore) {}

    private record Scope(AcademicReportingPeriod period, UUID classId, UUID subjectId,
                         String subjectCode, String subsystem) {
        String contentLanguage() { return "EN".equalsIgnoreCase(subsystem) ? "en" : "fr"; }
    }

    private Scope scope(UUID sessionId, UUID periodId, UUID classId, UUID subjectId) {
        AcademicReportingPeriod period = periods.findByIdAndSchoolId(periodId, TenantContext.get())
                .filter(p -> p.getAcademicSessionId().equals(sessionId))
                .orElseThrow(() -> ApiException.notFound("Modèle ou séquence de compétence"));
        Scope scope = jdbc.query("""
                SELECT s.code,c.subsystem
                  FROM subject s JOIN school_class c ON c.id=? AND c.school_id=?
                 WHERE s.id=? AND s.school_id=?
                """, rs -> rs.next() ? new Scope(period, classId, subjectId, rs.getString(1), rs.getString(2)) : null,
                classId, TenantContext.get(), subjectId, TenantContext.get());
        if (scope == null) throw ApiException.notFound("Classe ou matière");
        return scope;
    }

    private void assertCurriculum(Scope scope) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM academic_curriculum_subject c
                 WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=? AND c.subject_id=?
                   AND (c.active_from IS NULL OR c.active_from<=?)
                   AND (c.active_to IS NULL OR c.active_to>=?)
                """, Integer.class, TenantContext.get(), scope.period().getAcademicSessionId(),
                scope.classId(), scope.subjectId(), scope.period().getStartDate(), scope.period().getEndDate());
        if (count == null || count == 0) throw ApiException.coded(org.springframework.http.HttpStatus.BAD_REQUEST,
                "SUBJECT_NOT_ASSIGNED_TO_CLASS", "Cette matière n'est pas affectée à la classe pour cette séquence.");
    }

    private void assertClassSubjectAccess(Scope scope) {
        accessPolicy.require(AcademicAccessPolicyService.Capability.ASSESSMENT_MANAGE,
                scope.period().getAcademicSessionId(), scope.classId(), scope.subjectCode(), null,
                scope.period().getStartDate());
    }

    private UUID resolvedTeacher(Scope scope) {
        TeachingAssignmentResolver.Resolution assignment = assignments.resolve(
                scope.period().getAcademicSessionId(), scope.classId(), scope.subjectCode(), scope.period().getStartDate());
        AcademicAccessPolicyService.AccessDecision decision = accessPolicy.require(
                AcademicAccessPolicyService.Capability.SUBJECT_GRADE_EDIT,
                scope.period().getAcademicSessionId(), scope.classId(), scope.subjectCode(), null,
                scope.period().getStartDate());
        if (decision.employeeId() != null) return decision.employeeId();
        if (assignment.available()) return assignment.teacherId();
        throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT, assignment.code(),
                assignment.messageFr());
    }

    private UUID resolveEnrollment(Scope scope, UUID studentId, UUID requestedEnrollmentId) {
        String sql = """
                SELECT id FROM student_enrollment
                 WHERE school_id=? AND academic_session_id=? AND school_class_id=?
                   AND student_id=? AND status='ACTIVE'
                   AND enrolled_on<=? AND (exited_on IS NULL OR exited_on>=?)
                   AND (CAST(? AS uuid) IS NULL OR id=?)
                 ORDER BY created_at DESC
                 LIMIT 1
                """;
        UUID enrollmentId = jdbc.query(sql, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), scope.period().getAcademicSessionId(), scope.classId(), studentId,
                scope.period().getStartDate(), scope.period().getStartDate(), requestedEnrollmentId, requestedEnrollmentId);
        if (enrollmentId == null) {
            throw ApiException.badRequest("L'inscription active de l'élève ne correspond pas à la classe et à la session.");
        }
        return enrollmentId;
    }

    private String contentLanguage(UUID classId) {
        String subsystem = jdbc.queryForObject("SELECT subsystem FROM school_class WHERE id=? AND school_id=?",
                String.class, classId, TenantContext.get());
        return "EN".equalsIgnoreCase(subsystem) ? "en" : "fr";
    }

    private void assertCurrentTeacher(UUID assignedTeacherId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof AppUserPrincipal user)
                || !Set.of("teacher", "secondary_teacher", "form_teacher")
                .contains(user.roleCode())) return;
        UUID employeeId = jdbc.query("SELECT employee_id FROM app_user WHERE id=? AND school_id=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, user.userId(), TenantContext.get());
        if (!Objects.equals(employeeId, assignedTeacherId)) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Vous n'êtes pas affecté à cette matière dans cette classe");
        }
    }

    private UUID ensureCanonicalAssessment(ModelView model, UUID competencyId,
                                           BigDecimal maxScore, String subjectCode) {
        UUID assessmentId = jdbc.query("SELECT id FROM academic_assessment WHERE school_id=? AND legacy_secondary_competency_id=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, TenantContext.get(), competencyId);
        if (assessmentId != null) return assessmentId;
        CompetencyView competency = model.competencies().stream().filter(x -> x.id().equals(competencyId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Compétence secondaire"));
        String code = competency.code().trim().toUpperCase(Locale.ROOT);
        if (code.length() > 40) code = code.substring(0, 31) + "_" + competencyId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        UUID conflicting = jdbc.query("""
                SELECT id FROM academic_assessment
                 WHERE school_id=? AND reporting_period_id=? AND class_id=?
                   AND upper(subject_code)=upper(?) AND upper(code)=upper(?)
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), model.reportingPeriodId(), model.classId(), subjectCode, code);
        if (conflicting != null) {
            code = "LEGACY_" + competencyId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        }
        assessmentId = UUID.randomUUID();
        String label = competency.description() == null ? code : competency.description().trim();
        if (label.length() > 160) label = label.substring(0, 160);
        jdbc.update("""
                INSERT INTO academic_assessment
                    (id,school_id,academic_session_id,reporting_period_id,subject_code,class_id,
                     code,label,assessment_type,max_score,weight,mandatory,display_order,source,
                     legacy_secondary_competency_id)
                VALUES (?,?,?,?,?,?,?,?, 'SEQUENCE_EVALUATION',?,1,true,?, 'LEGACY_SECONDARY',?)
                """, assessmentId, TenantContext.get(), model.academicSessionId(), model.reportingPeriodId(),
                subjectCode, model.classId(), code, label, maxScore, competency.displayOrder(), competencyId);
        return assessmentId;
    }
    private static String normalizeLocale(String raw) { String x = raw.trim().toLowerCase(Locale.ROOT); if (!x.matches("fr|en|fr-[a-z]{2}|en-[a-z]{2}")) throw ApiException.badRequest("Locale de compétence invalide"); return x; }
    private static String normalizeStatus(String value, BigDecimal mark) { String x = value == null || value.isBlank() ? (mark == null ? "MISSING" : "SCORED") : value.trim().toUpperCase(Locale.ROOT); if (!Set.of("SCORED","ABSENT","EXEMPT","MISSING").contains(x)) throw ApiException.badRequest("Statut de note invalide"); return x; }
}
