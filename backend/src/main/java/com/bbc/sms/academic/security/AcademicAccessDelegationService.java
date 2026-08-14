package com.bbc.sms.academic.security;

import com.bbc.sms.academic.security.AcademicAccessDtos.*;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.bbc.sms.academic.security.AcademicAccessPolicyService.Capability;

/** Administrative lifecycle for temporary academic access grants. */
@Service
public class AcademicAccessDelegationService {
    private final JdbcTemplate jdbc;
    private final AcademicAccessPolicyService policy;
    private final AuditService audit;

    public AcademicAccessDelegationService(JdbcTemplate jdbc, AcademicAccessPolicyService policy,
                                           AuditService audit) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public DelegationPreview preview(DelegationRequest request) {
        policy.requireDelegationManager();
        ValidatedGrant grant = validate(request);
        List<String> capabilities = new ArrayList<>();
        capabilities.add(grant.capability().name());
        List<String> warnings = new ArrayList<>();
        if (request.effectiveTo() == null) warnings.add("DELEGATION_EXPIRY_RECOMMENDED");
        if (grant.subjectCode() == null && isSubjectEdit(grant.capability())) {
            warnings.add("CLASS_WIDE_EDIT_SCOPE");
        }
        return new DelegationPreview(request.academicSessionId(), request.employeeId(), request.classId(),
                grant.subjectId(), grant.subjectCode(), grant.capability().name(), grant.from(), grant.to(),
                grant.employeeName(), grant.employeeCode(), grant.accountUsername(), capabilities, warnings,
                List.of(), fingerprint(request, grant));
    }

