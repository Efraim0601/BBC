package com.bbc.sms.academic.security;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.foundation.cohort.AcademicCohortResolver;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.security.PermissionService;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.TeachingAssignmentResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Single resolver for academic data scope.
 *
 * <p>The module permission is deliberately not enough to enter this service.
 * Every decision is also bound to the tenant, academic session, effective
 * date, class, optional subject and optional active enrollment.</p>
 */
@Service
public class AcademicAccessPolicyService {

    public enum Capability {
        ACADEMIC_ROSTER_VIEW,
        ASSESSMENT_VIEW,
        ASSESSMENT_MANAGE,
        SUBJECT_GRADE_VIEW,
        SUBJECT_GRADE_EDIT,
        TITULAIRE_ANY_SUBJECT_GRADE_EDIT,
        SUBJECT_GRADE_SUBMIT,
        CLASS_RESULTS_VIEW,
        CLASS_REPORT_CARD_VIEW,
        GRADE_PACKET_REVIEW,
        REPORT_CARD_VALIDATE,
        REPORT_CARD_PUBLISH,
        COUNCIL_INPUT_VIEW,
        COUNCIL_INPUT_EDIT
    }

    public record AccessDecision(boolean allowed, UUID employeeId, String source,
                                 UUID assignmentId, long assignmentVersion,
                                 UUID delegationId, UUID classId, String subjectCode,
                                 String code, String messageFr, String messageEn,
                                 LocalDate effectiveDate) {
        public boolean delegated() { return "DELEGATION".equals(source); }
        public boolean assigned() { return assignmentId != null; }
    }

    public record SubjectCandidate(String code, String label, int coefficient,
                                   boolean remarkRequired) {}

    private record Actor(AppUserPrincipal principal, UUID employeeId, boolean teacherRole) {}
    private record Session(UUID id, LocalDate start, LocalDate end) {}
    private record Enrollment(UUID id, UUID classId) {}
    private record Resource(UUID classId, String level) {}
    private record Delegation(UUID id, String status) {}
    private record ResolutionView(UUID teacherId, UUID assignmentId, long assignmentVersion, String code) {}

    private final JdbcTemplate jdbc;
    private final PermissionService permissions;
    private final TeachingAssignmentResolver assignments;
    private final AuthorizationPolicyService centralPolicy;
    private final AcademicCohortResolver cohorts;

