package com.bbc.sms.settings;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.ParcoursAccessService;
import com.bbc.sms.platform.security.PolicyDecision;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.settings.dto.AccessControlDtos.*;
import com.bbc.sms.settings.dto.SettingsDtos.RoleView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Staged policy editor backend.  A mutation replaces a complete reviewed
 * subject profile atomically, is guarded by the tenant policy version, and is
 * recorded with before/after JSON for audit and rollback review.
 */
@Service
public class AccessControlService {
    private static final Set<String> EFFECTS = Set.of("ALLOW", "DENY", "INHERIT");
    private static final Set<String> SCOPES = Set.of(
            "NONE", "SCHOOL_ALL", "PARCOURS_ALLOWED", "ASSIGNED_CLASSES",
            "TITULAIRE_CLASSES", "ASSIGNED_CLASS_SUBJECTS",
            "TIMETABLE_OCCURRENCES_ASSIGNED", "LINKED_CHILDREN", "SELF",
            "CLASS_SET", "SUBJECT_SET", "CLASS_SUBJECT_SET", "PARCOURS_SET");

    private record ActionMeta(String code, String module, String groupCode,
                              String riskLevel, String scopeType, String requiredLevel) {}

    private record PotentialGrant(boolean allowed, String scopeMode) {}

    private static final List<Set<String>> SEPARATION_OF_DUTY_CONFLICTS = List.of(
            Set.of("FEE_WAIVE_REQUEST", "FEE_WAIVE_APPROVE"),
            Set.of("REFUND_REQUEST", "REFUND_APPROVE"),
            Set.of("PAYMENT_COLLECT", "PAYMENT_REVERSE"),
            Set.of("CASHIER_SESSION_OPEN", "CASHIER_SESSION_CLOSE"),
            Set.of("CASHIER_SESSION_APPROVE", "CASHIER_SESSION_CLOSE"),
            Set.of("LEDGER_POST", "LEDGER_REVERSE"),
            Set.of("LEDGER_CLOSE", "LEDGER_REOPEN"),
            Set.of("PAYROLL_CALCULATE", "PAYROLL_APPROVE"),
            Set.of("PAYROLL_REVIEW", "PAYROLL_APPROVE"),
            Set.of("PAYROLL_APPROVE", "PAYROLL_PAY"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuthorizationPolicyService policy;
    private final ParcoursAccessService parcours;

    public AccessControlService(JdbcTemplate jdbc, ObjectMapper mapper,
                                AuthorizationPolicyService policy,
                                ParcoursAccessService parcours) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.policy = policy;
        this.parcours = parcours;
    }

    @Transactional(readOnly = true)
    public List<ActionGroupView> actionGroups() {
        Map<String, List<ActionView>> grouped = catalog().stream()
                .collect(Collectors.groupingBy(ActionView::groupCode, LinkedHashMap::new,
                        Collectors.toList()));
        return grouped.entrySet().stream()
                .map(e -> new ActionGroupView(e.getKey(), groupLabelFr(e.getKey()),
                        groupLabelEn(e.getKey()), e.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActionView> catalog() {
        return jdbc.query("""
                SELECT code,module,group_code,label_fr,label_en,description_fr,description_en,
                       risk_level,scope_type,required_level,default_read_action,display_order
                  FROM permission_action WHERE active=true ORDER BY display_order,code
                """, (rs, n) -> new ActionView(rs.getString("code"), rs.getString("module"),
                rs.getString("group_code"), rs.getString("label_fr"), rs.getString("label_en"),
                rs.getString("description_fr"), rs.getString("description_en"),
                rs.getString("risk_level"), rs.getString("scope_type"),
                rs.getString("required_level"), rs.getBoolean("default_read_action"),
                rs.getInt("display_order")));
    }

    @Transactional(readOnly = true)
    public RoleWorkspace roleWorkspace(String roleCode) {
        RoleView role = role(roleCode);
        return new RoleWorkspace(role.code(), role.labelFr(), role.labelEn(), role.builtin(),
                policyVersion(), actionGroups(), roleRules(role.code()));
    }

    @Transactional(readOnly = true)
    public List<RoleView> roles() {
        return jdbc.query("""
                SELECT code,label_fr,label_en,builtin FROM role
                 ORDER BY builtin DESC, label_fr, code
                """, (rs, n) -> new RoleView(rs.getString("code"), rs.getString("label_fr"),
                rs.getString("label_en"), rs.getBoolean("builtin")));
    }

    @Transactional(readOnly = true)
    public PolicyPreview previewRole(String roleCode, RoleMutation mutation) {
        RoleView role = role(roleCode);
        List<RuleInput> desired = validateRules(roleCode, mutation.rules(), false,
                mutation.separationOfDutiesOverride(), mutation.separationOfDutiesReason(), false);
        List<RuleView> before = roleRules(roleCode);
        return preview("ROLE", role.code(), before, desired,
                affectedUsers(role.code()), preservedUserExceptions(role.code()));
    }

    @Transactional
    public RoleWorkspace updateRole(String roleCode, RoleMutation mutation) {
        RoleView role = role(roleCode);
        assertVersion(mutation.expectedPolicyVersion());
        List<RuleView> beforeRules = roleRules(roleCode);
        List<RuleInput> desired = validateRules(roleCode, mutation.rules(), true,
                mutation.separationOfDutiesOverride(), mutation.separationOfDutiesReason(), true);
        requireHighRiskConfirmation(beforeRules, desired, mutation.confirmHighRisk());
        assertPermissionAdministratorSurvivesRoleMutation(role.code(), desired);
        String before = json(beforeRules);
        UUID actor = currentUserId();
        jdbc.update("DELETE FROM permission_role_action WHERE school_id=? AND role_code=?",
                schoolId(), role.code());
        for (RuleInput rule : desired) insertRoleRule(role.code(), rule, actor);
        String after = json(roleRules(roleCode));
        audit("ROLE_RULES_REPLACED", role.code(), null, mutation.reason(), before, after);
        return roleWorkspace(roleCode);
    }

    @Transactional(readOnly = true)
    public List<TemplateView> templates() {
        return jdbc.query("""
                SELECT code,label_fr,label_en,description_fr,description_en,base_role_code
                  FROM permission_role_template WHERE active=true ORDER BY code
                """, (rs, n) -> new TemplateView(rs.getString("code"), rs.getString("label_fr"),
                rs.getString("label_en"), rs.getString("description_fr"),
                rs.getString("description_en"), rs.getString("base_role_code"),
                templateRules(rs.getString("code"))));
    }

    @Transactional(readOnly = true)
    public PolicyPreview previewTemplate(String roleCode, String templateCode) {
        role(roleCode);
        List<RuleView> template = templateRules(templateCode);
        if (template.isEmpty()) throw ApiException.notFound("Modèle de rôle");
        List<RuleInput> desired = template.stream().map(this::toInput).toList();
        return preview("ROLE_TEMPLATE", roleCode, roleRules(roleCode), desired,
                affectedUsers(roleCode), preservedUserExceptions(roleCode));
    }

    @Transactional
    public RoleWorkspace applyTemplate(String roleCode, String templateCode,
                                       Long expectedPolicyVersion, String reason,
                                       boolean confirmHighRisk) {
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("Une justification est obligatoire");
        List<RuleView> template = templateRules(templateCode);
        if (template.isEmpty()) throw ApiException.notFound("Modèle de rôle");
        if (expectedPolicyVersion == null) throw ApiException.badRequest("La version de politique est obligatoire");
        return updateRole(roleCode, new RoleMutation(expectedPolicyVersion, reason,
                template.stream().map(this::toInput).toList(), confirmHighRisk, false, null));
    }

    @Transactional(readOnly = true)
    public List<UserSelection> users(String search) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return jdbc.query("""
                SELECT id,username,display_name,role_code,active
                  FROM app_user
                 WHERE school_id=? AND (?='' OR lower(username) LIKE ? OR lower(display_name) LIKE ?)
                 ORDER BY display_name,username
                """, (rs, n) -> new UserSelection(rs.getObject("id", UUID.class),
                rs.getString("username"), rs.getString("display_name"), rs.getString("role_code"),
                rs.getBoolean("active"), userRoles(rs.getObject("id", UUID.class))),
                schoolId(), term, "%" + term + "%", "%" + term + "%");
    }

    @Transactional(readOnly = true)
    public UserWorkspace userWorkspace(UUID userId) {
        UserSelection user = user(userId);
        return new UserWorkspace(user, policyVersion(), userRules(userId), effectiveActions(userId));
    }

    @Transactional(readOnly = true)
    public PolicyPreview previewUser(UUID userId, UserMutation mutation) {
        user(userId);
        List<RuleInput> desired = validateRules("user:" + userId, mutation.rules(), false,
                mutation.separationOfDutiesOverride(), mutation.separationOfDutiesReason(), false);
        return preview("USER", userId.toString(), userRules(userId), desired,
                List.of(user(userId)), List.of());
    }

    @Transactional
    public UserWorkspace updateUser(UUID userId, UserMutation mutation) {
        user(userId);
        assertVersion(mutation.expectedPolicyVersion());
        List<RuleView> beforeRules = userRules(userId);
        List<RuleInput> desired = validateRules("user:" + userId, mutation.rules(), true,
                mutation.separationOfDutiesOverride(), mutation.separationOfDutiesReason(), true);
        requireHighRiskConfirmation(beforeRules, desired, mutation.confirmHighRisk());
        assertPermissionAdministratorSurvivesUserMutation(userId, desired);
        String before = json(beforeRules);
        UUID actor = currentUserId();
        jdbc.update("DELETE FROM permission_user_action WHERE school_id=? AND user_id=?",
                schoolId(), userId);
        for (RuleInput rule : desired) insertUserRule(userId, rule, actor);
        String after = json(userRules(userId));
        audit("USER_RULES_REPLACED", null, userId, mutation.reason(), before, after);
        return userWorkspace(userId);
    }

    @Transactional
    public UserWorkspace updateUserRoles(UUID userId, RoleAssignmentMutation mutation) {
        user(userId);
        assertVersion(mutation.expectedPolicyVersion());
        if (mutation.assignments() == null || mutation.assignments().stream().anyMatch(Objects::isNull)) {
            throw ApiException.badRequest("Les affectations de rôle sont obligatoires");
        }
        if (mutation.assignments().stream().filter(RoleAssignmentInput::primary).count() != 1) {
            throw ApiException.badRequest("Un seul rôle principal doit être sélectionné");
        }
        for (RoleAssignmentInput input : mutation.assignments()) {
            assertRoleExists(input.roleCode());
            if (input.effectiveTo() != null && input.effectiveFrom() != null
                    && input.effectiveTo().isBefore(input.effectiveFrom())) {
                throw ApiException.badRequest("La fin du rôle précède son début");
            }
        }
        boolean beforeAdmin = permissionAdministratorForUser(userId);
        boolean afterAdmin = hasDirectPermissionManageAllow(userId)
                || mutation.assignments().stream().anyMatch(input -> roleGrantsPermissionManage(input.roleCode()));
        if ((beforeAdmin || afterAdmin) && !mutation.confirmHighRisk()) {
            throw ApiException.coded(HttpStatus.CONFLICT, "POLICY_CONFIRMATION_REQUIRED",
                    "Confirmez explicitement la modification d'un administrateur des droits.");
        }
        if (beforeAdmin && !afterAdmin) {
            if (currentUserId().equals(userId)) {
                throw ApiException.coded(HttpStatus.CONFLICT, "SELF_LOCKOUT_BLOCKED",
                        "Vous ne pouvez pas retirer votre propre dernier accès de gestion des droits.");
            }
            if (permissionAdministratorCount() <= 1) {
                throw ApiException.coded(HttpStatus.CONFLICT, "LAST_PERMISSION_ADMIN_BLOCKED",
                        "Le dernier administrateur des droits doit rester actif ou être remplacé dans la même opération.");
            }
        }
        String before = json(userRoles(userId));
        UUID actor = currentUserId();
        jdbc.update("DELETE FROM app_user_role WHERE school_id=? AND user_id=?", schoolId(), userId);
        String primary = null;
        for (RoleAssignmentInput input : mutation.assignments()) {
            assertRoleExists(input.roleCode());
            if (input.effectiveTo() != null && input.effectiveFrom() != null
                    && input.effectiveTo().isBefore(input.effectiveFrom())) {
                throw ApiException.badRequest("La fin du rôle précède son début");
            }
            if (input.primary()) primary = input.roleCode();
            jdbc.update("""
                    INSERT INTO app_user_role(school_id,user_id,role_code,is_primary,effective_from,effective_to,assigned_by,reason)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, schoolId(), userId, input.roleCode(), input.primary(), input.effectiveFrom(),
                    input.effectiveTo(), actor, input.reason().trim());
        }
        jdbc.update("UPDATE app_user SET role_code=? WHERE id=? AND school_id=?", primary, userId, schoolId());
        audit("USER_ROLES_REPLACED", null, userId, mutation.reason(), before, json(userRoles(userId)));
        return userWorkspace(userId);
    }

    @Transactional(readOnly = true)
    public CapabilityView capabilities() {
        UUID userId = currentUserId();
        String mode = parcours.scopeMode(userId);
        List<String> allowedParcours = parcours.allowed(userId).stream()
                .map(scope -> scope.level() + ":" + scope.subsystem()).toList();
        List<EffectiveActionView> actions = catalog().stream().map(action -> {
            boolean requiresContext = !Set.of("NONE", "SCHOOL").contains(action.scopeType());
            if (requiresContext) {
                PotentialGrant potential = potentialGrant(userId, action.code());
                return new EffectiveActionView(action.code(), action.labelFr(), action.labelEn(),
                        potential.allowed() ? "CONTEXT_REQUIRED" : "DENY",
                        potential.scopeMode() == null ? action.scopeType() : potential.scopeMode(),
                        potential.allowed() ? "POTENTIAL_ALLOW" : "POLICY_RULE_MISSING",
                        true, action.riskLevel());
            }
            PolicyDecision decision = policy.decide(action.code(),
                    PolicyResourceContext.empty().forSchool(schoolId()));
            return new EffectiveActionView(action.code(), action.labelFr(), action.labelEn(),
                    decision.allowed() ? "ALLOW" : "DENY", decision.matchedScope(),
                    decision.winningRuleSource() == null ? decision.denialCode() : decision.winningRuleSource(),
                    false, action.riskLevel());
        }).toList();
        return new CapabilityView(policy.currentPolicyVersion(), mode, allowedParcours, actions);
    }

    @Transactional(readOnly = true)
    public PolicyDecision contextDecision(ContextDecisionRequest request) {
        if (request == null || request.actionCode() == null || request.actionCode().isBlank()) {
            throw ApiException.badRequest("Le code d'action est obligatoire");
        }
        ParcoursContext.Scope parcoursScope = request.parcours() == null || request.parcours().isBlank()
                ? null : ParcoursContext.parse(request.parcours());
        if (request.parcours() != null && !request.parcours().isBlank() && parcoursScope == null) {
            throw ApiException.badRequest("Le parcours doit être au format niveau:système");
        }
        PolicyResourceContext context = new PolicyResourceContext(schoolId(), request.academicSessionId(),
                request.effectiveDate(), parcoursScope, request.classId(), request.subjectCode(),
                request.studentId(), request.timetableOccurrenceId(), request.documentId(), null,
                request.periodKey(), request.level());
        return policy.decide(request.actionCode(), context);
    }

    @Transactional(readOnly = true)
    public List<AuditView> audit(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbc.query("""
                SELECT id,actor_user_id,target_role_code,target_user_id,mutation_type,reason,
                       correlation_id,occurred_at
                  FROM permission_policy_audit WHERE school_id=?
                 ORDER BY occurred_at DESC LIMIT ?
                """, (rs, n) -> new AuditView(rs.getObject("id", UUID.class),
                rs.getObject("actor_user_id", UUID.class), rs.getString("target_role_code"),
                rs.getObject("target_user_id", UUID.class), rs.getString("mutation_type"),
                rs.getString("reason"), rs.getString("correlation_id"),
                rs.getObject("occurred_at", OffsetDateTime.class)), schoolId(), safeLimit);
    }

    private List<RuleInput> validateRules(String subject, List<RuleInput> rules, boolean enforceExpiry,
                                          boolean separationOfDutiesOverride,
                                          String separationOfDutiesReason,
                                          boolean strictSeparationOfDuties) {
        if (rules == null) throw ApiException.badRequest("La liste des règles est obligatoire");
        Map<String, ActionMeta> actions = catalog().stream().collect(Collectors.toMap(
                ActionView::code, a -> new ActionMeta(a.code(), a.module(), a.groupCode(),
                        a.riskLevel(), a.scopeType(), a.requiredLevel())));
        Set<String> seen = new HashSet<>();
        List<RuleInput> normalized = new ArrayList<>();
        for (RuleInput raw : rules) {
            if (raw == null) throw ApiException.badRequest("Une règle ne peut pas être vide");
            String actionCode = upper(raw.actionCode());
            String effect = upper(raw.effect());
            String scope = upper(raw.scopeMode());
            ActionMeta action = actions.get(actionCode);
            if (action == null) throw ApiException.badRequest("Action inconnue : " + actionCode);
            if (!EFFECTS.contains(effect)) throw ApiException.badRequest("Effet invalide : " + effect);
            if (!SCOPES.contains(scope)) throw ApiException.badRequest("Périmètre invalide : " + scope);
            if (!seen.add(actionCode + "|" + effect + "|" + scope + "|"
                    + String.valueOf(raw.effectiveFrom()) + "|" + String.valueOf(raw.effectiveTo()))) {
                throw ApiException.badRequest("Règle dupliquée pour : " + actionCode);
            }
            // Jackson represents an explicit JSON null as NullNode for JsonNode
            // fields. Treat that as an absent payload so the browser's explicit
            // { scopeMode: "NONE", scopePayload: null } representation remains
            // equivalent to an omitted payload.
            if ("INHERIT".equals(effect) && (hasScopePayload(raw.scopePayload()) || !"NONE".equals(scope))) {
                throw ApiException.badRequest("Une règle héritée doit avoir le périmètre Aucun");
            }
            if ("ALLOW".equals(effect) || "DENY".equals(effect)) {
                if ("NONE".equals(scope) && !"NONE".equals(action.scopeType())) {
                    throw ApiException.badRequest("Le périmètre Aucun ne peut pas couvrir " + actionCode);
                }
                if ("SELF".equals(scope) && !"SELF".equals(action.scopeType())) {
                    throw ApiException.badRequest("Le périmètre Personnel ne correspond pas à " + actionCode);
                }
                if ("NONE".equals(action.scopeType()) && !"NONE".equals(scope)) {
                    throw ApiException.badRequest("Cette action ne possède pas de ressource à borner");
                }
            }
            if (raw.effectiveTo() != null && raw.effectiveFrom() != null
                    && raw.effectiveTo().isBefore(raw.effectiveFrom())) {
                throw ApiException.badRequest("La fin de la règle précède son début");
            }
            if (raw.permanent() && raw.effectiveTo() != null) {
                throw ApiException.badRequest("Une règle permanente ne peut pas avoir de date de fin");
            }
            if (enforceExpiry && !raw.permanent()
                    && Set.of("HIGH", "CRITICAL").contains(action.riskLevel())
                    && raw.effectiveTo() == null) {
                throw ApiException.badRequest("Une règle sensible doit avoir une date de fin ou être permanente");
            }
            if ("parent".equalsIgnoreCase(subject) && "ALLOW".equals(effect)
                    && !"parent".equalsIgnoreCase(action.module())) {
                throw ApiException.badRequest("Le portail parent ne peut pas recevoir une action du personnel");
            }
            if (raw.reason() == null || raw.reason().isBlank()) {
                throw ApiException.badRequest("Chaque règle doit avoir une justification");
            }
            normalized.add(new RuleInput(actionCode, effect, scope,
                    hasScopePayload(raw.scopePayload()) ? raw.scopePayload() : null,
                    raw.effectiveFrom(), raw.effectiveTo(), raw.permanent(), raw.reason().trim()));
        }
        List<Set<String>> conflicts = separationOfDutiesConflicts(normalized);
        if (separationOfDutiesOverride
                && (separationOfDutiesReason == null || separationOfDutiesReason.isBlank())) {
            throw ApiException.badRequest("La justification de l'exception de séparation des tâches est obligatoire");
        }
        if (strictSeparationOfDuties && !conflicts.isEmpty() && !separationOfDutiesOverride) {
            throw ApiException.coded(HttpStatus.CONFLICT, "FINANCE_SEPARATION_OF_DUTIES",
                    "Ces actions financières créent un conflit de séparation des tâches. Documentez une exception explicite.");
        }
        return normalized;
    }

    private static boolean hasScopePayload(JsonNode payload) {
        return payload != null && !payload.isNull();
    }

    private PolicyPreview preview(String subjectType, String subjectCode,
                                  List<RuleView> before, List<RuleInput> after,
                                  List<UserSelection> affectedUsers,
                                  List<RuleView> preservedUserExceptions) {
        Map<String, RuleView> beforeByAction = collapse(before);
        Map<String, RuleInput> afterByAction = after.stream().collect(Collectors.toMap(
                RuleInput::actionCode, x -> x, (a, b) -> b, LinkedHashMap::new));
        Map<String, ActionView> actions = catalog().stream().collect(Collectors.toMap(ActionView::code, x -> x));
        Set<String> codes = new LinkedHashSet<>();
        codes.addAll(beforeByAction.keySet());
        codes.addAll(afterByAction.keySet());
        List<PreviewChange> changes = codes.stream().sorted().filter(code -> {
            RuleView old = beforeByAction.get(code);
            RuleInput next = afterByAction.get(code);
            return !Objects.equals(old == null ? "INHERIT" : old.effect(), next == null ? "INHERIT" : next.effect())
                    || !Objects.equals(old == null ? "NONE" : old.scopeMode(), next == null ? "NONE" : next.scopeMode());
        }).map(code -> {
            RuleView old = beforeByAction.get(code);
            RuleInput next = afterByAction.get(code);
            ActionView action = actions.get(code);
            return new PreviewChange(code, old == null ? "INHERIT" : old.effect(),
                    next == null ? "INHERIT" : next.effect(), old == null ? "NONE" : old.scopeMode(),
                    next == null ? "NONE" : next.scopeMode(), action == null ? "LOW" : action.riskLevel(),
                    old == null ? "ADDITION" : next == null ? "REMOVAL" : "CHANGE");
        }).toList();
        List<RiskWarning> warnings = new ArrayList<>();
        for (RuleInput input : after) {
            ActionView action = actions.get(input.actionCode());
            if (action == null) continue;
            if (Set.of("HIGH", "CRITICAL").contains(action.riskLevel())) {
                warnings.add(new RiskWarning("SENSITIVE_ACTION", action.riskLevel(),
                        "Cette action sensible doit rester limitée et justifiée.",
                        "This sensitive action must remain limited and justified."));
            }
            if ("SCHOOL_ALL".equals(input.scopeMode())) {
                warnings.add(new RiskWarning("SCHOOL_WIDE_SCOPE", "HIGH",
                        "Cette règle couvre tout l’établissement.",
                        "This rule covers the whole school."));
            }
        }
        if (!separationOfDutiesConflicts(after).isEmpty()) {
            warnings.add(new RiskWarning("FINANCE_SEPARATION_OF_DUTIES", "CRITICAL",
                    "Cette combinaison sépare mal la demande, l'approbation ou la clôture financière.",
                    "This combination conflicts with request, approval, reversal or close duties."));
        }
        if (!preservedUserExceptions.isEmpty()) {
            warnings.add(new RiskWarning("USER_EXCEPTIONS_PRESERVED", "MEDIUM",
                    "Les exceptions explicites des utilisateurs concernés seront conservées.",
                    "Explicit user exceptions for affected users will be preserved."));
        }
        return new PolicyPreview(subjectType, subjectCode, policyVersion(), changes,
                warnings.stream().distinct().toList(), warnings.stream().anyMatch(w ->
                        Set.of("HIGH", "CRITICAL").contains(w.severity())),
                affectedUsers == null ? List.of() : affectedUsers,
                preservedUserExceptions == null ? List.of() : preservedUserExceptions);
    }

    private List<Set<String>> separationOfDutiesConflicts(List<RuleInput> rules) {
        Set<String> allowed = rules.stream()
                .filter(rule -> "ALLOW".equals(rule.effect()))
                .map(RuleInput::actionCode)
                .collect(Collectors.toSet());
        return SEPARATION_OF_DUTY_CONFLICTS.stream()
                .filter(pair -> pair.stream().allMatch(allowed::contains))
                .toList();
    }

    private void requireHighRiskConfirmation(List<RuleView> before, List<RuleInput> after,
                                             boolean confirmed) {
        Map<String, ActionView> actions = catalog().stream()
                .collect(Collectors.toMap(ActionView::code, x -> x));
        boolean highRisk = before.stream().anyMatch(rule -> isHighRisk(actions.get(rule.actionCode())))
                || after.stream().anyMatch(rule -> isHighRisk(actions.get(rule.actionCode())));
        if (highRisk && !confirmed) {
            throw ApiException.coded(HttpStatus.CONFLICT, "POLICY_CONFIRMATION_REQUIRED",
                    "Confirmez explicitement les changements sensibles après avoir vérifié la prévisualisation.");
        }
    }

    private boolean isHighRisk(ActionView action) {
        return action != null && Set.of("HIGH", "CRITICAL").contains(action.riskLevel());
    }

    private boolean allowsAction(List<RuleInput> rules, String actionCode) {
        boolean denied = rules.stream().anyMatch(rule -> actionCode.equals(rule.actionCode())
                && "DENY".equals(rule.effect()));
        return !denied && rules.stream().anyMatch(rule -> actionCode.equals(rule.actionCode())
                && "ALLOW".equals(rule.effect()));
    }

    private void assertPermissionAdministratorSurvivesRoleMutation(String roleCode,
                                                                   List<RuleInput> desired) {
        if (!roleGrantsPermissionManage(roleCode) || allowsAction(desired, "PERMISSION_MANAGE")) return;
        UUID actor = currentUserId();
        if (hasActiveRole(actor, roleCode) && !hasDirectPermissionManageAllow(actor)) {
            throw ApiException.coded(HttpStatus.CONFLICT, "SELF_LOCKOUT_BLOCKED",
                    "Cette modification vous retirerait le dernier accès de gestion des droits.");
        }
        if (permissionAdministratorCount() <= 1 && !roleUsersHaveDirectAdminOverride(roleCode)) {
            throw ApiException.coded(HttpStatus.CONFLICT, "LAST_PERMISSION_ADMIN_BLOCKED",
                    "Le dernier administrateur des droits doit rester actif ou être remplacé dans la même opération.");
        }
    }

    private void assertPermissionAdministratorSurvivesUserMutation(UUID userId,
                                                                    List<RuleInput> desired) {
        if (!permissionAdministratorForUser(userId)) return;
        boolean remains = allowsAction(desired, "PERMISSION_MANAGE")
                || roleGrantsPermissionManageForUser(userId);
        if (remains) return;
        if (currentUserId().equals(userId)) {
            throw ApiException.coded(HttpStatus.CONFLICT, "SELF_LOCKOUT_BLOCKED",
                    "Vous ne pouvez pas retirer votre propre dernier accès de gestion des droits.");
        }
        if (permissionAdministratorCount() <= 1) {
            throw ApiException.coded(HttpStatus.CONFLICT, "LAST_PERMISSION_ADMIN_BLOCKED",
                    "Le dernier administrateur des droits doit rester actif ou être remplacé dans la même opération.");
        }
    }

    private boolean roleGrantsPermissionManage(String roleCode) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM permission_role_action
                 WHERE school_id=? AND role_code=? AND action_code='PERMISSION_MANAGE'
                   AND effect='ALLOW'
                   AND (effective_from IS NULL OR effective_from<=current_date)
                   AND (effective_to IS NULL OR effective_to>=current_date)
                """, Integer.class, schoolId(), roleCode);
        return count != null && count > 0;
    }

    private boolean roleGrantsPermissionManageForUser(UUID userId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM permission_role_action r
                 WHERE r.school_id=? AND r.action_code='PERMISSION_MANAGE' AND r.effect='ALLOW'
                   AND (r.effective_from IS NULL OR r.effective_from<=current_date)
                   AND (r.effective_to IS NULL OR r.effective_to>=current_date)
                   AND (r.role_code=(SELECT role_code FROM app_user WHERE id=? AND school_id=?)
                        OR EXISTS (SELECT 1 FROM app_user_role ur
                                   WHERE ur.school_id=r.school_id AND ur.user_id=?
                                     AND ur.role_code=r.role_code
                                     AND (ur.effective_from IS NULL OR ur.effective_from<=current_date)
                                     AND (ur.effective_to IS NULL OR ur.effective_to>=current_date)))
                """, Integer.class, schoolId(), userId, schoolId(), userId);
        return count != null && count > 0;
    }

    private boolean permissionAdministratorForUser(UUID userId) {
        return hasDirectPermissionManageAllow(userId) || roleGrantsPermissionManageForUser(userId);
    }

    private boolean hasDirectPermissionManageAllow(UUID userId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM permission_user_action
                 WHERE school_id=? AND user_id=? AND action_code='PERMISSION_MANAGE'
                   AND effect='ALLOW'
                   AND (effective_from IS NULL OR effective_from<=current_date)
                   AND (effective_to IS NULL OR effective_to>=current_date)
                """, Integer.class, schoolId(), userId);
        return count != null && count > 0;
    }

    private boolean hasActiveRole(UUID userId, String roleCode) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM app_user u
                 WHERE u.school_id=? AND u.id=?
                   AND (lower(u.role_code)=lower(?) OR EXISTS
                        (SELECT 1 FROM app_user_role ur
                         WHERE ur.school_id=? AND ur.user_id=? AND lower(ur.role_code)=lower(?)
                           AND (ur.effective_from IS NULL OR ur.effective_from<=current_date)
                           AND (ur.effective_to IS NULL OR ur.effective_to>=current_date)))
                """, Integer.class, schoolId(), userId, roleCode, schoolId(), userId, roleCode);
        return count != null && count > 0;
    }

    private int permissionAdministratorCount() {
        Integer count = jdbc.queryForObject("""
                SELECT count(DISTINCT u.id) FROM app_user u
                 WHERE u.school_id=? AND u.active=true
                   AND (EXISTS (SELECT 1 FROM permission_user_action ua
                                WHERE ua.school_id=u.school_id AND ua.user_id=u.id
                                  AND ua.action_code='PERMISSION_MANAGE' AND ua.effect='ALLOW'
                                  AND (ua.effective_from IS NULL OR ua.effective_from<=current_date)
                                  AND (ua.effective_to IS NULL OR ua.effective_to>=current_date))
                        OR EXISTS (SELECT 1 FROM permission_role_action ra
                                   WHERE ra.school_id=u.school_id AND ra.action_code='PERMISSION_MANAGE'
                                     AND ra.effect='ALLOW'
                                     AND (ra.role_code=u.role_code OR EXISTS
                                          (SELECT 1 FROM app_user_role ur
                                           WHERE ur.school_id=u.school_id AND ur.user_id=u.id
                                             AND ur.role_code=ra.role_code
                                             AND (ur.effective_from IS NULL OR ur.effective_from<=current_date)
                                             AND (ur.effective_to IS NULL OR ur.effective_to>=current_date)))
                                     AND (ra.effective_from IS NULL OR ra.effective_from<=current_date)
                                     AND (ra.effective_to IS NULL OR ra.effective_to>=current_date)))
                   AND NOT EXISTS (SELECT 1 FROM permission_user_action deny
                                   WHERE deny.school_id=u.school_id AND deny.user_id=u.id
                                     AND deny.action_code='PERMISSION_MANAGE' AND deny.effect='DENY'
                                     AND (deny.effective_from IS NULL OR deny.effective_from<=current_date)
                                     AND (deny.effective_to IS NULL OR deny.effective_to>=current_date))
                """, Integer.class, schoolId());
        return count == null ? 0 : count;
    }

    private boolean roleUsersHaveDirectAdminOverride(String roleCode) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM app_user u
                 WHERE u.school_id=? AND u.active=true
                   AND (lower(u.role_code)=lower(?) OR EXISTS
                        (SELECT 1 FROM app_user_role ur WHERE ur.school_id=u.school_id
                         AND ur.user_id=u.id AND lower(ur.role_code)=lower(?)
                         AND (ur.effective_from IS NULL OR ur.effective_from<=current_date)
                         AND (ur.effective_to IS NULL OR ur.effective_to>=current_date)))
                   AND EXISTS (SELECT 1 FROM permission_user_action ua
                               WHERE ua.school_id=u.school_id AND ua.user_id=u.id
                                 AND ua.action_code='PERMISSION_MANAGE' AND ua.effect='ALLOW'
                                 AND (ua.effective_from IS NULL OR ua.effective_from<=current_date)
                                 AND (ua.effective_to IS NULL OR ua.effective_to>=current_date))
                """, Integer.class, schoolId(), roleCode, roleCode);
        return count != null && count > 0;
    }

    private List<UserSelection> affectedUsers(String roleCode) {
        return jdbc.query("""
                SELECT id,username,display_name,role_code,active FROM app_user u
                 WHERE u.school_id=? AND (lower(u.role_code)=lower(?) OR EXISTS
                       (SELECT 1 FROM app_user_role ur WHERE ur.school_id=u.school_id
                        AND ur.user_id=u.id AND lower(ur.role_code)=lower(?)
                        AND (ur.effective_from IS NULL OR ur.effective_from<=current_date)
                        AND (ur.effective_to IS NULL OR ur.effective_to>=current_date)))
                 ORDER BY display_name,username
                """, (rs, n) -> new UserSelection(rs.getObject("id", UUID.class),
                rs.getString("username"), rs.getString("display_name"), rs.getString("role_code"),
                rs.getBoolean("active"), userRoles(rs.getObject("id", UUID.class))),
                schoolId(), roleCode, roleCode);
    }

    private List<RuleView> preservedUserExceptions(String roleCode) {
        return rules("""
                SELECT ua.id,ua.user_id,ua.action_code,ua.effect,ua.scope_mode,ua.scope_payload::text,
                       ua.effective_from,ua.effective_to,ua.is_permanent,ua.reason,ua.version
                  FROM permission_user_action ua
                 WHERE ua.school_id=? AND EXISTS
                       (SELECT 1 FROM app_user u
                         WHERE u.school_id=ua.school_id AND u.id=ua.user_id
                           AND (lower(u.role_code)=lower(?) OR EXISTS
                                (SELECT 1 FROM app_user_role ur WHERE ur.school_id=u.school_id
                                 AND ur.user_id=u.id AND lower(ur.role_code)=lower(?)
                                 AND (ur.effective_from IS NULL OR ur.effective_from<=current_date)
                                 AND (ur.effective_to IS NULL OR ur.effective_to>=current_date))))
                 ORDER BY ua.user_id,ua.action_code,ua.created_at
                """, "USER", schoolId(), roleCode, roleCode);
    }

    private Map<String, RuleView> collapse(List<RuleView> rules) {
        Map<String, RuleView> result = new LinkedHashMap<>();
        for (RuleView rule : rules) {
            if ("DENY".equals(rule.effect()) || !result.containsKey(rule.actionCode())) result.put(rule.actionCode(), rule);
        }
        return result;
    }

    private List<EffectiveActionView> effectiveActions(UUID userId) {
        List<RuleView> overrides = userRules(userId);
        List<RuleView> roleRows = roleRulesForUser(userId);
        Map<String, ActionView> actions = catalog().stream().collect(Collectors.toMap(ActionView::code, x -> x));
        return actions.values().stream().map(action -> {
            RuleView selected = overrides.stream().filter(r -> r.actionCode().equals(action.code())
                    && !"INHERIT".equals(r.effect())).filter(r -> "DENY".equals(r.effect())).findFirst()
                    .orElseGet(() -> overrides.stream().filter(r -> r.actionCode().equals(action.code())
                            && "ALLOW".equals(r.effect())).findFirst().orElse(null));
            if (selected == null) {
                selected = roleRows.stream().filter(r -> r.actionCode().equals(action.code())
                        && "DENY".equals(r.effect())).findFirst().orElseGet(() -> roleRows.stream()
                        .filter(r -> r.actionCode().equals(action.code()) && "ALLOW".equals(r.effect()))
                        .findFirst().orElse(null));
            }
            return new EffectiveActionView(action.code(), action.labelFr(), action.labelEn(),
                    selected == null ? "INHERIT" : selected.effect(),
                    selected == null ? "NONE" : selected.scopeMode(),
                    selected == null ? "NONE" : selected.subjectType() + ":" + selected.subjectCode(),
                    !Set.of("NONE", "SCHOOL").contains(action.scopeType()), action.riskLevel());
        }).toList();
    }

    private List<RuleView> roleRulesForUser(UUID userId) {
        List<String> roles = userRoles(userId);
        if (roles.isEmpty()) return List.of();
        String placeholders = String.join(",", roles.stream().map(x -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(schoolId()); args.addAll(roles);
        args.add(LocalDate.now()); args.add(LocalDate.now());
        return rules("""
                SELECT id,role_code,action_code,effect,scope_mode,scope_payload::text,
                       effective_from,effective_to,is_permanent,reason,version
                  FROM permission_role_action
                 WHERE school_id=? AND role_code IN (%s)
                   AND (effective_from IS NULL OR effective_from<=?)
                   AND (effective_to IS NULL OR effective_to>=?)
                 ORDER BY action_code,effect DESC,created_at
                """.formatted(placeholders), "ROLE", args.toArray());
    }

    private PotentialGrant potentialGrant(UUID userId, String actionCode) {
        List<String> roles = userRoles(userId);
        List<String> modes = jdbc.query("""
                SELECT scope_mode FROM permission_user_action
                 WHERE school_id=? AND user_id=? AND action_code=? AND effect='ALLOW'
                   AND (effective_from IS NULL OR effective_from<=current_date)
                   AND (effective_to IS NULL OR effective_to>=current_date)
                 ORDER BY effective_from DESC NULLS LAST,created_at DESC
                """, (rs, n) -> rs.getString(1), schoolId(), userId, actionCode);
        if (!roles.isEmpty()) {
            String placeholders = String.join(",", roles.stream().map(x -> "?").toList());
            List<Object> args = new ArrayList<>();
            args.add(schoolId()); args.addAll(roles); args.add(actionCode);
            args.add(LocalDate.now()); args.add(LocalDate.now());
            modes.addAll(jdbc.query("""
                    SELECT scope_mode FROM permission_role_action
                     WHERE school_id=? AND role_code IN (%s) AND action_code=? AND effect='ALLOW'
                       AND (effective_from IS NULL OR effective_from<=?)
                       AND (effective_to IS NULL OR effective_to>=?)
                     ORDER BY effective_from DESC NULLS LAST,created_at DESC
                    """.formatted(placeholders), (rs, n) -> rs.getString(1), args.toArray()));
        }
        return modes.isEmpty() ? new PotentialGrant(false, null)
                : new PotentialGrant(true, modes.getFirst());
    }

    private List<RuleView> roleRules(String roleCode) {
        return rules("""
                SELECT id,role_code,action_code,effect,scope_mode,scope_payload::text,
                       effective_from,effective_to,is_permanent,reason,version
                  FROM permission_role_action WHERE school_id=? AND role_code=?
                 ORDER BY action_code,effective_from NULLS FIRST,created_at
                """, "ROLE", schoolId(), roleCode);
    }

    private List<RuleView> userRules(UUID userId) {
        return rules("""
                SELECT id,user_id,action_code,effect,scope_mode,scope_payload::text,
                       effective_from,effective_to,is_permanent,reason,version
                  FROM permission_user_action WHERE school_id=? AND user_id=?
                 ORDER BY action_code,effective_from NULLS FIRST,created_at
                """, "USER", schoolId(), userId);
    }

    private List<RuleView> templateRules(String templateCode) {
        return rules("""
                SELECT id,template_code,action_code,effect,scope_mode,scope_payload::text,
                       effective_from,effective_to,is_permanent,reason,0::bigint AS version
                  FROM permission_role_template_rule WHERE template_code=?
                 ORDER BY display_order,action_code
                """, "TEMPLATE", templateCode);
    }

    private List<RuleView> rules(String sql, String subjectType, Object... args) {
        return jdbc.query(sql, (rs, n) -> new RuleView(rs.getObject("id", UUID.class),
                subjectType, subjectValue(rs), rs.getString("action_code"), rs.getString("effect"),
                rs.getString("scope_mode"), parse(rs.getString("scope_payload")),
                rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class),
                rs.getBoolean("is_permanent"), rs.getString("reason"), rs.getLong("version")), args);
    }

    private String subjectValue(java.sql.ResultSet rs) throws java.sql.SQLException {
        for (String column : List.of("role_code", "user_id", "template_code")) {
            try {
                Object value = rs.getObject(column);
                if (value != null) return value.toString();
            } catch (java.sql.SQLException ignored) { }
        }
        return "";
    }

    private void insertRoleRule(String roleCode, RuleInput rule, UUID actor) {
        jdbc.update("""
                INSERT INTO permission_role_action
                    (school_id,role_code,action_code,effect,scope_mode,scope_payload,effective_from,effective_to,
                     is_permanent,reason,created_by,updated_by)
                VALUES (?,?,?,?,?,CAST(? AS jsonb),?,?,?,?,?,?)
                """, schoolId(), roleCode, rule.actionCode(), rule.effect(), rule.scopeMode(),
                json(rule.scopePayload()), rule.effectiveFrom(), rule.effectiveTo(), rule.permanent(),
                rule.reason(), actor, actor);
    }

    private void insertUserRule(UUID userId, RuleInput rule, UUID actor) {
        jdbc.update("""
                INSERT INTO permission_user_action
                    (school_id,user_id,action_code,effect,scope_mode,scope_payload,effective_from,effective_to,
                     is_permanent,reason,created_by,updated_by)
                VALUES (?,?,?,?,?,CAST(? AS jsonb),?,?,?,?,?,?)
                """, schoolId(), userId, rule.actionCode(), rule.effect(), rule.scopeMode(),
                json(rule.scopePayload()), rule.effectiveFrom(), rule.effectiveTo(), rule.permanent(),
                rule.reason(), actor, actor);
    }

    private RoleView role(String code) {
        List<RoleView> rows = jdbc.query("""
                SELECT code,label_fr,label_en,builtin FROM role WHERE code=?
                """, (rs, n) -> new RoleView(rs.getString("code"), rs.getString("label_fr"),
                rs.getString("label_en"), rs.getBoolean("builtin")), code);
        if (rows.isEmpty()) throw ApiException.notFound("Rôle");
        return rows.getFirst();
    }

    private UserSelection user(UUID id) {
        List<UserSelection> rows = jdbc.query("""
                SELECT id,username,display_name,role_code,active FROM app_user
                 WHERE school_id=? AND id=?
                """, (rs, n) -> new UserSelection(rs.getObject("id", UUID.class), rs.getString("username"),
                rs.getString("display_name"), rs.getString("role_code"), rs.getBoolean("active"),
                userRoles(rs.getObject("id", UUID.class))), schoolId(), id);
        if (rows.isEmpty()) throw ApiException.notFound("Utilisateur");
        return rows.getFirst();
    }

    private List<String> userRoles(UUID id) {
        return jdbc.query("""
                SELECT role_code FROM (
                    SELECT role_code,is_primary FROM app_user_role
                     WHERE school_id=? AND user_id=?
                       AND (effective_from IS NULL OR effective_from<=current_date)
                       AND (effective_to IS NULL OR effective_to>=current_date)
                    UNION ALL
                    SELECT role_code,true AS is_primary FROM app_user
                     WHERE school_id=? AND id=? AND active=true
                ) roles
                GROUP BY role_code
                ORDER BY bool_or(is_primary) DESC,role_code
                """, (rs, n) -> rs.getString(1), schoolId(), id, schoolId(), id);
    }

    private void assertRoleExists(String code) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM role WHERE code=?", Integer.class, code);
        if (count == null || count == 0) throw ApiException.badRequest("Rôle inconnu : " + code);
    }

    private void assertVersion(long expected) {
        long current = policyVersion();
        if (expected != current) {
            throw ApiException.coded(HttpStatus.CONFLICT, "POLICY_VERSION_CONFLICT",
                    "La politique a changé. Rechargez la prévisualisation avant de confirmer.");
        }
    }

    private long policyVersion() {
        Long version = jdbc.query("SELECT version FROM school_permission_version WHERE school_id=?",
                rs -> rs.next() ? rs.getLong(1) : 0L, schoolId());
        return version == null ? 0L : version;
    }

    private void audit(String mutationType, String targetRole, UUID targetUser, String reason,
                       String before, String after) {
        jdbc.update("""
                INSERT INTO permission_policy_audit
                    (school_id,actor_user_id,target_role_code,target_user_id,mutation_type,reason,before_state,after_state,correlation_id)
                VALUES (?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?)
                """, schoolId(), currentUserId(), targetRole, targetUser, mutationType,
                reason.trim(), before, after, UUID.randomUUID().toString());
    }

    private UUID schoolId() {
        AppUserPrincipal principal = currentPrincipal();
        if (com.bbc.sms.platform.tenant.TenantContext.isSet()) return com.bbc.sms.platform.tenant.TenantContext.get();
        if (principal != null) return principal.schoolId();
        throw ApiException.coded(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentification requise.");
    }

    private UUID currentUserId() {
        AppUserPrincipal principal = currentPrincipal();
        if (principal == null) throw ApiException.coded(HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED", "Authentification requise.");
        return principal.userId();
    }

    private AppUserPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        return principal instanceof AppUserPrincipal p ? p : null;
    }

    private String json(Object value) {
        try {
            return value == null ? null : mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw ApiException.coded(HttpStatus.INTERNAL_SERVER_ERROR, "POLICY_AUDIT_SERIALIZATION_FAILED",
                    "La politique ne peut pas être journalisée.");
        }
    }

    private JsonNode parse(String value) {
        if (value == null || value.isBlank()) return null;
        try { return mapper.readTree(value); }
        catch (Exception ex) { return null; }
    }

    private RuleInput toInput(RuleView rule) {
        return new RuleInput(rule.actionCode(), rule.effect(), rule.scopeMode(), rule.scopePayload(),
                rule.effectiveFrom(), rule.effectiveTo(), rule.permanent(), rule.reason());
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String groupLabelFr(String group) {
        return switch (group) {
            case "Students" -> "Élèves et familles";
            case "Academic" -> "Pédagogie";
            case "Attendance" -> "Présences";
            case "Timetable" -> "Emploi du temps";
            case "Settings" -> "Configuration et accès";
            case "Finance" -> "Finance et paie";
            case "Parent" -> "Portail parent";
            default -> "Autres fonctionnalités";
        };
    }

    private static String groupLabelEn(String group) {
        return switch (group) {
            case "Students" -> "Students and families";
            case "Academic" -> "Academic";
            case "Attendance" -> "Attendance";
            case "Timetable" -> "Timetable";
            case "Settings" -> "Configuration and access";
            case "Finance" -> "Finance and payroll";
            case "Parent" -> "Parent portal";
            default -> "Other features";
        };
    }
}
