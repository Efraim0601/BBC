package com.bbc.sms.settings;

import com.bbc.sms.identity.AppUser;
import com.bbc.sms.identity.AppUserRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.SectionRoles;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.settings.dto.SettingsDtos.*;
import com.bbc.sms.staff.Employee;
import com.bbc.sms.staff.EmployeeRepository;
import com.bbc.sms.staff.StaffAccountService;
import com.bbc.sms.staff.dto.StaffDtos.AccountResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Les administrateurs de l'établissement, vus depuis Paramètres → Administrateurs.
 *
 * <p>L'admin principal ne peut pas être partout : il délègue chaque cycle à un
 * administrateur de section, qui administre le sien comme lui administre l'école
 * — mais ne voit rien des deux autres, et ne touche à aucun réglage
 * école-entière (cf. {@code @perm.schoolWide()}).
 *
 * <p>Un administrateur de section est d'abord un employé : il figure dans le
 * Personnel, avec sa section, et son compte se provisionne par le même chemin
 * que celui d'un enseignant — mot de passe temporaire envoyé par e-mail, jamais
 * rendu à l'appelant. Ce service n'ajoute que ce qui manquait : créer les deux
 * d'un geste, et empêcher qu'un tel compte en engendre d'autres.
 *
 * <p>Tout est réservé à l'admin principal : {@code schoolWide()} garde les
 * endpoints, et {@link #assertNotSelf} interdit en plus de se dégrader soi-même,
 * ce qui laisserait l'école sans administrateur principal.
 */
@Service
public class AdminAccountService {

    private final AppUserRepository users;
    private final EmployeeRepository employees;
    private final StaffAccountService accounts;
    private final JdbcTemplate jdbc;

    public AdminAccountService(AppUserRepository users, EmployeeRepository employees,
                               StaffAccountService accounts, JdbcTemplate jdbc) {
        this.users = users;
        this.employees = employees;
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    /** Tous les comptes d'administration : l'admin principal et ses relais de section. */
    @Transactional(readOnly = true)
    public List<AdminView> list() {
        return jdbc.query("""
                SELECT u.id, u.username, u.display_name, u.role_code, u.active,
                       u.employee_id, e.email, e.level
                  FROM app_user u
                  LEFT JOIN employee e ON e.id = u.employee_id
                 WHERE u.school_id = ? AND u.role_code IN ('principal','admin_maternelle','admin_primary','admin_secondary')
                 ORDER BY (u.role_code = 'principal') DESC, u.display_name
                """,
                (rs, n) -> new AdminView(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("role_code"),
                        SectionRoles.sectionOf(rs.getString("role_code")),
                        rs.getString("email"),
                        rs.getBoolean("active"),
                        rs.getString("employee_id") == null
                                ? null : UUID.fromString(rs.getString("employee_id"))),
                TenantContext.get());
    }

    /**
     * Crée l'administrateur d'une section : l'employé, son compte, et l'envoi des
     * identifiants. Si un employé porte déjà cette adresse e-mail, on le promeut
     * plutôt que d'en créer un doublon — un même agent n'a qu'une fiche.
     */
    @Transactional
    public AccountResult create(AdminCreate in) {
        UUID schoolId = TenantContext.get();
        String section = normalizeSection(in.section());
        String role = SectionRoles.roleFor(section);

        String email = blankToNull(in.email());
        Employee e = email == null ? null : findByEmail(schoolId, email);
        if (e == null) {
            e = new Employee();
            e.setSchoolId(schoolId);
            e.setCode(nextCode(schoolId));
            e.setName(in.name().trim());
            e.setEmail(email);
            e.setType("Permanent");
            e.setInitials(initials(in.name()));
        }
        e.setPhone(blankToNull(in.phone()));
        e.setLevel(section);
        e.setActive(true);
        // Le rôle porté par la fiche employé décide de celui du compte créé
        // juste après (StaffAccountService.pickRole).
        e.setRoles(new HashSet<>(Set.of(role)));
        Employee saved = employees.save(e);

        AppUser existing = users.findByEmployeeId(saved.getId()).orElse(null);
        if (existing != null) {
            // Compte déjà là (un enseignant qu'on promeut) : on le hisse au rôle
            // d'admin de section avant de régénérer ses identifiants.
            assertNotSelf(existing.getId());
            existing.setRoleCode(role);
            users.save(existing);
        }
        return accounts.provisionOrReset(saved);
    }

    /**
     * Change la section d'un administrateur : le rôle et la section de sa fiche
     * employé bougent ensemble, sans quoi son verrou et son personnel
     * divergeraient.
     */
    @Transactional
    public AdminView changeSection(UUID userId, String section) {
        AppUser u = load(userId);
        assertNotSelf(userId);
        if (!SectionRoles.isSectionAdmin(u.getRoleCode())) {
            throw ApiException.badRequest("Seul un administrateur de section change de section");
        }
        String normalized = normalizeSection(section);
        u.setRoleCode(SectionRoles.roleFor(normalized));
        users.save(u);
        if (u.getEmployeeId() != null) {
            employees.findByIdAndSchoolId(u.getEmployeeId(), TenantContext.get()).ifPresent(e -> {
                e.setLevel(normalized);
                e.setRoles(new HashSet<>(Set.of(u.getRoleCode())));
                employees.save(e);
            });
        }
        return one(userId);
    }

    /** Suspend ou rétablit un administrateur de section. */
    @Transactional
    public AdminView setActive(UUID userId, boolean active) {
        AppUser u = load(userId);
        assertNotSelf(userId);
        if ("principal".equals(u.getRoleCode())) {
            throw ApiException.badRequest("L'administrateur principal ne peut pas être suspendu ici");
        }
        u.setActive(active);
        users.save(u);
        return one(userId);
    }

    /** Régénère le mot de passe et le renvoie par e-mail. */
    @Transactional
    public AccountResult resetCredentials(UUID userId) {
        AppUser u = load(userId);
        if (u.getEmployeeId() == null) {
            throw ApiException.badRequest("Ce compte n'est rattaché à aucune fiche employé");
        }
        Employee e = employees.findByIdAndSchoolId(u.getEmployeeId(), TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Employé"));
        return accounts.provisionOrReset(e);
    }

    // ---- Interne -------------------------------------------------------------

    private AdminView one(UUID userId) {
        return list().stream().filter(a -> a.userId().equals(userId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Administrateur"));
    }

    private AppUser load(UUID userId) {
        AppUser u = users.findById(userId).orElseThrow(() -> ApiException.notFound("Administrateur"));
        if (!u.getSchoolId().equals(TenantContext.get())) throw ApiException.notFound("Administrateur");
        return u;
    }

    /**
     * On ne se modifie pas soi-même depuis cet écran : c'est le seul garde-fou
     * qui empêche l'unique administrateur principal de se rétrograder et de
     * verrouiller l'établissement hors de toute administration.
     */
    private void assertNotSelf(UUID userId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Object p = auth == null ? null : auth.getPrincipal();
        if (p instanceof AppUserPrincipal aup && aup.userId().equals(userId)) {
            throw ApiException.badRequest("Vous ne pouvez pas modifier votre propre compte ici");
        }
    }

    private String normalizeSection(String section) {
        String s = section == null ? "" : section.trim().toLowerCase();
        if (!SectionRoles.SECTIONS.contains(s)) {
            throw ApiException.badRequest("Section inconnue : " + section);
        }
        return s;
    }

    private Employee findByEmail(UUID schoolId, String email) {
        return employees.findBySchoolId(schoolId).stream()
                .filter(e -> email.equalsIgnoreCase(e.getEmail()))
                .findFirst().orElse(null);
    }

    private String nextCode(UUID schoolId) {
        long n = employees.countBySchoolId(schoolId) + 1;
        String code;
        do {
            code = "EMP-" + String.format("%03d", n++);
        } while (employees.existsBySchoolIdAndCode(schoolId, code));
        return code;
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return null;
        StringBuilder sb = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (!word.isEmpty() && sb.length() < 2) sb.append(Character.toUpperCase(word.charAt(0)));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
