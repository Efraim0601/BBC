package com.bbc.sms.setup;

import com.bbc.sms.academic.Subject;
import com.bbc.sms.academic.SubjectRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.ParcoursContext.Scope;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.StudentService;
import com.bbc.sms.setup.dto.SetupDtos.*;
import com.bbc.sms.staff.EmployeeRepository;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
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
    private final StudentRepository students;
    private final EmployeeRepository employees;
    private final JdbcTemplate jdbc;

    public SetupService(SectionRepository sections, SchoolClassRepository classes,
                        SubjectRepository subjects, StudentRepository students,
                        EmployeeRepository employees, JdbcTemplate jdbc) {
        this.sections = sections;
        this.classes = classes;
        this.subjects = subjects;
        this.students = students;
        this.employees = employees;
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
        return classes.findBySchoolIdOrderByName(schoolId).stream()
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

    /** Active employees that may be assigned as teachers (picker source). */
    @Transactional(readOnly = true)
    public List<TeacherOption> assignableTeachers() {
        UUID schoolId = TenantContext.get();
        return employees.findBySchoolIdAndActiveTrueOrderByNameAsc(schoolId).stream()
                .map(e -> new TeacherOption(e.getId(), e.getName(), e.getCode()))
                .toList();
    }

    /** Teachers currently linked to a class. */
    @Transactional(readOnly = true)
    public List<TeacherOption> classTeachers(UUID classId) {
        UUID schoolId = TenantContext.get();
        classes.findByIdAndSchoolId(classId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));
        return jdbc.query(
                "SELECT e.id, e.name, e.code FROM teacher_class tc "
              + "JOIN employee e ON e.id = tc.employee_id "
              + "WHERE tc.class_id = ? AND e.school_id = ? ORDER BY e.name",
                (rs, n) -> new TeacherOption(
                        UUID.fromString(rs.getString("id")), rs.getString("name"), rs.getString("code")),
                classId, schoolId);
    }

    /** Replace the full set of teachers linked to a class (0..N). */
    @Transactional
    public List<TeacherOption> setClassTeachers(UUID classId, List<UUID> employeeIds) {
        UUID schoolId = TenantContext.get();
        classes.findByIdAndSchoolId(classId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));
        jdbc.update("DELETE FROM teacher_class WHERE class_id = ?", classId);
        if (employeeIds != null) {
            for (UUID empId : employeeIds.stream().distinct().toList()) {
                // Only link employees that belong to this tenant.
                employees.findByIdAndSchoolId(empId, schoolId)
                        .orElseThrow(() -> ApiException.badRequest("Enseignant inconnu"));
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
