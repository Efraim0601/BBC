package com.bbc.sms.staff;

import com.bbc.sms.hr.Department;
import com.bbc.sms.hr.DepartmentRepository;
import com.bbc.sms.identity.AppUser;
import com.bbc.sms.identity.AppUserRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.mail.MailService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.staff.dto.StaffDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    private final JdbcTemplate jdbc;

    public StaffService(EmployeeRepository repo, DepartmentRepository departments, MailService mail,
                        StaffAccountService accounts, AppUserRepository users, JdbcTemplate jdbc) {
        this.repo = repo;
        this.departments = departments;
        this.mail = mail;
        this.accounts = accounts;
        this.users = users;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> list() {
        UUID schoolId = TenantContext.get();
        Map<UUID, String> deptNames = new HashMap<>();
        for (Department d : departments.findBySchoolIdOrderByName(schoolId)) deptNames.put(d.getId(), d.getName());
        Map<UUID, String> logins = loginUsernames(schoolId);
        return repo.findBySchoolIdAndActiveTrueOrderByNameAsc(schoolId).stream()
                .map(e -> toView(e, deptNames.get(e.getDepartmentId()), logins.get(e.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public EmployeeView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public EmployeeView create(EmployeeUpsert in) {
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
        Employee e = find(employeeId);
        apply(e, in);
        e.setInitials(initials(in.name() != null && !in.name().isBlank() ? in.name() : e.getName()));
        e.setActive(true);
        Employee saved = repo.save(e);
        if (createLogin) {
            accounts.provisionOrReset(saved);
        } else {
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
        Employee e = find(id);
        apply(e, in);
        e.setInitials(initials(in.name()));
        return toView(repo.save(e));
    }

    @Transactional
    public void delete(UUID id) {
        Employee e = find(id);
        e.setActive(false);   // soft delete — keeps payroll/academic history intact
        repo.save(e);
    }

    /** (Re)issue the employee's login credentials and e-mail them; admin action. */
    @Transactional
    public AccountResult resetCredentials(UUID id) {
        return accounts.provisionOrReset(find(id));
    }

    private Employee find(UUID id) {
        return repo.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Employé"));
    }

    /** employeeId -> username for every staff-linked account in this school. */
    private Map<UUID, String> loginUsernames(UUID schoolId) {
        Map<UUID, String> map = new HashMap<>();
        for (AppUser u : users.findBySchoolIdAndEmployeeIdNotNull(schoolId)) {
            map.put(u.getEmployeeId(), u.getUsername());
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
        if (in.departmentId() != null
                && departments.findByIdAndSchoolId(in.departmentId(), TenantContext.get()).isEmpty()) {
            throw ApiException.badRequest("Département inconnu");
        }
        e.setDepartmentId(in.departmentId());
        e.setMonthlySalary(in.monthlySalary());
        e.setHourlyRate(in.hourlyRate());
        Set<String> validRoles = new HashSet<>(jdbc.queryForList("SELECT code FROM role", String.class));
        try {
            e.setRoles(resolveRoles(in.roles() == null ? null : List.copyOf(in.roles()), validRoles));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
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
                if (!validRoles.contains(code)) {
                    throw new IllegalArgumentException("Rôle inconnu (« " + r.trim() + " »)");
                }
                out.add(code);
            }
        }
        if (out.isEmpty()) out.add("teacher");
        return out;
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
        String username = users.findByEmployeeId(e.getId()).map(AppUser::getUsername).orElse(null);
        return toView(e, deptName, username);
    }

    private EmployeeView toView(Employee e, String deptName, String username) {
        // Copy the lazy @ElementCollection into a plain set while the session is
        // still open, otherwise JSON serialization fails with LazyInitializationException.
        Set<String> roles = e.getRoles() == null ? Set.of() : new HashSet<>(e.getRoles());
        return new EmployeeView(e.getId(), e.getCode(), e.getName(), e.getInitials(),
                e.getSex(), e.getType(), e.getEmail(), e.getPhone(), e.getFormClass(),
                e.getDepartmentId(), deptName,
                e.getMonthlySalary(), e.getHourlyRate(), roles, e.isActive(),
                username != null, username);
    }
}
