package com.bbc.sms.setup;

import com.bbc.sms.academic.Subject;
import com.bbc.sms.academic.SubjectRepository;
import com.bbc.sms.academic.SubjectClassCoef;
import com.bbc.sms.academic.SubjectClassCoefRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.ParcoursContext.Scope;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.StudentService;
import com.bbc.sms.setup.dto.SetupDtos.*;
import com.bbc.sms.staff.Employee;
import com.bbc.sms.staff.EmployeeRepository;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Academic Setup — admins build the relational backbone (sections, classes, subjects)
 * here, BEFORE enrolling students. The student form then binds a real {@code class_id}
 * instead of free text, which is the whole point of review issues #1 and #3.
 */
@Service
public class SetupService {

    private final SectionRepository sections;
    private final SchoolClassRepository classes;
    private final SubjectRepository subjects;
    private final SubjectClassCoefRepository coefs;
    private final StudentRepository students;
    private final EmployeeRepository employees;
    private final TeacherScopeService teacherScope;
    private final JdbcTemplate jdbc;

    public SetupService(SectionRepository sections, SchoolClassRepository classes,
                        SubjectRepository subjects, SubjectClassCoefRepository coefs,
                        StudentRepository students, EmployeeRepository employees,
                        TeacherScopeService teacherScope, JdbcTemplate jdbc) {
        this.sections = sections;
        this.classes = classes;
        this.subjects = subjects;
        this.coefs = coefs;
        this.students = students;
        this.employees = employees;
        this.teacherScope = teacherScope;
        this.jdbc = jdbc;
    }

