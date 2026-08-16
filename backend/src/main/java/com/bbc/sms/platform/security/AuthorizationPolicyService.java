package com.bbc.sms.platform.security;

import com.bbc.sms.academic.security.AcademicScopeResolver;
import com.bbc.sms.guardian.GuardianAccessService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central action + scope + invariant evaluator.
 *
 * <p>Module grants remain navigation compatibility gates.  This service never
 * treats a module grant as a business authorization.  Every allow must have a
 * catalogue action, a matching rule scope and all applicable tenant/session/
 * enrollment/assignment/guardian invariants.</p>
 */
@Service("policy")
public class AuthorizationPolicyService {
    record Action(String code, String module, String scopeType, String requiredLevel) {}

    record Rule(String source, String effect, String scopeMode, String scopePayload,
                LocalDate effectiveFrom, LocalDate effectiveTo) {}

    private record Rollout(String mode, boolean enforcementEnabled, String compatibilityRole) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AcademicScopeResolver academicScopes;
    private final ParcoursAccessService parcours;
    private final GuardianAccessService guardianAccess;
    private final AttendanceScopeResolver attendanceScopes;

    public AuthorizationPolicyService(JdbcTemplate jdbc, ObjectMapper mapper,
                                      AcademicScopeResolver academicScopes,
                                      ParcoursAccessService parcours,
                                      GuardianAccessService guardianAccess,
                                      AttendanceScopeResolver attendanceScopes) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.academicScopes = academicScopes;
        this.parcours = parcours;
        this.guardianAccess = guardianAccess;
        this.attendanceScopes = attendanceScopes;
    }

    @Transactional(readOnly = true)
    public PolicyDecision decide(String rawActionCode, PolicyResourceContext suppliedContext) {
        String actionCode = normalize(rawActionCode);
        AppUserPrincipal principal = currentPrincipal();
        if (principal == null) {
            return deny(actionCode, "AUTHENTICATION_REQUIRED",
                    "Authentification requise.", "Authentication is required.", 0,
                    "Reconnectez-vous.");
        }
        UUID tenant = currentTenant(principal);
        long version = tenant == null ? 0 : policyVersion(tenant);
        if (tenant == null) {
            return deny(actionCode, "TENANT_CONTEXT_MISSING",
                    "L'établissement courant est introuvable.", "The current school is missing.", version,
                    "Rechargez votre session.");
        }

        PolicyResourceContext context = suppliedContext == null
                ? PolicyResourceContext.empty().forSchool(tenant)
                : (suppliedContext.schoolId() == null ? suppliedContext.forSchool(tenant) : suppliedContext);
        if (!tenant.equals(context.schoolId())) {
            return deny(actionCode, "TENANT_MISMATCH",
                    "Cette ressource appartient à un autre établissement.",
                    "This resource belongs to another school.", version, null);
        }
        Action action = action(actionCode);
        if (action == null) {
            return deny(actionCode, "ACTION_UNKNOWN",
                    "Cette action n'est pas configurée dans le catalogue.",
                    "This action is not configured in the catalogue.", version,
                    "Demandez à un administrateur de publier cette action.");
        }
        if (!activeAccount(principal, tenant)) {
            return deny(actionCode, "ACCOUNT_DISABLED",
                    "Ce compte est désactivé.", "This account is disabled.", version, null);
        }
        List<String> activeRoles = activeRoles(principal, tenant, effectiveDate(context));
        boolean parent = isParent(principal, activeRoles, tenant);
        boolean parentOnly = isParentOnly(principal, activeRoles, tenant);
        if ("SELF".equalsIgnoreCase(action.scopeType())) {
            // The owner is never accepted from a request DTO.  SELF is
            // resolved from the active account's employee link on the server.
            context = context.withOwnerEmployeeId(employeeId(principal, tenant));
        }
        if (parentOnly && !"parent".equalsIgnoreCase(action.module())) {
            return deny(actionCode, "PARENT_STAFF_ENDPOINT_DENIED",
                    "Le portail parent ne donne pas accès aux écrans du personnel.",
                    "The parent portal does not grant staff endpoint access.", version,
                    "Ouvrez le portail parent ou contactez l'établissement.");
        }
        if (!resourcePresent(action, context)) {
            return deny(actionCode, "RESOURCE_CONTEXT_REQUIRED",
                    "Le contexte de la ressource est incomplet.",
                    "The resource context is incomplete.", version,
                    "Rechargez la page afin de fournir la session, la classe, la matière ou l'élève.");
        }
        if (!sessionInvariant(context)) {
            return deny(actionCode, "ACADEMIC_EFFECTIVE_DATE_OUT_OF_SESSION",
                    "La date d'effet ne se trouve pas dans la session académique.",
                    "The effective date is outside the academic session.", version, null);
        }
        if (!activeEnrollmentInvariant(action, context, activeRoles)) {
            return deny(actionCode, "ENROLLMENT_SCOPE_MISMATCH",
                    "L'inscription active de l'élève ne correspond pas à cette demande.",
                    "The student's active enrollment does not match this request.", version,
                    "Vérifiez la session et la classe de l'inscription.");
        }

        Rollout rollout = rollout(tenant);
        List<Rule> userRules = userRules(principal.userId(), tenant, actionCode, effectiveDate(context));
        PolicyDecision userDecision = evaluateRules(action, context, principal, userRules, activeRoles, version);
        if (userDecision != null) return userDecision;

        // During the compatibility window a safe principal DENY must not
        // override the generated legacy profile.  Evaluate the compatibility
        // profile alone until the administrator explicitly adopts oversight.
        List<String> evaluationRoles = activeRoles;
        if (!rollout.enforcementEnabled() && "LEGACY_COMPATIBILITY".equals(rollout.mode())
                && activeRoles.contains(rollout.compatibilityRole())) {
            evaluationRoles = List.of(rollout.compatibilityRole());
        }
        List<Rule> roleRules = roleRules(tenant, evaluationRoles, actionCode, effectiveDate(context));
        // Domain invariants use all active roles even while the rule source is
        // narrowed to the visible legacy compatibility profile.
        PolicyDecision roleDecision = evaluateRules(action, context, principal, roleRules, activeRoles, version);
        if (roleDecision != null) return roleDecision;

        return deny(actionCode, "POLICY_RULE_MISSING",
                "Aucune règle active n'autorise cette action dans ce périmètre.",
                "No active rule allows this action in this scope.", version,
                "Demandez un profil ou une délégation limitée avec une justification.");
    }

    public PolicyDecision require(String actionCode, PolicyResourceContext context) {
        PolicyDecision decision = decide(actionCode, context);
        if (!decision.allowed()) {
            throw ApiException.coded(HttpStatus.FORBIDDEN, decision.denialCode(), decision.messageFr());
        }
        return decision;
    }

    /**
     * Context-free gate for navigation and the policy workspace itself.  A
     * resource-scoped action must be evaluated with a server-resolved
     * resource context through {@link #decide(String, PolicyResourceContext)};
     * treating a missing context as a global deny would hide valid teacher,
     * parent, class and occurrence capabilities in the UI.
     */
    public boolean canAction(String actionCode) {
        AppUserPrincipal principal = currentPrincipal();
        if (principal == null) return false;
        Action action = action(normalize(actionCode));
        if (action == null || !Set.of("NONE", "SCHOOL")
                .contains(action.scopeType().toUpperCase(Locale.ROOT))) return false;
        return decide(action.code(), PolicyResourceContext.empty().forSchool(currentTenant(principal))).allowed();
    }

    public long currentPolicyVersion() {
        AppUserPrincipal p = currentPrincipal();
        UUID tenant = p == null ? null : currentTenant(p);
        return tenant == null ? 0 : policyVersion(tenant);
    }

    PolicyDecision evaluateRules(Action action, PolicyResourceContext context,
                                 AppUserPrincipal principal, List<Rule> rules,
                                 List<String> roles, long version) {
        PolicyDecision winningAllow = null;
        boolean domainRejectedAllow = false;
        for (Rule rule : rules) {
            if (!matches(rule, action, context, principal)) continue;
            if ("DENY".equals(rule.effect())) {
                return deny(action.code(), "POLICY_EXPLICIT_DENY",
                        "Une règle de refus explicite bloque cette action.",
                        "An explicit deny rule blocks this action.", version,
                        "Consultez la règle refusée et son périmètre.");
            }
            if ("ALLOW".equals(rule.effect()) && winningAllow == null) {
                if (!domainInvariant(action, context, principal, rule, roles)) {
                    // A generic resource scope can match more than one role
                    // rule. A form teacher may have both a dated titular-class
                    // rule and a secondary published-occurrence rule. An
                    // incompatible allow must not hide a later compatible one.
                    domainRejectedAllow = true;
                    continue;
                }
                winningAllow = PolicyDecision.allow(action.code(), rule.source(),
                        rule.scopeMode(), version);
            }
        }
        if (winningAllow != null) return winningAllow;
        if (domainRejectedAllow) {
            return deny(action.code(), domainDenialCode(action, roles),
                    domainDenialFr(action, roles), domainDenialEn(action, roles), version,
                    "Réparez l'affectation, la session, la classe ou la relation familiale.");
        }
        return null;
    }

    boolean matches(Rule rule, Action action, PolicyResourceContext context,
                    AppUserPrincipal principal) {
        if ("INHERIT".equals(rule.effect())) return false;
        PolicyScopeMode mode = safeMode(rule.scopeMode());
        if (mode == null || !scopeCompatible(action, mode)) return false;
        if ("GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS".equals(action.code())
                && mode != PolicyScopeMode.CLASS_SET) return false;
        return switch (mode) {
            case NONE -> true;
            case SCHOOL_ALL -> true;
            case PARCOURS_ALLOWED -> context.parcours() != null
                    && parcours.isAllowed(principal.userId(), context.parcours());
            case ASSIGNED_CLASSES -> academicScopes.can("ACADEMIC_ROSTER_VIEW", context);
            case TITULAIRE_CLASSES -> academicScopes.can("ACADEMIC_CLASS_RESULTS_VIEW", context);
            case ASSIGNED_CLASS_SUBJECTS -> academicScopes.can(subjectAction(action), context);
            case TIMETABLE_OCCURRENCES_ASSIGNED -> attendanceScopes.publishedOccurrenceAssigned(principal, context);
            case LINKED_CHILDREN -> guardianAccess.canAccess(context.schoolId(), principal.userId(),
                    context.studentId(), parentFeature(action.code()), effectiveDate(context));
            case SELF -> ownerMatches(principal, context);
            case CLASS_SET -> idInPayload(context.classId(), rule.scopePayload(), "classIds");
            case SUBJECT_SET -> valueInPayload(context.subjectCode(), rule.scopePayload(), "subjects");
            case CLASS_SUBJECT_SET -> classSubjectInPayload(context, rule.scopePayload());
            case PARCOURS_SET -> parcoursInPayload(context.parcours(), rule.scopePayload());
        };
    }

    private boolean domainInvariant(Action action, PolicyResourceContext context,
                                    AppUserPrincipal principal, Rule rule,
                                    List<String> roles) {
        boolean teacher = isTeacher(roles);
        boolean parent = isParent(principal, roles, context.schoolId());
        if (teacher && "students".equalsIgnoreCase(action.module()) && !isStudentReadAction(action.code())) {
            // Teacher templates are read-only by default, but an exact,
            // resource-scoped management action may be deliberately granted.
            // Do not turn the module grant into authority: the matching rule
            // must come from an approved student-management role or a dated
            // user exception, and the existing enrollment/assignment scope is
            // still mandatory below.
            if (!isStudentManagementGrant(action, rule, roles)) return false;
            if ("SCHOOL".equalsIgnoreCase(action.scopeType())) return true;
            return context.classId() != null
                    && academicScopes.can("ACADEMIC_ROSTER_VIEW", context);
        }
        if (teacher && isStudentReadAction(action.code())) {
            // Teacher student visibility is never broad even during compatibility
            // backfill: an active class/student assignment is non-overridable.
            return academicScopes.can("ACADEMIC_ROSTER_VIEW", context);
        }
        if ("GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS".equals(action.code())) {
            return academicScopes.can(action.code(), context);
        }
        if (teacher && action.module().equalsIgnoreCase("academic")) {
            return academicScopes.can(action.code(), context);
        }
        if (teacher && action.module().equalsIgnoreCase("presence")
                && isAttendanceOperational(action.code())) {
            return attendanceScopes.allowsTeacher(principal, context, rule.scopeMode());
        }
        if (teacher && action.module().equalsIgnoreCase("timetable")) {
            // The teacher default is the own published schedule.  A deliberate
            // management-role grant or a dated user exception may still grant
            // a dedicated management action; a module-level fallback never can.
            if ("TIMETABLE_MY_SCHEDULE_VIEW".equals(action.code())) {
                return attendanceScopes.ownPublishedSchedule(principal, context);
            }
            return hasTimetableManagementRole(roles)
                    || (rule.source().startsWith("USER_OVERRIDE:")
                    && (rule.effectiveFrom() != null || rule.effectiveTo() != null));
        }
        if (parent && "parent".equalsIgnoreCase(action.module())) {
            return context.studentId() != null
                    && guardianAccess.canAccess(context.schoolId(), principal.userId(),
                    context.studentId(), parentFeature(action.code()), effectiveDate(context));
        }
        return true;
    }

    private boolean resourcePresent(Action action, PolicyResourceContext context) {
        if (isAttendanceOperational(action.code())) {
            return attendanceScopes.hasRequiredContext(context);
        }
        return switch (action.scopeType().toUpperCase(Locale.ROOT)) {
            case "NONE", "SCHOOL" -> context.schoolId() != null;
            case "STUDENT", "CHILD" -> context.studentId() != null;
            case "CLASS" -> context.classId() != null;
            case "CLASS_SUBJECT" -> context.classId() != null && context.subjectCode() != null
                    && !context.subjectCode().isBlank();
            case "TIMETABLE_OCCURRENCE" -> context.timetableOccurrenceId() != null;
            case "PARCOURS" -> context.parcours() != null;
            case "SELF" -> context.ownerEmployeeId() != null;
            default -> false;
        };
    }

    /**
     * NONE is a real no-resource scope, not a wildcard.  Legacy broad access
     * is represented by SCHOOL_ALL so a backfilled NONE row can never grant a
     * student, class, occurrence or self-scoped resource accidentally.
     */
    static boolean scopeCompatible(Action action, PolicyScopeMode mode) {
        String scopeType = action.scopeType().toUpperCase(Locale.ROOT);
        if (mode == PolicyScopeMode.NONE) return "NONE".equals(scopeType);
        if (mode == PolicyScopeMode.SELF) return "SELF".equals(scopeType);
        return !"NONE".equals(scopeType);
    }

    private boolean sessionInvariant(PolicyResourceContext context) {
        if (context.academicSessionId() == null) return true;
        LocalDate date = effectiveDate(context);
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM academic_session
                 WHERE id=? AND school_id=? AND start_date<=? AND end_date>=?
                """, Integer.class, context.academicSessionId(), context.schoolId(), date, date);
        return count != null && count > 0;
    }

    private boolean activeEnrollmentInvariant(Action action, PolicyResourceContext context,
                                             List<String> roles) {
        if (context.studentId() == null || context.academicSessionId() == null) return true;
        if (!"STUDENT".equalsIgnoreCase(action.scopeType())
                && !"CHILD".equalsIgnoreCase(action.scopeType())) return true;
        // Only teacher student scope is constrained by the current active
        // enrollment.  Finance/administrative student lookups may include a
        // student without a current class, while teacher access remains
        // enrollment-backed and fail-closed.
        if (!isTeacher(roles)) return true;
        LocalDate date = effectiveDate(context);
        Integer activeStudent = jdbc.queryForObject("SELECT count(*) FROM student WHERE school_id=? AND id=? AND active=true",
                Integer.class, context.schoolId(), context.studentId());
        if (activeStudent == null || activeStudent == 0) return false;
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM student_enrollment
                 WHERE school_id=? AND student_id=? AND academic_session_id=? AND status='ACTIVE'
                   AND enrolled_on<=? AND (exited_on IS NULL OR exited_on>=?)
                """, Integer.class, context.schoolId(), context.studentId(),
                context.academicSessionId(), date, date);
        if (count == null || count == 0) return false;
        if (context.classId() == null) return true;
        Integer classCount = jdbc.queryForObject("""
                SELECT count(*) FROM student_enrollment
                 WHERE school_id=? AND student_id=? AND academic_session_id=? AND status='ACTIVE'
                   AND school_class_id=? AND enrolled_on<=? AND (exited_on IS NULL OR exited_on>=?)
                """, Integer.class, context.schoolId(), context.studentId(),
                context.academicSessionId(), context.classId(), date, date);
        return classCount != null && classCount > 0;
    }

    private List<Rule> userRules(UUID userId, UUID schoolId, String action, LocalDate date) {
        return jdbc.query("""
                SELECT effect,scope_mode,scope_payload::text,effective_from,effective_to
                  FROM permission_user_action
                 WHERE school_id=? AND user_id=? AND action_code=?
                   AND (effective_from IS NULL OR effective_from<=?)
                   AND (effective_to IS NULL OR effective_to>=?)
                 ORDER BY CASE WHEN effect='DENY' THEN 0 WHEN effect='ALLOW' THEN 1 ELSE 2 END,
                          effective_from DESC NULLS LAST, created_at DESC
                """, (rs, n) -> new Rule("USER_OVERRIDE:" + userId,
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class)),
                schoolId, userId, action, date, date);
    }

    private List<Rule> roleRules(UUID schoolId, List<String> roles, String action, LocalDate date) {
        if (roles.isEmpty()) return List.of();
        String placeholders = String.join(",", roles.stream().map(x -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(schoolId);
        args.addAll(roles);
        args.add(action);
        args.add(date);
        args.add(date);
        return jdbc.query("""
                SELECT role_code,effect,scope_mode,scope_payload::text,effective_from,effective_to
                  FROM permission_role_action
                 WHERE school_id=? AND role_code IN (%s) AND action_code=?
                   AND (effective_from IS NULL OR effective_from<=?)
                   AND (effective_to IS NULL OR effective_to>=?)
                 ORDER BY CASE WHEN effect='DENY' THEN 0 ELSE 1 END,
                          effective_from DESC NULLS LAST, created_at DESC
                """.formatted(placeholders), (rs, n) -> new Rule("ROLE:" + rs.getString(1),
                        rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, LocalDate.class), rs.getObject(6, LocalDate.class)),
                args.toArray());
    }

    private List<String> activeRoles(AppUserPrincipal principal, UUID schoolId, LocalDate date) {
        return jdbc.query("""
                SELECT DISTINCT lower(role_code) AS role_code FROM app_user_role
                 WHERE school_id=? AND user_id=?
                   AND (effective_from IS NULL OR effective_from<=?)
                   AND (effective_to IS NULL OR effective_to>=?)
                UNION SELECT lower(?)
                ORDER BY role_code
                """, (rs, n) -> rs.getString(1), schoolId, principal.userId(), date, date,
                principal.roleCode());
    }

    private Action action(String code) {
        return jdbc.query("""
                SELECT code,module,scope_type,required_level
                  FROM permission_action WHERE code=? AND active=true
                """, rs -> rs.next() ? new Action(rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4)) : null, code);
    }

    private Rollout rollout(UUID schoolId) {
        return jdbc.query("""
                SELECT mode,enforcement_enabled,COALESCE(compatibility_profile_code,'')
                  FROM permission_policy_rollout WHERE school_id=?
                """, rs -> rs.next() ? new Rollout(rs.getString(1), rs.getBoolean(2), rs.getString(3))
                        : new Rollout("SAFE_DEFAULT", true, ""), schoolId);
    }

    private long policyVersion(UUID schoolId) {
        Long value = jdbc.query("SELECT version FROM school_permission_version WHERE school_id=?",
                rs -> rs.next() ? rs.getLong(1) : 0L, schoolId);
        return value == null ? 0 : value;
    }

    private boolean activeAccount(AppUserPrincipal principal, UUID schoolId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM app_user
                 WHERE id=? AND school_id=? AND active=true
                """, Integer.class, principal.userId(), schoolId);
        return count != null && count > 0;
    }

    private UUID employeeId(AppUserPrincipal principal, UUID schoolId) {
        return jdbc.query("SELECT employee_id FROM app_user WHERE id=? AND school_id=? AND active=true",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                principal.userId(), schoolId);
    }

    private UUID currentTenant(AppUserPrincipal principal) {
        if (TenantContext.isSet()) return TenantContext.get();
        return principal.schoolId();
    }

    private AppUserPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        return principal instanceof AppUserPrincipal p ? p : null;
    }

    private static LocalDate effectiveDate(PolicyResourceContext context) {
        return context.effectiveDate() == null ? LocalDate.now() : context.effectiveDate();
    }

    private static String subjectAction(Action action) {
        return switch (action.code()) {
            case "ACADEMIC_ASSESSMENT_VIEW" -> "ACADEMIC_ASSESSMENT_VIEW";
            case "ACADEMIC_ASSESSMENT_MANAGE" -> "ACADEMIC_ASSESSMENT_MANAGE";
            case "GRADE_SUBMIT" -> "GRADE_SUBMIT";
            case "GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS" ->
                    "GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS";
            default -> "ACADEMIC_SUBJECT_GRADE_VIEW";
        };
    }

    private static String parentFeature(String action) {
        return switch (action) {
            case "PARENT_ATTENDANCE_VIEW" -> "attendance";
            case "PARENT_FINANCE_VIEW" -> "finance";
            case "PARENT_DISCIPLINE_VIEW" -> "discipline";
            case "PARENT_HEALTH_VIEW" -> "health";
            case "PARENT_ACADEMIC_VIEW" -> "academic";
            case "PARENT_DOCUMENT_DOWNLOAD" -> "finance";
            default -> "summary";
        };
    }

    boolean ownerMatches(AppUserPrincipal principal, PolicyResourceContext context) {
        UUID authenticatedEmployeeId = employeeId(principal, context.schoolId());
        return authenticatedEmployeeId != null && context.ownerEmployeeId() != null
                && authenticatedEmployeeId.equals(context.ownerEmployeeId());
    }

    private boolean idInPayload(UUID id, String raw, String key) {
        if (id == null || raw == null) return false;
        try {
            JsonNode values = mapper.readTree(raw).path(key);
            for (JsonNode value : values) if (id.toString().equalsIgnoreCase(value.asText())) return true;
        } catch (Exception ignored) { }
        return false;
    }

    private boolean valueInPayload(String value, String raw, String key) {
        if (value == null || raw == null) return false;
        try {
            for (JsonNode node : mapper.readTree(raw).path(key))
                if (value.equalsIgnoreCase(node.asText())) return true;
        } catch (Exception ignored) { }
        return false;
    }

    private boolean classSubjectInPayload(PolicyResourceContext context, String raw) {
        if (context.classId() == null || context.subjectCode() == null || raw == null) return false;
        try {
            for (JsonNode node : mapper.readTree(raw).path("classSubjects")) {
                if (context.classId().toString().equalsIgnoreCase(node.path("classId").asText())
                        && context.subjectCode().equalsIgnoreCase(node.path("subjectCode").asText())) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private boolean parcoursInPayload(ParcoursContext.Scope scope, String raw) {
        if (scope == null || raw == null) return false;
        try {
            for (JsonNode node : mapper.readTree(raw).path("parcours")) {
                if (scope.level().equalsIgnoreCase(node.path("level").asText())
                        && scope.subsystem().equalsIgnoreCase(node.path("subsystem").asText())) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    static PolicyScopeMode safeMode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return PolicyScopeMode.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private boolean isParent(AppUserPrincipal principal, List<String> roles, UUID schoolId) {
        if (roles.stream().map(AuthorizationPolicyService::normalizeRole)
                .anyMatch(role -> role.equals("parent"))) return true;
        Integer linked = jdbc.queryForObject("""
                SELECT count(*) FROM guardian
                 WHERE school_id=? AND app_user_id=? AND status IN ('ACTIVE','INVITED')
                """, Integer.class, schoolId, principal.userId());
        return linked != null && linked > 0;
    }

    private boolean isParentOnly(AppUserPrincipal principal, List<String> roles, UUID schoolId) {
        if (!isParent(principal, roles, schoolId)) return false;
        return roles.stream().map(AuthorizationPolicyService::normalizeRole)
                .allMatch(role -> role.equals("parent"));
    }

    private static boolean hasTimetableManagementRole(List<String> roles) {
        return roles.stream().map(AuthorizationPolicyService::normalizeRole)
                .anyMatch(role -> Set.of("principal", "prefect", "administrator", "admin",
                        "school_admin", "timetable_manager", "academic_manager").contains(role));
    }

    private static boolean isStudentManagementGrant(Action action, Rule rule,
                                                   List<String> roles) {
        boolean managementRole = roles.stream().map(AuthorizationPolicyService::normalizeRole)
                .anyMatch(role -> Set.of("principal", "administrator", "admin", "school_admin",
                        "registrar", "student_manager", "enrollment_manager").contains(role));
        if (managementRole) return true;
        // A user exception is intentionally time-bounded for student writes;
        // the Access Control service also requires a reason and applies the
        // high-risk confirmation rules before persisting it.
        return "ALLOW".equals(rule.effect())
                && rule.source().startsWith("USER_OVERRIDE:")
                && (rule.effectiveFrom() != null || rule.effectiveTo() != null);
    }

    static boolean isTeacher(List<String> roles) {
        return roles.stream().map(AuthorizationPolicyService::normalizeRole)
                .anyMatch(role -> role.equals("teacher") || role.equals("form_teacher"));
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isStudentReadAction(String code) {
        return code.equals("STUDENT_DIRECTORY_VIEW") || code.equals("STUDENT_PROFILE_VIEW")
                || code.equals("STUDENT_PHOTO_VIEW") || code.equals("GUARDIAN_VIEW")
                || code.equals("ENROLLMENT_VIEW");
    }

    private static boolean isAttendanceOperational(String code) {
        return code.equals("ATTENDANCE_ROSTER_VIEW") || code.equals("ATTENDANCE_MARK")
                || code.equals("ATTENDANCE_FINALIZE");
    }

    private static String domainDenialCode(Action action, List<String> roles) {
        if (isTeacher(roles) && action.module().equalsIgnoreCase("academic"))
            return "ACADEMIC_SCOPE_DENIED";
        if (isTeacher(roles) && action.module().equalsIgnoreCase("presence"))
            return "ATTENDANCE_OCCURRENCE_SCOPE_DENIED";
        return "DOMAIN_INVARIANT_DENIED";
    }

    private static String domainDenialFr(Action action, List<String> roles) {
        if (isTeacher(roles) && action.module().equalsIgnoreCase("academic"))
            return "Cette ressource académique n'est pas dans votre affectation active.";
        if (isTeacher(roles) && action.module().equalsIgnoreCase("presence"))
            return "Cette présence ne correspond pas à votre titulaire daté ou à une occurrence publiée qui vous est attribuée.";
        return "Une règle métier obligatoire bloque cette action dans ce périmètre.";
    }

    private static String domainDenialEn(Action action, List<String> roles) {
        if (isTeacher(roles) && action.module().equalsIgnoreCase("academic"))
            return "This academic resource is outside your active assignment.";
        if (isTeacher(roles) && action.module().equalsIgnoreCase("presence"))
            return "This attendance does not match your dated homeroom assignment or a published occurrence assigned to you.";
        return "A required domain invariant blocks this action in the requested scope.";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static PolicyDecision deny(String action, String code, String fr, String en,
                                       long version, String repairHint) {
        return PolicyDecision.deny(action, code, fr, en, version, repairHint);
    }
}