    @Transactional
    public DelegationView create(DelegationRequest request) {
        policy.requireDelegationManager();
        ValidatedGrant grant = validate(request);
        UUID actor = currentUserId();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO academic_access_delegation
                        (id,school_id,academic_session_id,employee_id,class_id,subject_id,subject_code,
                         capability_code,effective_from,effective_to,status,reason,requested_by,
                         approved_by,approved_at,source)
                    VALUES (?,?,?,?,?,?,?,?,?,?, 'ACTIVE',?,?,?,now(),?)
                    """, id, TenantContext.get(), request.academicSessionId(), request.employeeId(),
                    request.classId(), grant.subjectId(), grant.subjectCode(), grant.capability().name(),
                    grant.from(), grant.to(), request.reason().trim(), actor,
                    normalizeSource(request.source()));
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.coded(HttpStatus.CONFLICT, "DELEGATION_OVERLAP",
                    "Une délégation active équivalente couvre déjà cette période.");
        }
        DelegationView view = find(id);
        audit.record("ACADEMIC_ACCESS_DELEGATION_CREATED", "ACADEMIC_ACCESS_DELEGATION", id.toString(),
                null, view, request.reason());
        return view;
    }

    @Transactional(readOnly = true)
    public List<DelegationView> list(UUID sessionId, UUID classId, UUID employeeId,
                                     String status) {
        policy.requireDelegationManager();
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        return jdbc.query("""
                SELECT d.id,d.academic_session_id,d.employee_id,e.name,e.code,
                       u.username,u.role_code,COALESCE(u.active,false),d.class_id,c.name,
                       d.subject_id,d.subject_code,d.capability_code,d.effective_from,d.effective_to,
                       d.status,d.reason,d.requested_by,d.approved_by,d.approved_at,
                       d.revoked_by,d.revoked_at,d.revocation_reason,d.source,d.version
                  FROM academic_access_delegation d
                  JOIN employee e ON e.id=d.employee_id AND e.school_id=d.school_id
                  JOIN school_class c ON c.id=d.class_id AND c.school_id=d.school_id
                  LEFT JOIN LATERAL (
                      SELECT username,role_code,active FROM app_user x
                       WHERE x.school_id=d.school_id AND x.employee_id=d.employee_id
                       ORDER BY x.active DESC,x.created_at DESC LIMIT 1
                  ) u ON true
                 WHERE d.school_id=?
                   AND (CAST(? AS uuid) IS NULL OR d.academic_session_id=CAST(? AS uuid))
                   AND (CAST(? AS uuid) IS NULL OR d.class_id=CAST(? AS uuid))
                   AND (CAST(? AS uuid) IS NULL OR d.employee_id=CAST(? AS uuid))
                   AND (? IS NULL OR d.status=?)
                 ORDER BY d.effective_from DESC,d.created_at DESC
                """, (rs, n) -> delegation(rs), TenantContext.get(), sessionId, sessionId,
                classId, classId, employeeId, employeeId, normalizedStatus, normalizedStatus);
    }

    @Transactional
    public DelegationView revoke(UUID id, DelegationRevokeRequest request) {
        policy.requireDelegationManager();
        DelegationView before = find(id);
        if (before == null) throw ApiException.notFound("Delegation");
        if (!"ACTIVE".equals(before.status())) {
            throw ApiException.coded(HttpStatus.CONFLICT, "DELEGATION_NOT_ACTIVE", "Cette délégation n'est plus active.");
        }
        if (request.version() != null && request.version() != before.version()) {
            throw ApiException.staleVersion("Cette délégation a été modifiée entre-temps.",
                    before.version(), request.version());
        }
        int updated = jdbc.update("""
                UPDATE academic_access_delegation
                   SET status='REVOKED',revoked_by=?,revoked_at=now(),revocation_reason=?,
                       updated_at=now(),version=version+1
                 WHERE id=? AND school_id=? AND status='ACTIVE'
                   AND (? IS NULL OR version=?)
                """, currentUserId(), request.reason().trim(), id, TenantContext.get(),
                request.version(), request.version());
        if (updated != 1) throw ApiException.coded(HttpStatus.CONFLICT, "DELEGATION_CHANGED",
                "La délégation a été modifiée entre-temps.");
        DelegationView after = find(id);
        audit.record("ACADEMIC_ACCESS_DELEGATION_REVOKED", "ACADEMIC_ACCESS_DELEGATION", id.toString(),
                before, after, request.reason());
        return after;
    }

    @Transactional(readOnly = true)
    public ReadinessView readiness(UUID requestedSessionId) {
        policy.requireDelegationManager();
        UUID sessionId = requestedSessionId == null ? policy.currentSessionId() : requestedSessionId;
        Map<String, Object> session = jdbc.query("SELECT code,label FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? Map.of("code", rs.getString(1), "label", rs.getString(2)) : null,
                sessionId, TenantContext.get());
        if (session == null) throw ApiException.notFound("Session académique");
        LocalDate date = policy.currentSessionStart();
        if (requestedSessionId != null) {
            date = jdbc.query("SELECT start_date FROM academic_session WHERE id=? AND school_id=?",
                    rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null, requestedSessionId, TenantContext.get());
        }
        List<ReadinessIssue> issues = new ArrayList<>();
        issues.addAll(missingHomeroom(sessionId, date));
        issues.addAll(missingResponsible(sessionId, date));
        issues.addAll(unlinkedAssignments(sessionId, date));
        issues.addAll(duplicateNames());
        int missingHome = (int) issues.stream().filter(x -> "HOMEROOM_ASSIGNMENT_MISSING".equals(x.code())).count();
        int missingResponsible = (int) issues.stream().filter(x -> "RESPONSIBLE_ASSIGNMENT_MISSING".equals(x.code())).count();
        int ambiguous = (int) issues.stream().filter(x -> "RESPONSIBLE_ASSIGNMENT_AMBIGUOUS".equals(x.code())).count();
        int duplicates = (int) issues.stream().filter(x -> "DUPLICATE_EMPLOYEE_NAME".equals(x.code())).count();
        int unlinked = (int) issues.stream().filter(x -> "TEACHER_ACCOUNT_NOT_LINKED".equals(x.code())).count();
        return new ReadinessView(sessionId, String.valueOf(session.get("code")), String.valueOf(session.get("label")),
                issues.size(), missingHome, missingResponsible, ambiguous, duplicates, unlinked, issues);
    }

    @Transactional(readOnly = true)
    public DelegationPreview previewRequest(DelegationRequest request) { return preview(request); }

    @Transactional(readOnly = true)
    public List<ScopeSubject> teacherPreview(UUID employeeId, UUID sessionId, LocalDate date) {
        policy.requireDelegationManager();
        employee(employeeId);
        return jdbc.query("""
                SELECT c.id,c.name,c.level,s.code,COALESCE(s.label->>'fr',s.label->>'en',s.code),cur.remark_required
                  FROM academic_curriculum_subject cur
                  JOIN school_class c ON c.id=cur.class_id AND c.school_id=cur.school_id
                  JOIN subject s ON s.id=cur.subject_id
                 WHERE cur.school_id=? AND cur.academic_session_id=?
                   AND (cur.active_from IS NULL OR cur.active_from<=?)
                   AND (cur.active_to IS NULL OR cur.active_to>=?)
                 ORDER BY c.name,cur.display_order,s.code
                """, (rs, n) -> {
                    UUID classId = rs.getObject(1, UUID.class);
                    String code = rs.getString(4);
                    Map<String, Boolean> caps = new java.util.LinkedHashMap<>();
                    for (Capability capability : List.of(Capability.SUBJECT_GRADE_VIEW,
                            Capability.SUBJECT_GRADE_EDIT, Capability.SUBJECT_GRADE_SUBMIT,
                            Capability.CLASS_RESULTS_VIEW, Capability.CLASS_REPORT_CARD_VIEW)) {
                        caps.put(capability.name(), employeeCan(employeeId, capability, sessionId, classId, code, date));
                    }
                    String source = caps.get(Capability.SUBJECT_GRADE_EDIT.name()) ? "ASSIGNMENT" : "NONE";
                    return new ScopeSubject(code, rs.getString(5), classId, rs.getString(2), rs.getString(3),
                            source, null, 0, caps);
                }, TenantContext.get(), sessionId, date, date);
    }

    @Transactional(readOnly = true)
    public MyScopeView myScope(UUID sessionId, UUID periodId) {
        LocalDate date = periodId == null ? sessionStart(sessionId) : jdbc.query(
                "SELECT start_date FROM academic_reporting_period WHERE id=? AND school_id=?",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null, periodId, TenantContext.get());
        if (date == null) date = sessionStart(sessionId);
        String periodCode = null, periodLabel = null;
        if (periodId != null) {
            Map<String, Object> p = jdbc.query("SELECT code,label FROM academic_reporting_period WHERE id=? AND school_id=?",
                    rs -> rs.next() ? Map.of("code", rs.getString(1), "label", rs.getString(2)) : null,
                    periodId, TenantContext.get());
            if (p != null) { periodCode = String.valueOf(p.get("code")); periodLabel = String.valueOf(p.get("label")); }
        }
        List<ScopeSubject> subjects = teacherScopeSubjects(sessionId, date, false);
        List<ScopeSubject> overviews = teacherScopeSubjects(sessionId, date, true);
        return new MyScopeView(sessionId, periodId, periodCode, periodLabel, subjects, overviews);
    }

    private List<ScopeSubject> teacherScopeSubjects(UUID sessionId, LocalDate date, boolean overview) {
        UUID employeeId = policy.currentEmployeeId();
        if (employeeId == null) return List.of();
        return jdbc.query("""
                SELECT c.id,c.name,c.level,s.code,COALESCE(s.label->>'fr',s.label->>'en',s.code)
                  FROM academic_curriculum_subject cur
                  JOIN school_class c ON c.id=cur.class_id AND c.school_id=cur.school_id
                  JOIN subject s ON s.id=cur.subject_id
                 WHERE cur.school_id=? AND cur.academic_session_id=?
                   AND (cur.active_from IS NULL OR cur.active_from<=?)
                   AND (cur.active_to IS NULL OR cur.active_to>=?)
                 ORDER BY c.name,cur.display_order,s.code
                """, (rs, n) -> {
                    UUID classId = rs.getObject(1, UUID.class); String code = rs.getString(4);
                    Capability cap = overview ? Capability.CLASS_RESULTS_VIEW : Capability.SUBJECT_GRADE_VIEW;
                    AcademicAccessPolicyService.AccessDecision d = employeeDecision(employeeId, cap, sessionId, classId, overview ? null : code, date);
                    if (!d.allowed()) return null;
                    return new ScopeSubject(code, rs.getString(5), classId, rs.getString(2), rs.getString(3),
                            d.source(), d.assignmentId(), d.assignmentVersion(), policy.capabilities(sessionId, classId, overview ? null : code, null, date));
                }, TenantContext.get(), sessionId, date, date).stream().filter(java.util.Objects::nonNull).toList();
    }

    private ValidatedGrant validate(DelegationRequest request) {
        Capability capability;
        try { capability = Capability.valueOf(request.capabilityCode().trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw ApiException.badRequest("Capacité académique invalide."); }
        Map<String, Object> session = jdbc.query("SELECT start_date,end_date FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? Map.of("start", rs.getObject(1, LocalDate.class), "end", rs.getObject(2, LocalDate.class)) : null,
                request.academicSessionId(), TenantContext.get());
        if (session == null) throw ApiException.notFound("Session académique");
        LocalDate from = request.effectiveFrom(); LocalDate to = request.effectiveTo();
        LocalDate start = (LocalDate) session.get("start"), end = (LocalDate) session.get("end");
        if (from.isBefore(start) || from.isAfter(end) || (to != null && (to.isBefore(from) || to.isAfter(end)))) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, "ASSIGNMENT_DATE_OUTSIDE_SESSION",
                    "La période de délégation doit rester dans la session académique.", "effectiveFrom",
                    "Delegation dates must stay inside the academic session.");
        }
        Map<String, Object> employee = employee(request.employeeId());
        Map<String, Object> clazz = jdbc.query("SELECT name,level FROM school_class WHERE id=? AND school_id=?",
                rs -> rs.next() ? Map.of("name", rs.getString(1), "level", rs.getString(2)) : null,
                request.classId(), TenantContext.get());
        if (clazz == null) throw ApiException.notFound("Classe");
        String subjectCode = request.subjectCode() == null || request.subjectCode().isBlank()
                ? null : request.subjectCode().trim().toUpperCase(Locale.ROOT);
        UUID subjectId = request.subjectId();
        if (subjectId != null) {
            Map<String, Object> subject = jdbc.query("SELECT code FROM subject WHERE id=? AND school_id=?",
                    rs -> rs.next() ? Map.of("code", rs.getString(1)) : null, subjectId, TenantContext.get());
            if (subject == null) throw ApiException.notFound("Matière");
            String resolved = String.valueOf(subject.get("code")).toUpperCase(Locale.ROOT);
            if (subjectCode != null && !resolved.equals(subjectCode)) throw ApiException.badRequest("La matière sélectionnée est incohérente.");
            subjectCode = resolved;
        } else if (subjectCode != null) {
            subjectId = jdbc.query("SELECT id FROM subject WHERE school_id=? AND upper(code)=upper(?)",
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : null, TenantContext.get(), subjectCode);
            if (subjectId == null) throw ApiException.notFound("Matière");
        }
        boolean subjectScoped = isSubjectScoped(capability);
        if (subjectScoped != (subjectCode != null)) throw ApiException.badRequest(
                subjectScoped ? "Cette capacité doit être limitée à une matière." : "Cette capacité doit être limitée à la classe.");
        if (subjectCode != null && !curriculum(request.academicSessionId(), request.classId(), subjectCode, from)) {
            throw ApiException.coded(HttpStatus.BAD_REQUEST, "SUBJECT_NOT_ASSIGNED_TO_CLASS",
                    "Cette matière n'est pas affectée à la classe pour cette période.");
        }
        if (isSubjectEdit(capability) && to == null) throw ApiException.field(HttpStatus.BAD_REQUEST,
                "DELEGATION_EXPIRY_REQUIRED", "Une délégation de modification doit avoir une date d'expiration.",
                "effectiveTo", "An expiry date is required for an edit delegation.");
        if (request.reason() == null || request.reason().trim().length() < 3) throw ApiException.field(
                HttpStatus.BAD_REQUEST, "DELEGATION_REASON_REQUIRED", "Le motif de la délégation est obligatoire.",
                "reason", "Provide a reason for this delegation.");
        return new ValidatedGrant(capability, subjectId, subjectCode, from, to,
                String.valueOf(employee.get("name")), String.valueOf(employee.get("code")),
                employee.get("username") == null ? null : String.valueOf(employee.get("username")));
    }

    private boolean employeeCan(UUID employeeId, Capability capability, UUID sessionId,
                                UUID classId, String subjectCode, LocalDate date) {
        return employeeDecision(employeeId, capability, sessionId, classId, subjectCode, date).allowed();
    }

    private AcademicAccessPolicyService.AccessDecision employeeDecision(UUID employeeId, Capability capability,
                                                                         UUID sessionId, UUID classId,
                                                                         String subjectCode, LocalDate date) {
        if (capability == Capability.CLASS_RESULTS_VIEW || capability == Capability.CLASS_REPORT_CARD_VIEW) {
            AcademicAccessPolicyService.AccessDecision d = policy.employeeDecision(employeeId, capability, sessionId, classId, null, date);
            return d;
        }
        return policy.employeeDecision(employeeId, capability, sessionId, classId, subjectCode, date);
    }

    private DelegationView find(UUID id) {
        return jdbc.query("""
                SELECT d.id,d.academic_session_id,d.employee_id,e.name,e.code,
                       u.username,u.role_code,COALESCE(u.active,false),d.class_id,c.name,
                       d.subject_id,d.subject_code,d.capability_code,d.effective_from,d.effective_to,
                       d.status,d.reason,d.requested_by,d.approved_by,d.approved_at,
                       d.revoked_by,d.revoked_at,d.revocation_reason,d.source,d.version
                  FROM academic_access_delegation d JOIN employee e ON e.id=d.employee_id AND e.school_id=d.school_id
                  JOIN school_class c ON c.id=d.class_id AND c.school_id=d.school_id
                  LEFT JOIN LATERAL (SELECT username,role_code,active FROM app_user x
                                      WHERE x.school_id=d.school_id AND x.employee_id=d.employee_id
                                      ORDER BY x.active DESC,x.created_at DESC LIMIT 1) u ON true
                 WHERE d.school_id=? AND d.id=?
                """, rs -> rs.next() ? delegation(rs) : null, TenantContext.get(), id);
    }

    private DelegationView delegation(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DelegationView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getBoolean(8),
                rs.getObject(9, UUID.class), rs.getString(10), rs.getObject(11, UUID.class), rs.getString(12),
                rs.getString(13), rs.getObject(14, LocalDate.class), rs.getObject(15, LocalDate.class),
                rs.getString(16), rs.getString(17), rs.getObject(18, UUID.class), rs.getObject(19, UUID.class),
                rs.getObject(20, OffsetDateTime.class), rs.getObject(21, UUID.class), rs.getObject(22, OffsetDateTime.class),
                rs.getString(23), rs.getString(24), rs.getLong(25));
    }

    private Map<String, Object> employee(UUID id) {
        Map<String, Object> row = jdbc.query("""
                SELECT e.name,e.code,
                       (SELECT x.username FROM app_user x WHERE x.school_id=e.school_id AND x.employee_id=e.id
                        ORDER BY x.active DESC,x.created_at DESC LIMIT 1) AS username
                  FROM employee e WHERE e.id=? AND e.school_id=? AND e.active=true
                """, rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("name", rs.getString(1));
                    result.put("code", rs.getString(2));
                    result.put("username", rs.getString(3));
                    return result;
                }, id, TenantContext.get());
        if (row == null) throw ApiException.notFound("Employé");
        return row;
    }

    private List<ReadinessIssue> missingHomeroom(UUID sessionId, LocalDate date) {
        return jdbc.query("""
                SELECT c.id,c.name
                  FROM school_class c
                 WHERE c.school_id=? AND lower(c.level) IN ('primary','maternelle')
                   AND NOT EXISTS (SELECT 1 FROM class_teacher_assignment a
                                    WHERE a.school_id=c.school_id AND a.academic_session_id=? AND a.class_id=c.id
                                      AND a.role='HOMEROOM' AND a.status='ACTIVE'
                                      AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>=?))
                """, (rs, n) -> issue("HOMEROOM_ASSIGNMENT_MISSING", "ERROR", sessionId,
                        rs.getObject(1, UUID.class), rs.getString(2), null, null, null, null, null, null,
                        "Configurez le titulaire de la classe.", "Configure the class homeroom teacher.", "class-subjects"),
                TenantContext.get(), sessionId, date, date);
    }

    private List<ReadinessIssue> missingResponsible(UUID sessionId, LocalDate date) {
        return jdbc.query("""
                SELECT c.id,c.name,s.id,s.code,count(a.id)
                  FROM academic_curriculum_subject cur
                  JOIN school_class c ON c.id=cur.class_id AND c.school_id=cur.school_id
                  JOIN subject s ON s.id=cur.subject_id
                  LEFT JOIN academic_class_subject_teacher a ON a.school_id=cur.school_id
                    AND a.academic_session_id=cur.academic_session_id AND a.class_id=cur.class_id
                    AND a.subject_id=cur.subject_id AND a.role='RESPONSIBLE' AND a.active=true
                    AND (a.effective_from IS NULL OR a.effective_from<=?)
                    AND (a.effective_to IS NULL OR a.effective_to>=?)
                 WHERE cur.school_id=? AND cur.academic_session_id=? AND lower(c.level)='secondary'
                   AND (cur.active_from IS NULL OR cur.active_from<=?)
                   AND (cur.active_to IS NULL OR cur.active_to>=?)
                 GROUP BY c.id,c.name,s.id,s.code
                HAVING count(a.id) <> 1
                """, (rs, n) -> {
                    long count = rs.getLong(5);
                    String code = count == 0 ? "RESPONSIBLE_ASSIGNMENT_MISSING" : "RESPONSIBLE_ASSIGNMENT_AMBIGUOUS";
                    String fr = count == 0 ? "Affectez un enseignant responsable pour cette matière." : "Réparez les enseignants responsables qui se chevauchent.";
                    String en = count == 0 ? "Assign one responsible teacher for this subject." : "Repair overlapping responsible teachers.";
                    return issue(code, "ERROR", sessionId, rs.getObject(1, UUID.class), rs.getString(2),
                            rs.getObject(3, UUID.class), rs.getString(4), null, null, null, null, fr, en, "class-subjects");
                }, date, date, TenantContext.get(), sessionId, date, date);
    }

    private List<ReadinessIssue> unlinkedAssignments(UUID sessionId, LocalDate date) {
        return jdbc.query("""
                SELECT DISTINCT e.id,e.name,e.code
                  FROM employee e
                 WHERE e.school_id=? AND e.active=true
                   AND (EXISTS (SELECT 1 FROM class_teacher_assignment h WHERE h.school_id=e.school_id
                                AND h.academic_session_id=? AND h.employee_id=e.id AND h.role='HOMEROOM'
                                AND h.status='ACTIVE' AND h.effective_from<=? AND (h.effective_to IS NULL OR h.effective_to>=?))
                     OR EXISTS (SELECT 1 FROM academic_class_subject_teacher a WHERE a.school_id=e.school_id
                                AND a.academic_session_id=? AND a.employee_id=e.id AND a.role='RESPONSIBLE'
                                AND a.active=true AND (a.effective_from IS NULL OR a.effective_from<=?)
                                AND (a.effective_to IS NULL OR a.effective_to>=?)))
                   AND NOT EXISTS (SELECT 1 FROM app_user u WHERE u.school_id=e.school_id AND u.employee_id=e.id AND u.active=true)
                """, (rs, n) -> issue("TEACHER_ACCOUNT_NOT_LINKED", "WARNING", sessionId, null, null,
                        null, null, rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), null,
                        "Le personnel enseignant n'a pas de compte actif lié.",
                        "This teaching employee has no linked active account.", "staff"),
                TenantContext.get(), sessionId, date, date, sessionId, date, date);
    }

    private List<ReadinessIssue> duplicateNames() {
        return jdbc.query("""
                SELECT min(e.id),min(e.name),min(e.code),count(*)
                  FROM employee e
                 WHERE e.school_id=? AND e.active=true
                 GROUP BY lower(regexp_replace(e.name,'[^[:alnum:]]','','g'))
                HAVING count(*)>1
                """, (rs, n) -> issue("DUPLICATE_EMPLOYEE_NAME", "WARNING", null, null, null, null, null,
                        rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), null,
                        "Plusieurs employés portent un nom similaire : utilisez le code et le compte.",
                        "Several employees share this normalized name; use the code and account identity.", "staff"),
                TenantContext.get());
    }

    private ReadinessIssue issue(String code, String severity, UUID sessionId, UUID classId,
                                 String className, UUID subjectId, String subjectCode,
                                 UUID employeeId, String employeeName, String employeeCode,
                                 String username, String fr, String en, String target) {
        return new ReadinessIssue(code, severity, sessionId, classId, className, subjectId, subjectCode,
                employeeId, employeeName, employeeCode, username, fr, en, target);
    }

    private boolean curriculum(UUID sessionId, UUID classId, String subjectCode, LocalDate date) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id
                 WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=? AND upper(s.code)=upper(?)
                   AND (c.active_from IS NULL OR c.active_from<=?) AND (c.active_to IS NULL OR c.active_to>=?)
                """, Integer.class, TenantContext.get(), sessionId, classId, subjectCode, date, date);
        return count != null && count > 0;
    }

    private LocalDate sessionStart(UUID sessionId) {
        return jdbc.query("SELECT start_date FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null, sessionId, TenantContext.get());
    }

    private static boolean isSubjectScoped(Capability capability) {
        return switch (capability) {
            case SUBJECT_GRADE_VIEW, SUBJECT_GRADE_EDIT, SUBJECT_GRADE_SUBMIT,
                    ASSESSMENT_VIEW, ASSESSMENT_MANAGE -> true;
            default -> false;
        };
    }

    private static boolean isSubjectEdit(Capability capability) {
        return capability == Capability.SUBJECT_GRADE_EDIT || capability == Capability.SUBJECT_GRADE_SUBMIT;
    }

    private static String normalizeSource(String source) {
        String value = source == null || source.isBlank() ? "MANUAL" : source.trim().toUpperCase(Locale.ROOT);
        return List.of("MANUAL", "SUBSTITUTION", "IMPORT").contains(value) ? value : "MANUAL";
    }

    private static String fingerprint(DelegationRequest request, ValidatedGrant grant) {
        String raw = request.academicSessionId() + ":" + request.employeeId() + ":" + request.classId() + ":"
                + grant.subjectCode() + ":" + grant.capability() + ":" + grant.from() + ":" + grant.to();
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private record ValidatedGrant(Capability capability, UUID subjectId, String subjectCode,
                                  LocalDate from, LocalDate to, String employeeName,
                                  String employeeCode, String accountUsername) {}
}