    // ---- Sections -----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SectionView> listSections() {
        UUID schoolId = TenantContext.get();
        Scope scope = ParcoursContext.get();
        return sections.findBySchoolIdOrderByLabel(schoolId).stream()
                .filter(s -> StudentService.inScope(scope, s.getLevel(), s.getSubsystem()))
                .map(this::toView).toList();
    }

    @Transactional
    public SectionView createSection(SectionUpsert in) {
        UUID schoolId = TenantContext.get();
        Section s = new Section();
        s.setId(uniqueSectionId(schoolId, in.subsystem(), in.level()));
        s.setSchoolId(schoolId);
        s.setLabel(in.label().trim());
        s.setSubsystem(in.subsystem());
        s.setLevel(in.level());
        return toView(sections.save(s));
    }

    @Transactional
    public SectionView updateSection(String id, SectionUpsert in) {
        UUID schoolId = TenantContext.get();
        Section s = sections.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Section"));
        s.setLabel(in.label().trim());
        s.setSubsystem(in.subsystem());
        s.setLevel(in.level());
        return toView(sections.save(s));
    }

    @Transactional
    public void deleteSection(String id) {
        UUID schoolId = TenantContext.get();
        Section s = sections.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Section"));
        if (classes.existsBySchoolIdAndSectionId(schoolId, id)) {
            throw ApiException.conflict("Cette section contient des classes — supprimez-les d'abord");
        }
        sections.delete(s);
    }

    // ---- Classes ------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ClassView> listClasses() {
        UUID schoolId = TenantContext.get();
        Map<String, Section> byId = sections.findBySchoolIdOrderByLabel(schoolId).stream()
                .collect(java.util.stream.Collectors.toMap(Section::getId, x -> x));
        Scope scope = ParcoursContext.get();
        // Un enseignant ne voit que les classes qui lui sont assignées.
        Set<UUID> allowed = teacherScope.allowedClassIds();
        return classes.findBySchoolIdOrderByName(schoolId).stream()
                .filter(c -> allowed == null || allowed.contains(c.getId()))
                .filter(c -> StudentService.inScope(scope, c.getLevel(), c.getSubsystem()))
                .map(c -> toView(c, byId.get(c.getSectionId())))
                .toList();
    }

    @Transactional
    public ClassView createClass(ClassUpsert in) {
        UUID schoolId = TenantContext.get();
        Section section = sections.findByIdAndSchoolId(in.sectionId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Section"));
        String name = in.name().trim();
        if (classes.existsBySchoolIdAndName(schoolId, name)) {
            throw ApiException.conflict("Une classe « " + name + " » existe déjà");
        }
        SchoolClass c = new SchoolClass();
        c.setSchoolId(schoolId);
        c.setSectionId(section.getId());
        c.setName(name);
        c.setSubsystem(section.getSubsystem());   // class inherits its section's subsystem/level
        c.setLevel(section.getLevel());
        return toView(classes.save(c), section);
    }

    /**
     * Find a class by name, or create it (and its owning section) on the fly.
     * Used by the student bulk-import so a register for "5e A" lands in a real
     * relational class even when the admin has not pre-created it. The section is
     * matched (or created) by (subsystem, level) so every FR/EN + level pairing
     * gets exactly one auto-section.
     */
    @Transactional
    public SchoolClass findOrCreateClass(String rawName, String subsystem, String level) {
        UUID schoolId = TenantContext.get();
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) throw ApiException.badRequest("Nom de classe obligatoire");
        String sub = normSubsystem(subsystem);
        if (sub == null) throw ApiException.badRequest("Sous-système invalide (attendu FR ou EN)");
        String lvl = normLevel(level);

        return classes.findBySchoolIdAndName(schoolId, name).orElseGet(() -> {
            Section section = findOrCreateSection(schoolId, sub, lvl);
            SchoolClass c = new SchoolClass();
            c.setSchoolId(schoolId);
            c.setSectionId(section.getId());
            c.setName(name);
            c.setSubsystem(section.getSubsystem());
            c.setLevel(section.getLevel());
            return classes.save(c);
        });
    }

    /** Reuse a section with the same (subsystem, level), else create a labelled one. */
    private Section findOrCreateSection(UUID schoolId, String subsystem, String level) {
        return sections.findBySchoolIdOrderByLabel(schoolId).stream()
                .filter(s -> subsystem.equalsIgnoreCase(s.getSubsystem()) && level.equalsIgnoreCase(s.getLevel()))
                .findFirst()
                .orElseGet(() -> {
                    Section s = new Section();
                    s.setId(uniqueSectionId(schoolId, subsystem, level));
                    s.setSchoolId(schoolId);
                    s.setLabel(sectionLabel(subsystem, level));
                    s.setSubsystem(subsystem);
                    s.setLevel(level);
                    return sections.save(s);
                });
    }

    private static String normLevel(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase();
        if (v.startsWith("mat")) return "maternelle";
        if (v.startsWith("sec")) return "secondary";
        return "primary";
    }

    private static String sectionLabel(String subsystem, String level) {
        String lvl = switch (level) {
            case "maternelle" -> "Maternelle";
            case "secondary" -> "Secondaire";
            default -> "Primaire";
        };
        return lvl + ("FR".equalsIgnoreCase(subsystem) ? " francophone" : " anglophone");
    }

    @Transactional
    public ClassView updateClass(UUID id, ClassUpsert in) {
        UUID schoolId = TenantContext.get();
        SchoolClass c = classes.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));
        Section section = sections.findByIdAndSchoolId(in.sectionId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Section"));
        String name = in.name().trim();
        if (!name.equalsIgnoreCase(c.getName()) && classes.existsBySchoolIdAndName(schoolId, name)) {
            throw ApiException.conflict("Une classe « " + name + " » existe déjà");
        }
        c.setName(name);
        c.setSectionId(section.getId());
        c.setSubsystem(section.getSubsystem());
        c.setLevel(section.getLevel());
        return toView(classes.save(c), section);
    }

    @Transactional
    public void deleteClass(UUID id) {
        UUID schoolId = TenantContext.get();
        SchoolClass c = classes.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));
        if (students.countBySchoolIdAndClassIdAndActiveTrue(schoolId, id) > 0) {
            throw ApiException.conflict("Des élèves sont inscrits dans cette classe — réaffectez-les d'abord");
        }
        classes.delete(c);
    }

    // ---- Class ↔ teachers (N:N, 0..N teachers per class) --------------------

    /**
     * Employés actifs assignables comme enseignants.
     *
     * <p>Avec {@code level}, la liste se limite à la section demandée : un
     * enseignant du primaire n'apparaît pas dans le sélecteur d'une classe du
     * secondaire. Les employés sans section restent proposés — la première
     * affectation fixera la leur.
     */
    @Transactional(readOnly = true)
    public List<TeacherOption> assignableTeachers(String level) {
        UUID schoolId = TenantContext.get();
        String wanted = blankToNull(level);
        return employees.findBySchoolIdAndActiveTrueOrderByNameAsc(schoolId).stream()
                .filter(e -> wanted == null || e.getLevel() == null || wanted.equals(e.getLevel()))
                .map(e -> new TeacherOption(e.getId(), e.getName(), e.getCode(), e.getLevel()))
                .toList();
    }

    /**
     * Verrouille la règle « un enseignant, une section » au moment de l'affectation.
     * Sans section, l'employé prend celle de la classe : c'est la première
     * affectation qui le rattache, pas une saisie séparée.
     */
    @Transactional
    public void bindTeacherSection(UUID employeeId, String classLevel) {
        UUID schoolId = TenantContext.get();
        Employee e = employees.findByIdAndSchoolId(employeeId, schoolId)
                .orElseThrow(() -> ApiException.badRequest("Enseignant inconnu"));
        if (classLevel == null || classLevel.isBlank()) return;
        if (e.getLevel() == null) {
            e.setLevel(classLevel);
            employees.save(e);
            return;
        }
        if (!e.getLevel().equals(classLevel)) {
            throw ApiException.conflict(e.getName() + " est rattaché à la section « " + sectionLabel(e.getLevel())
                    + " » : il ne peut pas enseigner en « " + sectionLabel(classLevel) + " ». "
                    + "Changez sa section depuis la fiche du personnel si c'est une mutation.");
        }
    }

    /** Libellé français d'une section, pour les messages d'erreur. */
    public static String sectionLabel(String level) {
        if (level == null) return "non définie";
        return switch (level) {
            case "maternelle" -> "Maternelle";
            case "primary" -> "Primaire";
            case "secondary" -> "Secondaire";
            default -> level;
        };
    }

    /** Teachers currently linked to a class. */
    @Transactional(readOnly = true)
    public List<TeacherOption> classTeachers(UUID classId) {
        UUID schoolId = TenantContext.get();
        classes.findByIdAndSchoolId(classId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));
        return jdbc.query(
                "SELECT e.id, e.name, e.code, e.level FROM teacher_class tc "
              + "JOIN employee e ON e.id = tc.employee_id "
              + "WHERE tc.class_id = ? AND e.school_id = ? ORDER BY e.name",
                (rs, n) -> new TeacherOption(UUID.fromString(rs.getString("id")),
                        rs.getString("name"), rs.getString("code"), rs.getString("level")),
                classId, schoolId);
    }

    /** Replace the full set of teachers linked to a class (0..N). */
    @Transactional
    public List<TeacherOption> setClassTeachers(UUID classId, List<UUID> employeeIds) {
        UUID schoolId = TenantContext.get();
        SchoolClass cls = classes.findByIdAndSchoolId(classId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));
        jdbc.update("DELETE FROM teacher_class WHERE class_id = ?", classId);
        if (employeeIds != null) {
            for (UUID empId : employeeIds.stream().distinct().toList()) {
                // Only link employees that belong to this tenant…
                employees.findByIdAndSchoolId(empId, schoolId)
                        .orElseThrow(() -> ApiException.badRequest("Enseignant inconnu"));
                // …et qui exercent dans la section de la classe.
                bindTeacherSection(empId, cls.getLevel());
                jdbc.update("INSERT INTO teacher_class (employee_id, class_id) VALUES (?, ?)", empId, classId);
            }
        }
        return classTeachers(classId);
    }

    // ---- Subjects -----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SubjectView> listSubjects() {
        UUID schoolId = TenantContext.get();
        return subjects.findBySchoolIdOrderByCode(schoolId).stream().map(this::toView).toList();
    }

    @Transactional
    public SubjectView createSubject(SubjectUpsert in) {
        UUID schoolId = TenantContext.get();
        String code = in.code().trim().toUpperCase();
        String subsystem = normSubsystem(in.subsystem());
        boolean dup = subjects.findBySchoolIdOrderByCode(schoolId).stream()
                .anyMatch(x -> code.equals(x.getCode())
                        && java.util.Objects.equals(subsystem, normSubsystem(x.getSubsystem())));
        if (dup) {
            throw ApiException.conflict("Une matière « " + code + " » existe déjà"
                    + (subsystem == null ? "" : " (" + subsystem + ")"));
        }
        Subject s = new Subject();
        s.setSchoolId(schoolId);
        s.setCode(code);
        s.setSubsystem(subsystem);
        s.setLabel(in.label());
        s.setCoef(Math.max(1, in.coef()));
        return toView(subjects.save(s));
    }

    @Transactional
    public SubjectView updateSubject(UUID id, SubjectUpsert in) {
        UUID schoolId = TenantContext.get();
        Subject s = subjects.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Matière"));
        s.setSubsystem(normSubsystem(in.subsystem()));
        s.setLabel(in.label());
        s.setCoef(Math.max(1, in.coef()));
        return toView(subjects.save(s));
    }

    /** Normalise a subsystem tag to 'FR' | 'EN' | null (common to both). */
    private static String normSubsystem(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toUpperCase();
        return (v.equals("FR") || v.equals("EN")) ? v : null;
    }

    @Transactional
    public void deleteSubject(UUID id) {
        UUID schoolId = TenantContext.get();
        Subject s = subjects.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Matière"));
        subjects.delete(s);
    }

    // ---- Per-class coefficients --------------------------------------------

    @Transactional(readOnly = true)
    public List<ClassCoefView> listCoefficients() {
        UUID schoolId = TenantContext.get();
        Map<UUID, SchoolClass> classById = classes.findBySchoolIdOrderByName(schoolId).stream()
                .collect(java.util.stream.Collectors.toMap(SchoolClass::getId, c -> c));
        Map<UUID, Subject> subjById = subjects.findBySchoolIdOrderByCode(schoolId).stream()
                .collect(java.util.stream.Collectors.toMap(Subject::getId, s -> s));
        return coefs.findBySchoolId(schoolId).stream()
                .map(cc -> {
                    SchoolClass c = classById.get(cc.getClassId());
                    Subject s = subjById.get(cc.getSubjectId());
                    if (c == null || s == null) return null;
                    return new ClassCoefView(c.getId(), c.getName(), c.getSubsystem(),
                            s.getId(), s.getCode(), cc.getCoef());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Import per-class coefficients from the official tables. Each row targets a
     * (subject, class) pair: the subject is matched by code (created if missing),
     * and the class token is matched by name or grade prefix so "5e" reaches every
     * 5e-class. Existing pairs are overwritten. Rows with no matching class or a
     * blank coefficient are skipped and reported.
     */
    @Transactional
    public CoefImportResult importCoefficients(CoefImportRequest in) {
        UUID schoolId = TenantContext.get();
        List<SchoolClass> allClasses = classes.findBySchoolIdOrderByName(schoolId);
        List<CoefImportError> errors = new ArrayList<>();
        Set<String> createdSubjects = new HashSet<>();
        int applied = 0, skipped = 0, lineNo = 0;

        for (CoefImportRow row : in.rows()) {
            lineNo++;
            String label = (blankToNull(row.code()) == null ? "?" : row.code().trim())
                    + " / " + (blankToNull(row.klass()) == null ? "?" : row.klass().trim());
            try {
                String sub = normSubsystem(row.subsystem());
                if (sub == null) { skipped++; errors.add(new CoefImportError(lineNo, label, "Sous-système invalide (FR/EN)")); continue; }
                if (blankToNull(row.code()) == null) { skipped++; errors.add(new CoefImportError(lineNo, label, "Code matière manquant")); continue; }
                if (row.coef() == null || row.coef() <= 0) { skipped++; continue; }   // "-" in the table = not taught

                List<SchoolClass> targets = allClasses.stream()
                        .filter(c -> sub.equalsIgnoreCase(c.getSubsystem()))
                        .filter(c -> classMatches(c.getName(), row.klass()))
                        .toList();
                if (targets.isEmpty()) { skipped++; errors.add(new CoefImportError(lineNo, label, "Aucune classe « " + row.klass() + " » (" + sub + ")")); continue; }

                Subject subject = findOrCreateSubject(schoolId, row.code(), sub, row.label(), createdSubjects);
                for (SchoolClass c : targets) {
                    SubjectClassCoef cc = coefs.findBySchoolIdAndSubjectIdAndClassId(schoolId, subject.getId(), c.getId())
                            .orElseGet(() -> {
                                SubjectClassCoef fresh = new SubjectClassCoef();
                                fresh.setSchoolId(schoolId);
                                fresh.setSubjectId(subject.getId());
                                fresh.setClassId(c.getId());
                                return fresh;
                            });
                    cc.setCoef(row.coef());
                    coefs.save(cc);
                    applied++;
                }
            } catch (RuntimeException ex) {
                skipped++;
                errors.add(new CoefImportError(lineNo, label, ex.getMessage()));
            }
        }
        return new CoefImportResult(applied, createdSubjects.size(), skipped, errors);
    }

    private Subject findOrCreateSubject(UUID schoolId, String rawCode, String subsystem, String rawLabel, Set<String> created) {
        String code = (rawCode.trim().length() > 8 ? rawCode.trim().substring(0, 8) : rawCode.trim()).toUpperCase();
        return subjects.findBySchoolIdOrderByCode(schoolId).stream()
                .filter(s -> code.equals(s.getCode()) && java.util.Objects.equals(subsystem, normSubsystem(s.getSubsystem())))
                .findFirst()
                .orElseGet(() -> {
                    Subject s = new Subject();
                    s.setSchoolId(schoolId);
                    s.setCode(code);
                    s.setSubsystem(subsystem);
                    String name = blankToNull(rawLabel) == null ? code : rawLabel.trim();
                    s.setLabel("FR".equals(subsystem) ? Map.of("fr", name) : Map.of("en", name));
                    s.setCoef(1);
                    Subject saved = subjects.save(s);
                    created.add(code + "/" + subsystem);
                    return saved;
                });
    }

    /** A stored class name matches a token if equal, or the name starts with the grade token. */
    static boolean classMatches(String className, String token) {
        String c = normKey(className), t = normKey(token);
        if (t.isEmpty()) return false;
        if (c.equals(t)) return true;
        // Grade prefix: "5e" matches "5ea", but "form1" must not match "form10".
        return c.startsWith(t) && (c.length() == t.length() || !Character.isDigit(c.charAt(t.length())));
    }

    private static String normKey(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
        return n.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    // ---- mapping ------------------------------------------------------------

    private SectionView toView(Section s) {
        long classCount = classes.findBySchoolIdAndSectionId(s.getSchoolId(), s.getId()).size();
        return new SectionView(s.getId(), s.getLabel(), s.getSubsystem(), s.getLevel(), classCount);
    }

    private ClassView toView(SchoolClass c, Section section) {
        long count = students.countBySchoolIdAndClassIdAndActiveTrue(c.getSchoolId(), c.getId());
        Long teachers = jdbc.queryForObject(
                "SELECT count(*) FROM teacher_class WHERE class_id = ?", Long.class, c.getId());
        return new ClassView(c.getId(), c.getName(), c.getSectionId(),
                section == null ? null : section.getLabel(),
                c.getSubsystem(), c.getLevel(), count, teachers == null ? 0 : teachers);
    }

    private SubjectView toView(Subject s) {
        return new SubjectView(s.getId(), s.getCode(), s.getSubsystem(), s.getLabel(), s.getCoef());
    }

    /** Deterministic short id from subsystem+level (mat-fr, pri-fr, sec-en…), suffixed if taken. */
    private String uniqueSectionId(UUID schoolId, String subsystem, String level) {
        String prefix = level.startsWith("mat") ? "mat" : level.startsWith("pri") ? "pri" : "sec";
        String base = prefix + "-" + subsystem.toLowerCase();
        base = Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("[^a-z0-9-]", "");
        String id = base;
        int n = 2;
        while (sections.existsByIdAndSchoolId(id, schoolId)) {
            id = base + "-" + n++;
        }
        return id;
    }
}
