package com.bbc.sms.staff;

import com.bbc.sms.hr.Department;
import com.bbc.sms.hr.DepartmentRepository;
import com.bbc.sms.identity.AppUser;
import com.bbc.sms.identity.AppUserRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.security.SectionRoles;
import com.bbc.sms.platform.mail.MailService;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.SetupService;
import com.bbc.sms.staff.dto.StaffDtos.*;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StaffService {

    private static final Map<String, String> ROLE_ALIASES = Map.ofEntries(
            Map.entry("surveillant", "prefect"),
            Map.entry("supervisor", "prefect"),
            Map.entry("prefet", "prefect"),
            Map.entry("cashier", "econome"),
            Map.entry("caissier", "econome"),
            Map.entry("bursar", "econome"),
            Map.entry("enseignant", "teacher"),
            Map.entry("prof", "teacher"),
            Map.entry("enseignant secondaire", "secondary_teacher"),
            Map.entry("secondary teacher", "secondary_teacher"),
            Map.entry("pp", "form_teacher"),
            Map.entry("professeur principal", "form_teacher"),
            Map.entry("proviseur", "principal"),
            Map.entry("directeur", "principal")
    );

    private final EmployeeRepository repo;
    private final DepartmentRepository departments;
    private final MailService mail;
    private final StaffAccountService accounts;
    private final AppUserRepository users;
    private final SchoolClassRepository classes;
    private final SetupService setup;
    private final TeacherScopeService accessScope;
    private final TeacherScopeService teacherScope;
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicyService policy;

    public StaffService(EmployeeRepository repo, DepartmentRepository departments, MailService mail,
                        StaffAccountService accounts, AppUserRepository users,
                        SchoolClassRepository classes, SetupService setup, JdbcTemplate jdbc,
                        AuthorizationPolicyService policy, TeacherScopeService teacherScope) {
        this.repo = repo;
        this.departments = departments;
        this.mail = mail;
        this.accounts = accounts;
        this.users = users;
        this.classes = classes;
        this.setup = setup;
        this.accessScope = teacherScope;
        this.teacherScope = teacherScope;
        this.jdbc = jdbc;
        this.policy = policy;
    }

    // ---- Classes d'un enseignant ------------------------------------------
    // Le même lien qu'à l'écran des classes (teacher_class), pris par l'autre
    // bout : depuis la fiche de l'employé plutôt que classe par classe.

    @Transactional(readOnly = true)
    public List<TeacherClassView> classesOf(UUID employeeId) {
        requireSchool("HR_VIEW");
        UUID schoolId = TenantContext.get();
        find(employeeId);
        return jdbc.query("""
                SELECT c.id, c.name, c.level, c.subsystem, s.label AS section_label,
                       (SELECT count(*) FROM student st WHERE st.class_id = c.id AND st.active) AS students
                  FROM teacher_class tc
                  JOIN school_class c ON c.id = tc.class_id
                  LEFT JOIN section s ON s.id = c.section_id
                 WHERE tc.employee_id = ? AND c.school_id = ?
                 ORDER BY c.name
                """,
                (rs, n) -> new TeacherClassView(UUID.fromString(rs.getString("id")), rs.getString("name"),
                        rs.getString("level"), rs.getString("subsystem"), rs.getString("section_label"),
                        rs.getInt("students")),
                employeeId, schoolId);
    }

    /**
     * Remplace la liste des classes de l'enseignant. Une classe décochée est
     * retirée, une liste vide le détache de toutes. Chaque classe doit relever
     * de sa section — sinon l'appel échoue en bloc, sans rien modifier.
     */
    @Transactional
    public List<TeacherClassView> setClasses(UUID employeeId, List<UUID> classIds) {
        UUID schoolId = TenantContext.get();
        policy.require("HR_MANAGE", new PolicyResourceContext(schoolId, null, java.time.LocalDate.now(),
                null, null, null, null, null, null, null, null, null));
        Employee e = find(employeeId);
        List<UUID> wanted = classIds == null ? List.of() : classIds.stream().distinct().toList();

        // Tout valider AVANT d'écrire : pas de suppression suivie d'un refus.
        for (UUID classId : wanted) {
            SchoolClass c = classes.findByIdAndSchoolId(classId, schoolId)
                    .orElseThrow(() -> ApiException.badRequest("Classe inconnue"));
            policy.require("TEACHING_CLASS_ASSIGNMENT_MANAGE",
                    new PolicyResourceContext(schoolId, null, java.time.LocalDate.now(), null,
                            classId, null, null, null, null, null, null, c.getLevel()));
            setup.bindTeacherSection(e.getId(), c.getLevel());
        }
        jdbc.update("DELETE FROM teacher_class tc USING school_class c "
                  + "WHERE tc.class_id = c.id AND tc.employee_id = ? AND c.school_id = ?",
                employeeId, schoolId);
        for (UUID classId : wanted) {
            jdbc.update("INSERT INTO teacher_class (employee_id, class_id) VALUES (?, ?)", employeeId, classId);
        }
        // Class assignment is the authoritative source for a teacher's
        // parcours envelope. Keep the linked login in assignment-derived mode
        // so @parcours.allows() can expose exactly the assigned class scopes;
        // an empty assignment remains restrictive because it derives no rows.
        jdbc.update("UPDATE app_user SET parcours_scope_mode='ASSIGNMENT_DERIVED' "
                  + "WHERE school_id=? AND employee_id=?", schoolId, employeeId);
        return classesOf(employeeId);
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> list() {
        requireSchool("HR_VIEW");
        UUID schoolId = TenantContext.get();
        Map<UUID, String> deptNames = new HashMap<>();
        for (Department d : departments.findBySchoolIdOrderByName(schoolId)) deptNames.put(d.getId(), d.getName());
        Map<UUID, AppUser> loginAccounts = loginAccounts(schoolId);
        String section = teacherScope.adminSection();
        return repo.findBySchoolIdAndActiveTrueOrderByNameAsc(schoolId).stream()
                .filter(e -> inSection(e, section))
                .map(e -> toView(e, deptNames.get(e.getDepartmentId()), loginAccounts.get(e.getId()))).toList();
    }

    /**
     * L'employé relève-t-il de la section de l'administrateur courant ?
     *
     * <p>Le personnel sans section — économat, intendance, direction — reste
     * visible de tous : il ne dépend d'aucun cycle, et le masquer le rendrait
     * introuvable pour les admins de section, qui traitent pourtant ses congés
     * et ses remplacements.
     */
    private static boolean inSection(Employee e, String section) {
        return section == null || e.getLevel() == null || section.equals(e.getLevel());
    }

    @Transactional(readOnly = true)
    public EmployeeView get(UUID id) {
        requireSchool("HR_VIEW");
        return toView(find(id));
    }

    @Transactional
    public EmployeeView create(EmployeeUpsert in) {
        requireSchool("HR_MANAGE");
        UUID schoolId = TenantContext.get();
        Employee e = new Employee();
        e.setSchoolId(schoolId);
        e.setCode(nextCode(schoolId));
        apply(e, in);
        e.setInitials(initials(in.name()));
        Employee saved = repo.save(e);
        // When a login account is requested, the UI follows up with reset-credentials
        // (which e-mails the actual credentials and reports delivery), so skip the vague
        // courtesy notice here to avoid sending the employee two e-mails. Otherwise send
        // the fire-and-forget notice (no-op unless SMTP is configured).
        if (!Boolean.TRUE.equals(in.createLogin())) {
            mail.notifyUserCreated(schoolId, saved.getName(), saved.getEmail());
        }
        return toView(saved);
    }

    /**
     * Create an inactive employee from an accepted self-registration application.
     * Salary/roles are filled later at finalize.
     */
    @Transactional
    public Employee createInactiveDraft(UUID schoolId, String name, String sex, String type,
                                        String email, String phone, String formClass) {
        requireSchool("HR_MANAGE");
        Employee e = new Employee();
        e.setSchoolId(schoolId);
        e.setCode(nextCode(schoolId));
        e.setName(name.trim());
        e.setSex(blankToNull(sex));
        e.setType(type == null || type.isBlank() ? "Permanent" : type);
        e.setEmail(blankToNull(email));
        e.setPhone(blankToNull(phone));
        e.setFormClass(blankToNull(formClass));
        e.setMonthlySalary(0);
        e.setHourlyRate(0);
        e.setRoles(new HashSet<>(Set.of("teacher")));
        e.setActive(false);
        e.setInitials(initials(name));
        return repo.save(e);
    }

    /** Activate a draft employee and apply HR fields (salary, roles, department). */
    @Transactional
    public EmployeeView finalizeDraft(UUID employeeId, EmployeeUpsert in, boolean createLogin) {
        requireSchool("HR_MANAGE");
        Employee e = find(employeeId);
        apply(e, in);
        e.setInitials(initials(in.name() != null && !in.name().isBlank() ? in.name() : e.getName()));
        e.setActive(true);
        Employee saved = repo.save(e);
        if (createLogin) {
            accounts.provisionOrReset(saved);
        } else {
            accounts.syncAccount(saved);
            mail.notifyUserCreated(saved.getSchoolId(), saved.getName(), saved.getEmail());
        }
        return toView(saved);
    }

    /**
     * Bulk create employees from client-parsed CSV/Excel rows. Bad rows are skipped and
     * reported; the rest still import. Codes are handed out from a local counter so a
     * whole batch stays unique within itself.
     */
    @Transactional
    public StaffImportResult importStaff(StaffImportRequest in) {
        requireSchool("HR_MANAGE");
        UUID schoolId = TenantContext.get();
        boolean wantLogin = Boolean.TRUE.equals(in.createLogin());
        Set<String> validRoles = new HashSet<>(jdbc.queryForList("SELECT code FROM role", String.class));
        Map<String, UUID> deptByName = new HashMap<>();
        for (Department d : departments.findBySchoolIdOrderByName(schoolId)) {
            deptByName.put(d.getName().trim().toLowerCase(Locale.ROOT), d.getId());
        }

        long seq = repo.countBySchoolId(schoolId) + 1;
        Set<String> usedCodes = new HashSet<>();
        Set<String> usedEmails = new HashSet<>();
        List<StaffImportError> errors = new ArrayList<>();
        int created = 0;
        int lineNo = 0;

        for (StaffImportRow row : in.rows()) {
            lineNo++;
            String label = row.name() == null ? "" : row.name().trim();
            try {
                if (label.isBlank()) throw new IllegalArgumentException("Nom obligatoire");

                String sex = normalizeSex(row.sex());
                String type = normalizeType(row.type());
                String email = blankToNull(row.email());
                String phone = blankToNull(row.phone());
                if (email != null && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    throw new IllegalArgumentException("Adresse e-mail invalide");
                }
                if (phone != null && !phone.matches("^[+0-9][0-9\\s().-]{5,24}$")) {
                    throw new IllegalArgumentException("Numéro de téléphone invalide");
                }
                if (email != null) {
                    String key = email.toLowerCase(Locale.ROOT);
                    if (usedEmails.contains(key)
                            || repo.existsBySchoolIdAndEmailIgnoreCaseAndActiveTrue(schoolId, email)) {
                        throw new IllegalArgumentException("E-mail déjà présent (" + email + ") — ignoré");
                    }
                    usedEmails.add(key);
                }

                UUID departmentId = row.departmentId();
                if (departmentId == null && blankToNull(row.department()) != null) {
                    UUID resolved = deptByName.get(row.department().trim().toLowerCase(Locale.ROOT));
                    if (resolved == null) {
                        throw new IllegalArgumentException("Département inconnu (« " + row.department().trim() + " »)");
                    }
                    departmentId = resolved;
                } else if (departmentId != null
                        && departments.findByIdAndSchoolId(departmentId, schoolId).isEmpty()) {
                    throw new IllegalArgumentException("Département inconnu");
                }

                Set<String> roles = resolveRoles(row.roles(), validRoles);
                assertNoNewPrivilege(Set.of(), roles);   // un import ne nomme pas d'administrateur
                Set<String> managementLevels = new HashSet<>();
                if (roles.contains("principal")) {
                    String importedLevel = normSection(blankToNull(row.section()));
                    if (importedLevel == null) {
                        throw new IllegalArgumentException(
                                "Un principal doit avoir au moins un cycle attribué (maternelle, primary ou secondary)");
                    }
                    managementLevels.add(importedLevel);
                }

                String code;
                do {
                    code = "EMP-" + String.format("%03d", seq++);
                } while (usedCodes.contains(code) || repo.existsBySchoolIdAndCode(schoolId, code));
                usedCodes.add(code);

                Employee e = new Employee();
                e.setSchoolId(schoolId);
                e.setCode(code);
                e.setName(label);
                e.setSex(sex);
                e.setType(type);
                e.setEmail(email);
                e.setPhone(phone);
                e.setFormClass(blankToNull(row.formClass()));
                // Un admin de cycle importe dans son cycle : la colonne « section »
                // du fichier ne peut pas l'en faire sortir.
                String rowSection = normSection(blankToNull(row.section()));
                String adminSection = teacherScope.adminSection();
                if (adminSection != null) {
                    if (rowSection != null && !adminSection.equals(rowSection)) {
                        throw new IllegalArgumentException(
                                "Section « " + rowSection + " » hors de votre périmètre");
                    }
                    rowSection = adminSection;
                }
                e.setLevel(rowSection);
                roles = teachingRolesForSection(roles, e.getLevel(), validRoles);
                e.setManagementLevels(managementLevels);
                e.setDepartmentId(departmentId);
                e.setMonthlySalary(row.monthlySalary() == null ? 0L : Math.max(0L, row.monthlySalary()));
                e.setHourlyRate(row.hourlyRate() == null ? 0 : Math.max(0, row.hourlyRate()));
                e.setRoles(roles);
                e.setInitials(initials(label));
                Employee saved = repo.save(e);
                created++;

                if (wantLogin && email != null) {
                    accounts.provisionOrReset(saved);
                } else {
                    mail.notifyUserCreated(schoolId, saved.getName(), saved.getEmail());
                }
            } catch (RuntimeException ex) {
                errors.add(new StaffImportError(lineNo, label.isBlank() ? "?" : label,
                        ex.getMessage() == null ? "Erreur" : ex.getMessage()));
            }
        }
        return new StaffImportResult(created, errors.size(), errors);
    }

    @Transactional
    public EmployeeView update(UUID id, EmployeeUpsert in) {
        requireSchool("HR_MANAGE");
        Employee e = find(id);
        apply(e, in);
        e.setInitials(initials(in.name()));
        Employee saved = repo.save(e);
        accounts.syncAccount(saved);
        return toView(saved);
    }

    @Transactional
    public void delete(UUID id) {
        requireSchool("HR_MANAGE");
        Employee e = find(id);
        e.setActive(false);   // soft delete — keeps payroll/academic history intact
        repo.save(e);
    }

    /**
     * Retire d'un seul geste les employés cochés dans l'annuaire. Chaque
     * identifiant est traité à part : une fiche hors section ou déjà retirée est
     * rapportée sans faire échouer les autres. Les contrôles précèdent toute
     * écriture, la transaction reste donc saine malgré les erreurs collectées.
     */
    @Transactional
    public BulkDeleteResult deleteAll(List<UUID> ids) {
        int deleted = 0;
        List<BulkDeleteError> errors = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            try {
                Employee e = find(id);
                e.setActive(false);
                repo.save(e);
                deleted++;
            } catch (ApiException ex) {
                errors.add(new BulkDeleteError(id, ex.getMessage()));
            }
        }
        return new BulkDeleteResult(deleted, errors.size(), errors);
    }

    /** (Re)issue the employee's login credentials and e-mail them; admin action. */
    @Transactional
    public AccountResult resetCredentials(UUID id) {
        requireSchool("HR_MANAGE");
        return accounts.provisionOrReset(find(id));
    }

    /**
     * L'employé, à condition qu'il relève du périmètre de l'appelant. Toutes les
     * opérations sur une fiche passent par ici : le contrôle de section est donc
     * posé une fois, et non répété à chaque méthode où il pourrait manquer.
     */
    private Employee find(UUID id) {
        Employee e = repo.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Employé"));
        accessScope.assertEmployee(e.getId());
        return e;
    }

    /** employeeId -> account for every staff-linked account in this school. */
    private Map<UUID, AppUser> loginAccounts(UUID schoolId) {
        Map<UUID, AppUser> map = new HashMap<>();
        for (AppUser u : users.findBySchoolIdAndEmployeeIdNotNull(schoolId)) {
            map.put(u.getEmployeeId(), u);
        }
        return map;
    }

    private void apply(Employee e, EmployeeUpsert in) {
        e.setName(in.name());
        e.setSex(blankToNull(in.sex()));   // "" would violate CHECK (sex IN ('M','F'))
        if (in.type() != null && !in.type().isBlank()) e.setType(in.type());
        e.setEmail(blankToNull(in.email()));
        e.setPhone(blankToNull(in.phone()));
        e.setFormClass(blankToNull(in.formClass()));
        applySection(e, blankToNull(in.section()));
        if (in.departmentId() != null
                && departments.findByIdAndSchoolId(in.departmentId(), TenantContext.get()).isEmpty()) {
            throw ApiException.badRequest("Département inconnu");
        }
        e.setDepartmentId(in.departmentId());
        e.setMonthlySalary(in.monthlySalary());
        e.setHourlyRate(in.hourlyRate());
        Set<String> validRoles = new HashSet<>(jdbc.queryForList("SELECT code FROM role", String.class));
        Set<String> resolvedRoles;
        try {
            resolvedRoles = resolveRoles(in.roles() == null ? null : List.copyOf(in.roles()), validRoles);
            assertNoNewPrivilege(e.getRoles(), resolvedRoles);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
        resolvedRoles = teachingRolesForSection(resolvedRoles, e.getLevel(), validRoles);
        e.setRoles(resolvedRoles);
        applyManagementLevels(e, in.managementLevels(), resolvedRoles);
    }

    /**
     * Les rôles d'administration ne se <em>distribuent</em> pas depuis le module
     * Personnel.
     *
     * <p>Sans ce contrôle, l'écran des employés offrirait le chemin de traverse
     * que l'écran des administrateurs ferme : un admin de section cocherait
     * « Principal » sur sa propre fiche, ou nommerait l'admin d'un autre cycle,
     * et son verrou tomberait. La nomination passe par Paramètres →
     * Administrateurs, réservé à l'admin principal.
     *
     * <p>Seuls les rôles <em>nouvellement ajoutés</em> sont refusés : la fiche
     * d'un administrateur déjà nommé reste modifiable — téléphone, département,
     * salaire — sans qu'on lui retire son rôle au passage.
     */
    private void assertNoNewPrivilege(Set<String> current, Set<String> wanted) {
        Set<String> held = current == null ? Set.of() : current;
        for (String r : wanted) {
            if (SectionRoles.privilegedRoles().contains(r) && !held.contains(r)) {
                throw ApiException.badRequest(
                        "Le rôle « " + r + " » se confie depuis Paramètres → Administrateurs.");
            }
        }
    }

    /**
     * Change de section. Un enseignant n'exerce que dans un cycle : muter quelqu'un
     * vers une autre section le détacherait de ses classes actuelles, on l'exige donc
     * explicitement — les affectations de l'ancienne section sont retirées.
     */
    private void applySection(Employee e, String section) {
        if (section != null && !SECTIONS.contains(section)) {
            throw ApiException.badRequest("Section inconnue (attendu maternelle, primary ou secondary)");
        }
        String adminSection = accessScope.adminSection();
        if (adminSection != null) {
            // Un admin de cycle recrute dans son cycle : à défaut de section
            // saisie il impose la sienne, et ne peut muter personne ailleurs —
            // ce serait faire sortir un agent de son propre périmètre.
            if (section == null) section = adminSection;
            else accessScope.assertSection(section);
        }
        String previous = e.getLevel();
        e.setLevel(section);
        if (e.getId() != null && previous != null && !previous.equals(section)) {
            jdbc.update("DELETE FROM teacher_class tc USING school_class c "
                      + "WHERE tc.class_id = c.id AND tc.employee_id = ? AND c.level = ?",
                    e.getId(), previous);
        }
    }

    private static final Set<String> SECTIONS = Set.of("maternelle", "primary", "secondary");

    /** Accepte « Primaire », « primary », « Secondaire »… et renvoie le code interne. */
    private static String normSection(String raw) {
        if (raw == null) return null;
        String c = raw.trim().toLowerCase();
        if (c.startsWith("mat") || c.startsWith("kind") || c.startsWith("nurs")) return "maternelle";
        if (c.startsWith("pri")) return "primary";
        if (c.startsWith("sec")) return "secondary";
        return SECTIONS.contains(c) ? c : null;
    }

    /** Turn empty/blank input into null so optional, CHECK-constrained columns stay valid. */
    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String normalizeSex(String raw) {
        String s = blankToNull(raw);
        if (s == null) return null;
        String c = s.toLowerCase(Locale.ROOT);
        if (c.startsWith("m") || c.startsWith("g")) return "M";
        if (c.startsWith("f")) return "F";
        throw new IllegalArgumentException("Sexe invalide (attendu M ou F)");
    }

    private static String normalizeType(String raw) {
        String s = blankToNull(raw);
        if (s == null) return "Permanent";
        String c = s.toLowerCase(Locale.ROOT);
        if (c.startsWith("vac") || c.startsWith("contract") || c.contains("horaire")) return "Vacataire";
        if (c.startsWith("perm") || c.startsWith("titulaire") || c.startsWith("full")) return "Permanent";
        throw new IllegalArgumentException("Type invalide (Permanent ou Vacataire)");
    }

    private Set<String> resolveRoles(List<String> raw, Set<String> validRoles) {
        Set<String> out = new HashSet<>();
        if (raw != null) {
            for (String r : raw) {
                if (r == null || r.isBlank()) continue;
                String lower = r.trim().toLowerCase(Locale.ROOT);
                String code = ROLE_ALIASES.getOrDefault(lower, ROLE_ALIASES.getOrDefault(lower.replace(' ', '_'), lower.replace(' ', '_')));
                if ("parent".equals(code)) {
                    throw new IllegalArgumentException("Le rôle « parent » ne s’applique pas au personnel");
                }
                if (Set.of("administrator", "admin", "school_admin").contains(code)) {
                    throw new IllegalArgumentException(
                            "Le rôle Administrateur est réservé au compte technique de l’établissement");
                }
                if (!validRoles.contains(code)) {
                    throw new IllegalArgumentException("Rôle inconnu (« " + r.trim() + " »)");
                }
                out.add(code);
            }
        }
        if (out.isEmpty()) out.add("teacher");
        return out;
    }

    private Set<String> teachingRolesForSection(Set<String> roles, String section,
                                                Set<String> validRoles) {
        Set<String> normalized = new HashSet<>(roles);
        if ("secondary".equals(section) && normalized.remove("teacher")
                && validRoles.contains("secondary_teacher")) {
            normalized.add("secondary_teacher");
        } else if (!"secondary".equals(section) && normalized.remove("secondary_teacher")) {
            normalized.add("teacher");
        }
        return normalized;
    }

    private void applyManagementLevels(Employee employee, Set<String> rawLevels, Set<String> roles) {
        Set<String> normalized = new HashSet<>();
        if (rawLevels != null) {
            for (String raw : rawLevels) {
                String level = normSection(blankToNull(raw));
                if (level == null) {
                    throw ApiException.badRequest(
                            "Cycle de direction inconnu (attendu maternelle, primary ou secondary)");
                }
                normalized.add(level);
            }
        }
        if (roles.contains("principal") && normalized.isEmpty()) {
            throw ApiException.badRequest(
                    "Attribuez au principal au moins un cycle : Maternelle, Primaire ou Secondaire");
        }
        employee.setManagementLevels(roles.contains("principal") ? normalized : new HashSet<>());
    }

    private String nextCode(UUID schoolId) {
        long n = repo.countBySchoolId(schoolId) + 1;
        String code;
        do {
            code = "EMP-" + String.format("%03d", n++);
        } while (repo.existsBySchoolIdAndCode(schoolId, code));
        return code;
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) return null;
        StringBuilder sb = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (!word.isEmpty() && sb.length() < 2) {
                sb.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private EmployeeView toView(Employee e) {
        String deptName = e.getDepartmentId() == null ? null
                : departments.findByIdAndSchoolId(e.getDepartmentId(), e.getSchoolId())
                        .map(Department::getName).orElse(null);
        AppUser account = users.findByEmployeeId(e.getId()).orElse(null);
        return toView(e, deptName, account);
    }

    private EmployeeView toView(Employee e, String deptName, AppUser account) {
        // Copy the lazy @ElementCollection into a plain set while the session is
        // still open, otherwise JSON serialization fails with LazyInitializationException.
        Set<String> roles = e.getRoles() == null ? Set.of() : new HashSet<>(e.getRoles());
        Set<String> managementLevels = e.getManagementLevels() == null
                ? Set.of() : new HashSet<>(e.getManagementLevels());
        return new EmployeeView(e.getId(), e.getCode(), e.getName(), e.getInitials(),
                e.getSex(), e.getType(), e.getEmail(), e.getPhone(), e.getFormClass(),
                e.getLevel(), managementLevels, e.getDepartmentId(), deptName,
                e.getMonthlySalary(), e.getHourlyRate(), roles, e.isActive(),
                account != null, account == null ? null : account.getId(), account == null ? null : account.getUsername());
    }

    private void requireSchool(String action) {
        policy.require(action, new PolicyResourceContext(TenantContext.get(), null, java.time.LocalDate.now(),
                null, null, null, null, null, null, null, null, null));
    }
}
