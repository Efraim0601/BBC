package com.bbc.sms.staff;

import com.bbc.sms.identity.School;
import com.bbc.sms.identity.SchoolRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.mail.MailService;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.staff.dto.StaffDtos.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class StaffApplicationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<String> OPEN_STATUSES = List.of("pending", "accepted");

    private final StaffApplicationRepository apps;
    private final SchoolRepository schools;
    private final EmployeeRepository employees;
    private final StaffService staff;
    private final MailService mail;
    private final AuthorizationPolicyService policy;

    public StaffApplicationService(StaffApplicationRepository apps, SchoolRepository schools,
                                   EmployeeRepository employees, StaffService staff, MailService mail,
                                   AuthorizationPolicyService policy) {
        this.apps = apps;
        this.schools = schools;
        this.employees = employees;
        this.staff = staff;
        this.mail = mail;
        this.policy = policy;
    }

    // ---- Portal settings (admin) -------------------------------------------

    @Transactional(readOnly = true)
    public StaffPortalSettingsView getPortalSettings() {
        requireSchool("HR_VIEW");
        return toSettings(currentSchool());
    }

    @Transactional
    public StaffPortalSettingsView updatePortalSettings(StaffPortalSettingsUpdate in) {
        requireSchool("HR_MANAGE");
        School s = currentSchool();
        if (in.enabled()) {
            ensurePortalCredentials(s);
            s.setStaffPortalEnabled(true);
        } else {
            s.setStaffPortalEnabled(false);
        }
        return toSettings(schools.save(s));
    }

    @Transactional
    public StaffPortalSettingsView regeneratePortalToken() {
        requireSchool("HR_MANAGE");
        School s = currentSchool();
        ensurePortalCredentials(s);
        s.setStaffPortalToken(randomToken());
        if (!s.isStaffPortalEnabled()) {
            // regenerating implies the admin wants a usable link
            s.setStaffPortalEnabled(true);
        }
        return toSettings(schools.save(s));
    }

    // ---- Public portal -----------------------------------------------------

    @Transactional(readOnly = true)
    public StaffPortalMeta publicMeta(String slug, String token) {
        School s = resolveOpenPortal(slug, token);
        return new StaffPortalMeta(s.getName(), s.getCode(), true);
    }

    @Transactional
    public StaffApplicationView submit(String slug, String token, StaffApplicationSubmit in) {
        School s = resolveOpenPortal(slug, token);
        String name = in.name() == null ? "" : in.name().trim();
        if (name.isBlank()) throw ApiException.badRequest("Nom obligatoire");

        String email = blankToNull(in.email());
        String phone = blankToNull(in.phone());
        if (email == null && phone == null) {
            throw ApiException.badRequest("Indiquez au moins un e-mail ou un téléphone");
        }
        if (email != null && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw ApiException.badRequest("Adresse e-mail invalide");
        }
        if (phone != null && !phone.matches("^[+0-9][0-9\\s().-]{5,24}$")) {
            throw ApiException.badRequest("Numéro de téléphone invalide");
        }
        if (email != null
                && (apps.existsBySchoolIdAndEmailIgnoreCaseAndStatusIn(s.getId(), email, OPEN_STATUSES)
                || employees.existsBySchoolIdAndEmailIgnoreCaseAndActiveTrue(s.getId(), email))) {
            throw ApiException.conflict("Une candidature ou un compte existe déjà avec cet e-mail");
        }

        String sex = normalizeSex(in.sex());
        String type = normalizeType(in.type());

        StaffApplication a = new StaffApplication();
        a.setSchoolId(s.getId());
        a.setStatus("pending");
        a.setName(name);
        a.setSex(sex);
        a.setType(type);
        a.setEmail(email);
        a.setPhone(phone);
        a.setFormClass(blankToNull(in.formClass()));
        a.setDepartmentHint(blankToNull(in.departmentHint()));
        a.setDesiredRoles(blankToNull(in.desiredRoles()));
        a.setNotes(blankToNull(in.notes()));
        a.setSubmittedAt(Instant.now());
        StaffApplication saved = apps.save(a);

        // Best-effort notify school contact (no-op if SMTP off / no school email).
        if (s.getEmail() != null && !s.getEmail().isBlank()) {
            mail.notifyUserCreated(s.getId(), "Candidature RH — " + name, s.getEmail());
        }
        return toView(saved, null);
    }

    // ---- Admin review ------------------------------------------------------

    @Transactional(readOnly = true)
    public List<StaffApplicationView> list(String status) {
        requireSchool("HR_VIEW");
        UUID schoolId = TenantContext.get();
        List<StaffApplication> list = (status == null || status.isBlank())
                ? apps.findBySchoolIdOrderBySubmittedAtDesc(schoolId)
                : apps.findBySchoolIdAndStatusOrderBySubmittedAtDesc(schoolId, status.trim());
        return list.stream().map(a -> toView(a, employeeCode(a.getEmployeeId()))).toList();
    }

    @Transactional
    public StaffApplicationView accept(UUID id) {
        requireSchool("HR_MANAGE");
        StaffApplication a = findApp(id);
        if (!"pending".equals(a.getStatus())) {
            throw ApiException.badRequest("Seules les candidatures en attente peuvent être acceptées");
        }
        Employee draft = staff.createInactiveDraft(
                a.getSchoolId(), a.getName(), a.getSex(), a.getType(),
                a.getEmail(), a.getPhone(), a.getFormClass());
        a.setEmployeeId(draft.getId());
        a.setStatus("accepted");
        a.setDecidedAt(Instant.now());
        a.setDecidedBy(currentUserId());
        return toView(apps.save(a), draft.getCode());
    }

    @Transactional
    public StaffApplicationView reject(UUID id, StaffApplicationReject in) {
        requireSchool("HR_MANAGE");
        StaffApplication a = findApp(id);
        if (!"pending".equals(a.getStatus()) && !"accepted".equals(a.getStatus())) {
            throw ApiException.badRequest("Cette candidature ne peut plus être refusée");
        }
        if ("accepted".equals(a.getStatus()) && a.getEmployeeId() != null) {
            // Soft-delete the draft employee created at accept.
            employees.findByIdAndSchoolId(a.getEmployeeId(), a.getSchoolId()).ifPresent(e -> {
                e.setActive(false);
                employees.save(e);
            });
        }
        a.setStatus("rejected");
        a.setRejectReason(in.reason().trim());
        a.setDecidedAt(Instant.now());
        a.setDecidedBy(currentUserId());
        return toView(apps.save(a), employeeCode(a.getEmployeeId()));
    }

    @Transactional
    public StaffApplicationView finalize(UUID id, StaffApplicationFinalize in) {
        requireSchool("HR_MANAGE");
        StaffApplication a = findApp(id);
        if (!"accepted".equals(a.getStatus())) {
            throw ApiException.badRequest("Acceptez d'abord la candidature avant de finaliser");
        }
        if (a.getEmployeeId() == null) {
            throw ApiException.badRequest("Aucun employé brouillon lié à cette candidature");
        }
        boolean createLogin = Boolean.TRUE.equals(in.createLogin());
        EmployeeUpsert upsert = new EmployeeUpsert(
                a.getName(),
                a.getSex(),
                in.type() == null || in.type().isBlank() ? a.getType() : in.type(),
                a.getEmail() == null ? "" : a.getEmail(),
                a.getPhone() == null ? "" : a.getPhone(),
                in.formClass() != null ? in.formClass() : a.getFormClass(),
                in.section(),
                in.departmentId(),
                in.monthlySalary(),
                in.hourlyRate(),
                in.roles(),
                createLogin);
        staff.finalizeDraft(a.getEmployeeId(), upsert, createLogin);
        a.setStatus("finalized");
        a.setFinalizedAt(Instant.now());
        return toView(apps.save(a), employeeCode(a.getEmployeeId()));
    }

    // ---- helpers -----------------------------------------------------------

    private School resolveOpenPortal(String slug, String token) {
        if (slug == null || slug.isBlank() || token == null || token.isBlank()) {
            throw ApiException.badRequest("Lien du portail invalide");
        }
        School s = schools.findByStaffPortalSlug(slug.trim())
                .orElseThrow(() -> ApiException.notFound("Portail introuvable"));
        if (!s.isStaffPortalEnabled()) {
            throw ApiException.badRequest("Le portail d'inscription est désactivé");
        }
        if (s.getStaffPortalToken() == null || !s.getStaffPortalToken().equals(token.trim())) {
            throw ApiException.badRequest("Lien du portail invalide ou expiré");
        }
        return s;
    }

    private void ensurePortalCredentials(School s) {
        if (s.getStaffPortalSlug() == null || s.getStaffPortalSlug().isBlank()) {
            s.setStaffPortalSlug(uniqueSlug(s));
        }
        if (s.getStaffPortalToken() == null || s.getStaffPortalToken().isBlank()) {
            s.setStaffPortalToken(randomToken());
        }
    }

    private String uniqueSlug(School s) {
        String base = slugify(s.getCode() != null ? s.getCode() : s.getName());
        if (base.isBlank()) base = "ecole";
        String candidate = base;
        int n = 2;
        while (schools.findByStaffPortalSlug(candidate).filter(o -> !o.getId().equals(s.getId())).isPresent()) {
            candidate = base + "-" + n++;
        }
        return candidate;
    }

    private static String slugify(String raw) {
        if (raw == null) return "";
        String n = Normalizer.normalize(raw.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "");
        n = n.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return n.length() > 48 ? n.substring(0, 48).replaceAll("-$", "") : n;
    }

    private static String randomToken() {
        byte[] buf = new byte[24];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private School currentSchool() {
        return schools.findById(TenantContext.get())
                .orElseThrow(() -> ApiException.badRequest("Établissement introuvable"));
    }

    private StaffApplication findApp(UUID id) {
        return apps.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Candidature"));
    }

    private String employeeCode(UUID employeeId) {
        if (employeeId == null) return null;
        return employees.findById(employeeId).map(Employee::getCode).orElse(null);
    }

    private StaffPortalSettingsView toSettings(School s) {
        String slug = s.getStaffPortalSlug();
        String token = s.getStaffPortalToken();
        String path = (slug == null || token == null) ? null
                : "/join-staff/" + slug + "?t=" + token;
        return new StaffPortalSettingsView(s.isStaffPortalEnabled(), slug, token, path);
    }

    private StaffApplicationView toView(StaffApplication a, String employeeCode) {
        return new StaffApplicationView(
                a.getId(), a.getStatus(), a.getName(), a.getSex(), a.getType(),
                a.getEmail(), a.getPhone(), a.getFormClass(), a.getDepartmentHint(),
                a.getDesiredRoles(), a.getNotes(), a.getRejectReason(),
                a.getEmployeeId(), employeeCode,
                a.getSubmittedAt(), a.getDecidedAt(), a.getFinalizedAt());
    }

    private static UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return p.userId();
        }
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String normalizeSex(String raw) {
        String s = blankToNull(raw);
        if (s == null) return null;
        String c = s.toLowerCase(Locale.ROOT);
        if (c.startsWith("m") || c.startsWith("g")) return "M";
        if (c.startsWith("f")) return "F";
        throw ApiException.badRequest("Sexe invalide (attendu M ou F)");
    }

    private static String normalizeType(String raw) {
        String s = blankToNull(raw);
        if (s == null) return "Permanent";
        String c = s.toLowerCase(Locale.ROOT);
        if (c.startsWith("vac") || c.startsWith("contract") || c.contains("horaire")) return "Vacataire";
        if (c.startsWith("perm") || c.startsWith("titulaire") || c.startsWith("full")) return "Permanent";
        throw ApiException.badRequest("Type invalide (Permanent ou Vacataire)");
    }

    private void requireSchool(String action) {
        policy.require(action, new PolicyResourceContext(TenantContext.get(), null, java.time.LocalDate.now(),
                null, null, null, null, null, null, null, null, null));
    }
}