    @Autowired
    public AcademicAccessPolicyService(JdbcTemplate jdbc, PermissionService permissions,
                                       TeachingAssignmentResolver assignments,
                                       @Lazy AuthorizationPolicyService centralPolicy,
                                       AcademicCohortResolver cohorts) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.assignments = assignments;
        this.centralPolicy = centralPolicy;
        this.cohorts = cohorts;
    }

    /** Constructor retained for focused domain-unit tests without the policy schema. */
    public AcademicAccessPolicyService(JdbcTemplate jdbc, PermissionService permissions,
                                       TeachingAssignmentResolver assignments,
                                       AuthorizationPolicyService centralPolicy) {
        this(jdbc, permissions, assignments, centralPolicy, null);
    }

    /** Constructor retained for focused domain-unit tests without the policy schema. */
    public AcademicAccessPolicyService(JdbcTemplate jdbc, PermissionService permissions,
                                       TeachingAssignmentResolver assignments) {
        this(jdbc, permissions, assignments, null, null);
    }

    /** Fail closed for a direct academic resource request. */
    @Transactional(readOnly = true)
    public AccessDecision require(Capability capability, UUID academicSessionId,
                                  UUID classId, String rawSubjectCode, UUID studentId,
                                  LocalDate requestedDate) {
        AccessDecision decision = resolve(capability, academicSessionId, classId,
                rawSubjectCode, studentId, requestedDate);
        if (!decision.allowed()) {
            throw ApiException.coded(HttpStatus.FORBIDDEN, decision.code(), decision.messageFr());
        }
        return decision;
    }

    /** Domain-only require used by the central evaluator's invariant adapter. */
    public AccessDecision requireDomain(Capability capability, UUID academicSessionId,
                                        UUID classId, String rawSubjectCode, UUID studentId,
                                        LocalDate requestedDate) {
        AccessDecision decision = resolveDomain(capability, academicSessionId, classId,
                rawSubjectCode, studentId, requestedDate);
        if (!decision.allowed()) {
            throw ApiException.coded(HttpStatus.FORBIDDEN, decision.code(), decision.messageFr());
        }
        return decision;
    }

    @Transactional(readOnly = true)
    public boolean can(Capability capability, UUID academicSessionId, UUID classId,
                       String rawSubjectCode, UUID studentId, LocalDate requestedDate) {
        // Collection filters use this boolean form to test many candidate
        // resources.  A denied central V2 decision is expected for candidates
        // outside the teacher's assignment and must not abort the whole list
        // with a 403; direct resource callers continue to use require(...).
        try {
            return resolve(capability, academicSessionId, classId, rawSubjectCode,
                    studentId, requestedDate).allowed();
        } catch (ApiException denied) {
            return false;
        }
    }

    /** Domain-only check used by AuthorizationPolicyService without recursion. */
    public boolean canForCentral(Capability capability, UUID academicSessionId, UUID classId,
                                 String rawSubjectCode, UUID studentId, LocalDate requestedDate) {
        return resolveDomain(capability, academicSessionId, classId, rawSubjectCode,
                studentId, requestedDate).allowed();
    }

    /** Resolve a staff preview for an employee selected by an administrator. */
    @Transactional(readOnly = true)
    public AccessDecision employeeDecision(UUID employeeId, Capability capability,
                                          UUID academicSessionId, UUID classId,
                                          String rawSubjectCode, LocalDate requestedDate) {
        String subjectCode = normalize(rawSubjectCode);
        Session session = session(academicSessionId);
        LocalDate date = requestedDate == null && session != null ? session.start() : requestedDate;
        Resource resource = classId == null ? null : resource(classId);
        if (employeeId == null || session == null || resource == null || date == null
                || date.isBefore(session.start()) || date.isAfter(session.end())) {
            return deny(denialCode(capability), denialFr(capability), denialEn(capability), date);
        }
        if (capability == Capability.CLASS_RESULTS_VIEW || capability == Capability.CLASS_REPORT_CARD_VIEW
                || capability == Capability.COUNCIL_INPUT_VIEW || capability == Capability.COUNCIL_INPUT_EDIT) {
            TeachingAssignmentResolver.Resolution homeroom = assignments.resolveHomeroom(session.id(), classId, date);
            if (employeeId.equals(homeroom.teacherId())
                    && (capability != Capability.COUNCIL_INPUT_EDIT || councilEditDefault(resource.level()))) {
                return allow(employeeId, "secondary".equalsIgnoreCase(resource.level())
                                ? "SECONDARY_HOMEROOM_VIEW" : "PRIMARY_HOMEROOM",
                        homeroom.assignmentId(), homeroom.assignmentVersion(), null, classId,
                        subjectCode, homeroom.code(), date);
            }
            return delegationDecision(employeeId, session.id(), classId, subjectCode, capability, date);
        }
        if (subjectCode.isBlank() || !subjectInCurriculum(session.id(), classId, subjectCode, date)) {
            return deny("ACADEMIC_SUBJECT_ACCESS_DENIED", denialFr(capability), denialEn(capability), date);
        }
        TeachingAssignmentResolver.Resolution assignment = assignments.resolve(
                session.id(), classId, subjectCode, date);
        if (assignment.available() && employeeId.equals(assignment.teacherId())) {
            return allow(employeeId, "secondary".equalsIgnoreCase(resource.level())
                            ? "SECONDARY_RESPONSIBLE" : "PRIMARY_HOMEROOM", assignment.assignmentId(),
                    assignment.assignmentVersion(), null, classId, subjectCode, assignment.code(), date);
        }
        return delegationDecision(employeeId, session.id(), classId, subjectCode, capability, date);
    }

    /** Structured decision without throwing; useful for filtered collections and previews. */
    @Transactional(readOnly = true)
    public AccessDecision resolve(Capability capability, UUID academicSessionId,
                                  UUID classId, String rawSubjectCode, UUID studentId,
                                  LocalDate requestedDate) {
        boolean centralAllowed = requireCentralAction(capability, academicSessionId, classId,
                rawSubjectCode, studentId, requestedDate);
        return resolveDomain(capability, academicSessionId, classId, rawSubjectCode,
                studentId, requestedDate, centralAllowed);
    }

    /** Domain-only path used by AcademicScopeResolver from the central evaluator. */
    public AccessDecision resolveDomain(Capability capability, UUID academicSessionId,
                                        UUID classId, String rawSubjectCode, UUID studentId,
                                        LocalDate requestedDate) {
        return resolveDomain(capability, academicSessionId, classId, rawSubjectCode,
                studentId, requestedDate, false);
    }

    private AccessDecision resolveDomain(Capability capability, UUID academicSessionId,
                                         UUID classId, String rawSubjectCode, UUID studentId,
                                         LocalDate requestedDate, boolean centralAllowed) {
        String subjectCode = normalize(rawSubjectCode);
        LocalDate effectiveDate = requestedDate;
        Actor actor = actor();
        if (actor == null) return deny("ACADEMIC_CLASS_ACCESS_DENIED",
                "Accès académique refusé.", "Academic access denied.", effectiveDate);
        if (actor.teacherRole() && actor.employeeId() == null) {
            return deny("TEACHER_ACCOUNT_NOT_LINKED",
                    "Votre compte enseignant n'est pas relié à un employé actif. Contactez la direction.",
                    "Your teacher account is not linked to an active employee. Contact management.", effectiveDate);
        }
        if (academicSessionId == null || classId == null) {
            return deny("ACADEMIC_CLASS_ACCESS_DENIED",
                    "La session et la classe sont obligatoires.",
                    "The academic session and class are required.", effectiveDate);
        }

        Session session = session(academicSessionId);
        if (session == null) return deny("ACADEMIC_CLASS_ACCESS_DENIED",
                "Cette session académique n'est pas accessible.",
                "This academic session is not accessible.", effectiveDate);
        if (effectiveDate == null) effectiveDate = session.start();
        if (effectiveDate.isBefore(session.start()) || effectiveDate.isAfter(session.end())) {
            return deny("ACADEMIC_EFFECTIVE_DATE_OUT_OF_SESSION",
                    "La date d'effet ne se trouve pas dans la session académique.",
                    "The effective date is outside the academic session.", effectiveDate);
        }

        Resource resource = resource(classId);
        if (resource == null) return deny("ACADEMIC_CLASS_ACCESS_DENIED",
                "Cette classe n'est pas accessible.", "This class is not accessible.", effectiveDate);

        Enrollment enrollment = studentId == null ? null : enrollment(studentId, academicSessionId,
                effectiveDate);
        boolean enrollmentMatches = enrollment != null && classId.equals(enrollment.classId());
        if (enrollment != null && cohorts != null) {
            enrollmentMatches = cohorts.studentBelongsToClass(academicSessionId, classId, studentId,
                    "ACTIVE", effectiveDate);
        }
        if (studentId != null && !enrollmentMatches) {
            return deny("ENROLLMENT_SCOPE_MISMATCH",
                    "L'inscription de l'élève ne correspond pas à cette classe et cette session.",
                    "The student's enrollment does not match this class and session.", effectiveDate);
        }

        // Management actions are explicit and are checked after the tenant and
        // enrollment scope, so a broad action never becomes a cross-tenant leak.
        if (managementAllowed(actor, capability, centralAllowed)) {
            return allow(actor.employeeId(), "MANAGEMENT_ACTION", null, 0, null,
                    classId, subjectCode, "ACADEMIC_ACCESS_ALLOWED", effectiveDate);
        }

        if (!actor.teacherRole()) {
            return deny(denialCode(capability), denialFr(capability), denialEn(capability), effectiveDate);
        }

        if (capability == Capability.TITULAIRE_ANY_SUBJECT_GRADE_EDIT) {
            if (subjectCode.isBlank() || !subjectInCurriculum(academicSessionId, classId,
                    subjectCode, effectiveDate)) {
                return deny("ACADEMIC_SUBJECT_ACCESS_DENIED",
                        "Cette matière ne fait pas partie du curriculum actif de votre classe titulaire.",
                        "This subject is not in the active curriculum of your homeroom class.", effectiveDate);
            }
            TeachingAssignmentResolver.Resolution homeroom = assignments.resolveHomeroom(
                    academicSessionId, classId, effectiveDate);
            if (actor.employeeId().equals(homeroom.teacherId())) {
                return allow(actor.employeeId(), "TITULAIRE_ANY_SUBJECT",
                        homeroom.assignmentId(), homeroom.assignmentVersion(), null,
                        classId, subjectCode, "TITULAIRE_ANY_SUBJECT_EDIT_ALLOWED", effectiveDate);
            }
            return deny("ACADEMIC_TITULAIRE_SCOPE_DENIED",
                    "Cette classe ne vous est pas attribuée comme titulaire à cette date.",
                    "You are not the dated homeroom teacher for this class.", effectiveDate);
        }

        if (capability == Capability.ACADEMIC_ROSTER_VIEW) {
            // Primary and Kindergarten are class-teacher models: the dated
            // homeroom assignment is the class-access authority even when no
            // timetable, slot or class-subject distribution exists yet.
            if (isHomeroomLevel(resource.level())) {
                TeachingAssignmentResolver.Resolution homeroom = assignments.resolveHomeroom(
                        academicSessionId, classId, effectiveDate);
                if (actor.employeeId().equals(homeroom.teacherId())) {
                    return allow(actor.employeeId(), "PRIMARY_HOMEROOM",
                            homeroom.assignmentId(), homeroom.assignmentVersion(), null,
                            classId, subjectCode, homeroom.code(), effectiveDate);
                }
            }
            // Secondary keeps subject-teacher grade authority, but its dated
            // homeroom teacher still owns the class roster regardless of which
            // subjects are distributed to other teachers.
            if (isSecondaryLevel(resource.level())) {
                TeachingAssignmentResolver.Resolution homeroom = assignments.resolveHomeroom(
                        academicSessionId, classId, effectiveDate);
                if (actor.employeeId().equals(homeroom.teacherId())) {
                    return allow(actor.employeeId(), "SECONDARY_HOMEROOM_VIEW",
                            homeroom.assignmentId(), homeroom.assignmentVersion(), null,
                            classId, subjectCode, homeroom.code(), effectiveDate);
                }
            }
            if (isSecondaryLevel(resource.level())
                    && hasResponsibleAssignment(actor.employeeId(), academicSessionId, classId,
                    effectiveDate, subjectCode)) {
                return allow(actor.employeeId(), "SECONDARY_RESPONSIBLE", null, 0, null,
                        classId, subjectCode, "ACADEMIC_ACCESS_ALLOWED", effectiveDate);
            }
            return deny("ACADEMIC_CLASS_ACCESS_DENIED", denialFr(capability), denialEn(capability), effectiveDate);
        }

        boolean classWide = capability == Capability.CLASS_RESULTS_VIEW
                || capability == Capability.CLASS_REPORT_CARD_VIEW
                || capability == Capability.COUNCIL_INPUT_VIEW
                || capability == Capability.COUNCIL_INPUT_EDIT
                || capability == Capability.GRADE_PACKET_REVIEW;

        if (classWide) {
            TeachingAssignmentResolver.Resolution homeroom = assignments.resolveHomeroom(
                    academicSessionId, classId, effectiveDate);
            boolean isTitulaire = actor.employeeId().equals(homeroom.teacherId());
            if (isTitulaire && (capability != Capability.COUNCIL_INPUT_EDIT
                    || councilEditDefault(resource.level()))) {
                return allow(actor.employeeId(), sourceFor(resource.level(), "HOMEROOM"),
                        homeroom.assignmentId(), homeroom.assignmentVersion(), null,
                        classId, subjectCode, homeroom.code(), effectiveDate);
            }
            AccessDecision delegated = delegationDecision(actor.employeeId(), academicSessionId,
                    classId, subjectCode, capability, effectiveDate);
            if (delegated.allowed()) return delegated;
            return deny(denialCode(capability), denialFr(capability), denialEn(capability), effectiveDate);
        }

        if (subjectCode.isBlank() || !subjectInCurriculum(academicSessionId, classId,
                subjectCode, effectiveDate)) {
            return deny("ACADEMIC_SUBJECT_ACCESS_DENIED",
                    "Cette matière ne vous est pas attribuée pour cette classe et cette période.",
                    "This subject is not assigned to you for this class and period.", effectiveDate);
        }

        TeachingAssignmentResolver.Resolution subjectAssignment = assignments.resolve(
                academicSessionId, classId, subjectCode, effectiveDate);
        boolean ownsSubject = actor.employeeId().equals(subjectAssignment.teacherId());
        if (ownsSubject && subjectAssignment.available()) {
            return allow(actor.employeeId(), "secondary".equalsIgnoreCase(resource.level())
                            ? "SECONDARY_RESPONSIBLE" : "PRIMARY_HOMEROOM",
                    subjectAssignment.assignmentId(), subjectAssignment.assignmentVersion(), null,
                    classId, subjectCode, "ACADEMIC_ACCESS_ALLOWED", effectiveDate);
        }

        AccessDecision delegated = delegationDecision(actor.employeeId(), academicSessionId,
                classId, subjectCode, capability, effectiveDate);
        if (delegated.allowed()) return delegated;

        boolean assignmentBlocker = !subjectAssignment.available();
        String code = assignmentBlocker && subjectAssignment.code() != null
                && !subjectAssignment.code().isBlank() ? subjectAssignment.code() : denialCode(capability);
        String messageFr = assignmentBlocker && subjectAssignment.messageFr() != null
                ? subjectAssignment.messageFr() : denialFr(capability);
        String messageEn = assignmentBlocker && subjectAssignment.messageEn() != null
                ? subjectAssignment.messageEn() : denialEn(capability);
        return deny(code, messageFr, messageEn, effectiveDate);
    }

    /** Filter a curriculum collection at the service boundary. */
    @Transactional(readOnly = true)
    public List<SubjectCandidate> filterSubjects(UUID academicSessionId, UUID classId,
                                                 LocalDate effectiveDate, Capability capability,
                                                 List<SubjectCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        return candidates.stream().filter(x -> can(capability, academicSessionId, classId,
                x.code(), null, effectiveDate)).toList();
    }

    public Map<String, Boolean> capabilities(UUID academicSessionId, UUID classId,
                                             String subjectCode, UUID studentId,
                                             LocalDate effectiveDate) {
        Map<String, Boolean> values = new java.util.LinkedHashMap<>();
        Arrays.stream(Capability.values()).forEach(cap -> values.put(cap.name(),
                can(cap, academicSessionId, classId, subjectCode, studentId, effectiveDate)));
        return values;
    }

    @Transactional(readOnly = true)
    public void requireSnapshot(UUID snapshotId, Capability capability) {
        Map<String, Object> row = jdbc.query("""
                SELECT v.academic_session_id,v.reporting_period_id,v.student_id,e.school_class_id,
                       p.start_date
                  FROM bulletin_version v
                  LEFT JOIN student_enrollment e ON e.id=v.enrollment_id AND e.school_id=v.school_id
                  JOIN academic_reporting_period p ON p.id=v.reporting_period_id AND p.school_id=v.school_id
                 WHERE v.school_id=? AND v.id=?
                """, rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("session", rs.getObject(1, UUID.class));
                    result.put("period", rs.getObject(2, UUID.class));
                    result.put("student", rs.getObject(3, UUID.class));
                    result.put("class", rs.getObject(4, UUID.class));
                    result.put("date", rs.getObject(5, LocalDate.class));
                    return result;
                },
                TenantContext.get(), snapshotId);
        if (row == null || row.get("class") == null) throw ApiException.coded(HttpStatus.FORBIDDEN,
                "CLASS_RESULTS_ACCESS_DENIED", "Ce bulletin n'est pas accessible.");
        require(capability, (UUID) row.get("session"), (UUID) row.get("class"), null,
                (UUID) row.get("student"), (LocalDate) row.get("date"));
    }

    @Transactional(readOnly = true)
    public void requireAssessment(UUID assessmentId, Capability capability) {
        Map<String, Object> row = jdbc.query("""
                SELECT a.academic_session_id,a.class_id,a.subject_code,p.start_date
                  FROM academic_assessment a
                  JOIN academic_reporting_period p ON p.id=a.reporting_period_id AND p.school_id=a.school_id
                 WHERE a.school_id=? AND a.id=?
                """, rs -> rs.next() ? Map.of("session", rs.getObject(1, UUID.class),
                        "class", rs.getObject(2, UUID.class), "subject", rs.getString(3),
                        "date", rs.getObject(4, LocalDate.class)) : null,
                TenantContext.get(), assessmentId);
        if (row == null || row.get("class") == null || row.get("subject") == null) {
            throw ApiException.coded(HttpStatus.FORBIDDEN, "ACADEMIC_SUBJECT_ACCESS_DENIED",
                    "Cette évaluation n'est pas accessible.");
        }
        require(capability, (UUID) row.get("session"), (UUID) row.get("class"),
                (String) row.get("subject"), null, (LocalDate) row.get("date"));
    }

    @Transactional(readOnly = true)
    public void requireBatchJob(UUID jobId, Capability capability) {
        Map<String, Object> row = jdbc.query("""
                SELECT j.academic_session_id,j.class_id,p.start_date
                  FROM bulletin_batch_job j
                  JOIN academic_reporting_period p ON p.id=j.reporting_period_id AND p.school_id=j.school_id
                 WHERE j.school_id=? AND j.id=?
                """, rs -> rs.next() ? Map.of("session", rs.getObject(1, UUID.class),
                        "class", rs.getObject(2, UUID.class), "date", rs.getObject(3, LocalDate.class)) : null,
                TenantContext.get(), jobId);
        if (row == null) throw ApiException.coded(HttpStatus.FORBIDDEN,
                "CLASS_RESULTS_ACCESS_DENIED", "Ce traitement académique n'est pas accessible.");
        require(capability, (UUID) row.get("session"), (UUID) row.get("class"), null,
                null, (LocalDate) row.get("date"));
    }

    @Transactional(readOnly = true)
    public UUID currentSessionId() {
        return jdbc.query("SELECT id FROM academic_session WHERE school_id=? AND is_current=true",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, TenantContext.get());
    }

    @Transactional(readOnly = true)
    public LocalDate currentSessionStart() {
        return jdbc.query("SELECT start_date FROM academic_session WHERE school_id=? AND is_current=true",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null, TenantContext.get());
    }

    /** Resolve the current active enrollment before authorizing a student resource. */
    @Transactional(readOnly = true)
    public AccessDecision requireCurrentStudent(Capability capability, UUID studentId,
                                                 String subjectCode) {
        UUID sessionId = currentSessionId();
        LocalDate date = currentSessionStart();
        return requireStudent(capability, studentId, sessionId, date, subjectCode);
    }

    @Transactional(readOnly = true)
    public AccessDecision requireStudent(Capability capability, UUID studentId, UUID sessionId,
                                         LocalDate date, String subjectCode) {
        Enrollment enrollment = sessionId == null || date == null ? null : enrollment(studentId, sessionId, date);
        if (enrollment == null) {
            throw ApiException.coded(HttpStatus.FORBIDDEN, "ENROLLMENT_SCOPE_MISMATCH",
                    "L'inscription active de l'élève ne correspond pas à une session académique accessible.");
        }
        return require(capability, sessionId, enrollment.classId(), subjectCode, studentId, date);
    }

    @Transactional(readOnly = true)
    public AccessDecision requireCurrentClass(Capability capability, UUID classId,
                                              String subjectCode) {
        UUID sessionId = currentSessionId();
        LocalDate date = currentSessionStart();
        return require(capability, sessionId, classId, subjectCode, null, date);
    }

    public boolean restrictedTeacher() {
        Actor actor = actor();
        return actor != null && actor.teacherRole();
    }

    public UUID currentEmployeeId() {
        Actor actor = actor();
        return actor == null ? null : actor.employeeId();
    }

    public void requireDelegationManager() {
        Actor actor = actor();
        if (actor == null || actor.teacherRole() || !centralPolicy.canAction("PERMISSION_MANAGE")) {
            throw ApiException.coded(HttpStatus.FORBIDDEN, "ACADEMIC_ACCESS_DELEGATE_DENIED",
                    "Vous n'êtes pas autorisé à gérer les délégations académiques.");
        }
    }

    /** Read-only access to the delegation workspace is a settings permission. */
    public void requireDelegationViewer() {
        Actor actor = actor();
        if (actor == null || actor.teacherRole() || !centralPolicy.canAction("PERMISSION_VIEW")) {
            throw ApiException.coded(HttpStatus.FORBIDDEN, "ACADEMIC_ACCESS_AUDIT_DENIED",
                    "Academic delegation viewing is not authorized.");
        }
    }

    public boolean canAction(String action) {
        Actor actor = actor();
        return actor != null && !actor.teacherRole() && permissions.canAction(action);
    }

    private boolean managementAllowed(Actor actor, Capability capability, boolean centralAllowed) {
        if (actor.teacherRole()) return false;
        if (capability == Capability.TITULAIRE_ANY_SUBJECT_GRADE_EDIT) return false;
        if (centralAllowed) return true;
        String action = switch (capability) {
            case CLASS_RESULTS_VIEW -> "ACADEMIC_CLASS_RESULTS_VIEW";
            case CLASS_REPORT_CARD_VIEW -> "ACADEMIC_REPORT_CARD_VIEW";
            case SUBJECT_GRADE_EDIT -> "ACADEMIC_SUBJECT_GRADE_EDIT";
            case TITULAIRE_ANY_SUBJECT_GRADE_EDIT -> "GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS";
            case ASSESSMENT_MANAGE -> "ACADEMIC_ASSESSMENT_MANAGE";
            case GRADE_PACKET_REVIEW -> "ACADEMIC_GRADE_PACKET_REVIEW";
            case REPORT_CARD_VALIDATE -> "ACADEMIC_REPORT_CARD_VALIDATE";
            case REPORT_CARD_PUBLISH -> "ACADEMIC_REPORT_CARD_PUBLISH";
            case COUNCIL_INPUT_EDIT -> "ACADEMIC_COUNCIL_INPUT_EDIT";
            case SUBJECT_GRADE_VIEW, ASSESSMENT_VIEW, COUNCIL_INPUT_VIEW,
                    ACADEMIC_ROSTER_VIEW -> "ACADEMIC_CLASS_RESULTS_VIEW";
            case SUBJECT_GRADE_SUBMIT -> "GRADE_SUBMIT";
        };
        return action != null && permissions.canAction(action);
    }

    private AccessDecision delegationDecision(UUID employeeId, UUID sessionId, UUID classId,
                                              String subjectCode, Capability capability,
                                              LocalDate date) {
        if (employeeId == null) return deny(denialCode(capability), denialFr(capability), denialEn(capability), date);
        List<String> codes = delegatedCapabilityCodes(capability);
        String placeholders = String.join(",", java.util.Collections.nCopies(codes.size(), "?"));
        String sql = "SELECT id,status "
                + "FROM academic_access_delegation "
                + "WHERE school_id=? AND academic_session_id=? AND employee_id=? AND class_id=? "
                + "AND capability_code IN (" + placeholders + ") AND status='ACTIVE' "
                + "AND effective_from<=? AND (effective_to IS NULL OR effective_to>=?) "
                + "AND (subject_code IS NULL OR upper(subject_code)=upper(?)) "
                + "ORDER BY subject_code NULLS LAST,effective_from DESC LIMIT 1";
        List<Object> args = new java.util.ArrayList<>();
        args.add(TenantContext.get()); args.add(sessionId); args.add(employeeId); args.add(classId);
        args.addAll(codes); args.add(date); args.add(date); args.add(subjectCode == null ? "" : subjectCode);
        Delegation active = jdbc.query(sql, rs -> rs.next()
                        ? new Delegation(rs.getObject(1, UUID.class), rs.getString(2)) : null,
                args.toArray());
        if (active == null) return deny(denialCode(capability), denialFr(capability), denialEn(capability), date);
        return allow(employeeId, "DELEGATION", null, 0, active.id(), classId, subjectCode,
                "DELEGATION_ACTIVE", date);
    }

    private static List<String> delegatedCapabilityCodes(Capability capability) {
        return switch (capability) {
            case SUBJECT_GRADE_VIEW -> List.of(Capability.SUBJECT_GRADE_VIEW.name(),
                    Capability.SUBJECT_GRADE_EDIT.name(), Capability.SUBJECT_GRADE_SUBMIT.name());
            case SUBJECT_GRADE_EDIT -> List.of(Capability.SUBJECT_GRADE_EDIT.name(),
                    Capability.SUBJECT_GRADE_SUBMIT.name());
            case ASSESSMENT_VIEW -> List.of(Capability.ASSESSMENT_VIEW.name(),
                    Capability.ASSESSMENT_MANAGE.name());
            case COUNCIL_INPUT_VIEW -> List.of(Capability.COUNCIL_INPUT_VIEW.name(),
                    Capability.COUNCIL_INPUT_EDIT.name());
            default -> List.of(capability.name());
        };
    }

    private boolean subjectInCurriculum(UUID sessionId, UUID classId, String code, LocalDate date) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                  FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id
                 WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=?
                   AND upper(s.code)=upper(?)
                   AND (c.active_from IS NULL OR c.active_from<=?)
                   AND (c.active_to IS NULL OR c.active_to>=?)
                """, Integer.class, TenantContext.get(), sessionId, classId, code, date, date);
        return count != null && count > 0;
    }

    private boolean hasResponsibleAssignment(UUID employeeId, UUID sessionId, UUID classId,
                                             LocalDate date, String subjectCode) {
        if (subjectCode == null || subjectCode.isBlank()) {
            Integer count = jdbc.queryForObject("""
                    SELECT count(*) FROM academic_class_subject_teacher a
                     WHERE a.school_id=? AND a.academic_session_id=? AND a.class_id=?
                       AND a.employee_id=? AND a.role='RESPONSIBLE' AND a.active=true
                       AND (a.effective_from IS NULL OR a.effective_from<=?)
                       AND (a.effective_to IS NULL OR a.effective_to>=?)
                    """, Integer.class, TenantContext.get(), sessionId, classId, employeeId, date, date);
            return count != null && count > 0;
        }
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM academic_class_subject_teacher a JOIN subject s ON s.id=a.subject_id
                 WHERE a.school_id=? AND a.academic_session_id=? AND a.class_id=?
                   AND a.employee_id=? AND a.role='RESPONSIBLE' AND a.active=true
                   AND upper(s.code)=upper(?)
                   AND (a.effective_from IS NULL OR a.effective_from<=?)
                   AND (a.effective_to IS NULL OR a.effective_to>=?)
                """, Integer.class, TenantContext.get(), sessionId, classId, employeeId,
                subjectCode, date, date);
        return count != null && count > 0;
    }

    private boolean requireCentralAction(Capability capability, UUID academicSessionId,
                                         UUID classId, String rawSubjectCode, UUID studentId,
                                         LocalDate requestedDate) {
        if (centralPolicy == null) return false;
        String action = actionCode(capability);
        LocalDate effectiveDate = requestedDate != null
                ? requestedDate
                : sessionStart(academicSessionId);
        PolicyResourceContext context = new PolicyResourceContext(
                TenantContext.get(), academicSessionId,
                effectiveDate, resourceParcours(classId), classId,
                normalize(rawSubjectCode), studentId, null, null, null, null,
                resourceLevel(classId));
        var decision = centralPolicy.decide(action, context);
        if (!decision.allowed()) {
            throw ApiException.coded(HttpStatus.FORBIDDEN, decision.denialCode(), decision.messageFr());
        }
        return true;
    }

    private String resourceLevel(UUID classId) {
        if (classId == null) return null;
        return jdbc.query("SELECT lower(level) FROM school_class WHERE school_id=? AND id=?",
                rs -> rs.next() ? rs.getString(1) : null, TenantContext.get(), classId);
    }

    private LocalDate sessionStart(UUID academicSessionId) {
        if (academicSessionId == null) return currentSessionStart();
        return jdbc.query("SELECT start_date FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null,
                academicSessionId, TenantContext.get());
    }

    private ParcoursContext.Scope resourceParcours(UUID classId) {
        if (classId == null) return null;
        return jdbc.query("SELECT level,subsystem FROM school_class WHERE school_id=? AND id=?",
                rs -> rs.next() ? new ParcoursContext.Scope(rs.getString(1), rs.getString(2)) : null,
                TenantContext.get(), classId);
    }

    private static String actionCode(Capability capability) {
        return switch (capability) {
            case ACADEMIC_ROSTER_VIEW -> "ACADEMIC_ROSTER_VIEW";
            case ASSESSMENT_VIEW -> "ACADEMIC_ASSESSMENT_VIEW";
            case ASSESSMENT_MANAGE -> "ACADEMIC_ASSESSMENT_MANAGE";
            case SUBJECT_GRADE_VIEW -> "ACADEMIC_SUBJECT_GRADE_VIEW";
            case SUBJECT_GRADE_EDIT -> "ACADEMIC_SUBJECT_GRADE_EDIT";
            case TITULAIRE_ANY_SUBJECT_GRADE_EDIT -> "GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS";
            case SUBJECT_GRADE_SUBMIT -> "GRADE_SUBMIT";
            case CLASS_RESULTS_VIEW -> "ACADEMIC_CLASS_RESULTS_VIEW";
            case CLASS_REPORT_CARD_VIEW -> "ACADEMIC_REPORT_CARD_VIEW";
            case GRADE_PACKET_REVIEW -> "ACADEMIC_GRADE_PACKET_REVIEW";
            case REPORT_CARD_VALIDATE -> "ACADEMIC_REPORT_CARD_VALIDATE";
            case REPORT_CARD_PUBLISH -> "ACADEMIC_REPORT_CARD_PUBLISH";
            case COUNCIL_INPUT_VIEW -> "ACADEMIC_COUNCIL_INPUT_VIEW";
            case COUNCIL_INPUT_EDIT -> "ACADEMIC_COUNCIL_INPUT_EDIT";
        };
    }

    private Actor actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof AppUserPrincipal p)) return null;
        UUID tenant = TenantContext.get();
        if (p.schoolId() != null && tenant != null && !tenant.equals(p.schoolId())) return null;
        UUID employeeId = jdbc.query("""
                SELECT u.employee_id
                  FROM app_user u LEFT JOIN employee e ON e.id=u.employee_id AND e.school_id=u.school_id
                 WHERE u.id=? AND u.school_id=? AND u.active=true
                   AND (e.id IS NULL OR e.active=true)
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                p.userId(), tenant);
        return new Actor(p, employeeId, isTeacherRole(p.roleCode()));
    }

    private Session session(UUID sessionId) {
        return jdbc.query("SELECT id,start_date,end_date FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? new Session(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class),
                        rs.getObject(3, LocalDate.class)) : null, sessionId, TenantContext.get());
    }

    private Resource resource(UUID classId) {
        return jdbc.query("SELECT id,lower(level) FROM school_class WHERE id=? AND school_id=?",
                rs -> rs.next() ? new Resource(rs.getObject(1, UUID.class), rs.getString(2)) : null,
                classId, TenantContext.get());
    }

    private Enrollment enrollment(UUID studentId, UUID sessionId, LocalDate date) {
        return jdbc.query("""
                SELECT id,school_class_id FROM student_enrollment
                 WHERE school_id=? AND student_id=? AND academic_session_id=? AND status='ACTIVE'
                   AND enrolled_on<=? AND (exited_on IS NULL OR exited_on>=?)
                 ORDER BY enrolled_on DESC,created_at DESC LIMIT 1
                """, rs -> rs.next() ? new Enrollment(rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class)) : null,
                TenantContext.get(), studentId, sessionId, date, date);
    }

    private static boolean isTeacherRole(String role) {
        return role != null && List.of("teacher", "secondary_teacher", "form_teacher")
                .contains(role.toLowerCase(Locale.ROOT));
    }

    private static String sourceFor(String level, String source) {
        return "secondary".equalsIgnoreCase(level) ? "SECONDARY_HOMEROOM_VIEW" : "PRIMARY_HOMEROOM";
    }

    private static boolean isHomeroomLevel(String level) {
        return "primary".equalsIgnoreCase(level) || "maternelle".equalsIgnoreCase(level);
    }

    private static boolean isSecondaryLevel(String level) {
        return "secondary".equalsIgnoreCase(level);
    }

    private static boolean councilEditDefault(String level) {
        return !"secondary".equalsIgnoreCase(level);
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static AccessDecision allow(UUID employeeId, String source, UUID assignmentId,
                                        long assignmentVersion, UUID delegationId, UUID classId,
                                        String subjectCode, String code, LocalDate date) {
        return new AccessDecision(true, employeeId, source, assignmentId, assignmentVersion,
                delegationId, classId, subjectCode, code, "Accès académique autorisé.",
                "Academic access allowed.", date);
    }

    private static AccessDecision deny(String code, String fr, String en, LocalDate date) {
        return new AccessDecision(false, null, null, null, 0, null, null, null,
                code, fr, en, date);
    }

    private static String denialCode(Capability capability) {
        return switch (capability) {
            case CLASS_RESULTS_VIEW, CLASS_REPORT_CARD_VIEW -> "CLASS_RESULTS_ACCESS_DENIED";
            case SUBJECT_GRADE_VIEW, SUBJECT_GRADE_EDIT, TITULAIRE_ANY_SUBJECT_GRADE_EDIT,
                    SUBJECT_GRADE_SUBMIT,
                    ASSESSMENT_VIEW, ASSESSMENT_MANAGE -> "ACADEMIC_SUBJECT_ACCESS_DENIED";
            case GRADE_PACKET_REVIEW -> "ACADEMIC_PACKET_ACCESS_DENIED";
            case REPORT_CARD_VALIDATE, REPORT_CARD_PUBLISH -> "CLASS_RESULTS_ACCESS_DENIED";
            case COUNCIL_INPUT_VIEW, COUNCIL_INPUT_EDIT -> "ACADEMIC_COUNCIL_ACCESS_DENIED";
            case ACADEMIC_ROSTER_VIEW -> "ACADEMIC_CLASS_ACCESS_DENIED";
        };
    }

    private static String denialFr(Capability capability) {
        return switch (capability) {
            case SUBJECT_GRADE_VIEW, SUBJECT_GRADE_EDIT, SUBJECT_GRADE_SUBMIT,
                    ASSESSMENT_VIEW, ASSESSMENT_MANAGE -> "Cette matière ne vous est pas attribuée pour cette classe et cette période.";
            case GRADE_PACKET_REVIEW -> "Vous n'êtes pas autorisé à revoir cette feuille de notes.";
            case COUNCIL_INPUT_VIEW, COUNCIL_INPUT_EDIT -> "Les données du conseil de classe ne sont pas accessibles dans ce périmètre.";
            default -> "Les résultats complets de cette classe ne sont pas accessibles dans ce périmètre.";
        };
    }

    private static String denialEn(Capability capability) {
        return switch (capability) {
            case SUBJECT_GRADE_VIEW, SUBJECT_GRADE_EDIT, SUBJECT_GRADE_SUBMIT,
                    ASSESSMENT_VIEW, ASSESSMENT_MANAGE -> "This subject is not assigned to you for this class and period.";
            case GRADE_PACKET_REVIEW -> "You are not authorized to review this grade packet.";
            case COUNCIL_INPUT_VIEW, COUNCIL_INPUT_EDIT -> "Class-council data is not available in this scope.";
            default -> "Complete results for this class are not available in this scope.";
        };
    }
}
