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

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
                            s.getId(), s.getCode(), cc.getCoef(), s.getCoef());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public ClassCoefView upsertCoefficient(ClassCoefUpsert in) {
        UUID schoolId = TenantContext.get();
        SchoolClass schoolClass = classes.findByIdAndSchoolId(in.classId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));
        Subject subject = subjects.findByIdAndSchoolId(in.subjectId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Matière"));
        if (subject.getSubsystem() != null && !subject.getSubsystem().equalsIgnoreCase(schoolClass.getSubsystem())) {
            throw ApiException.badRequest("Cette matière appartient au sous-système " + subject.getSubsystem()
                    + " et ne peut pas être affectée à une classe " + schoolClass.getSubsystem());
        }
        SubjectClassCoef coefficient = coefs.findBySchoolIdAndSubjectIdAndClassId(schoolId, subject.getId(), schoolClass.getId())
                .orElseGet(() -> {
                    SubjectClassCoef fresh = new SubjectClassCoef();
                    fresh.setSchoolId(schoolId);
                    fresh.setSubjectId(subject.getId());
                    fresh.setClassId(schoolClass.getId());
                    return fresh;
                });
        coefficient.setCoef(in.coef());
        SubjectClassCoef saved = coefs.save(coefficient);
        syncCurrentSessionCurriculum(schoolClass, subject, in.coef());
        return classCoefView(schoolClass, subject, saved);
    }

    @Transactional
    public void deleteCoefficient(UUID classId, UUID subjectId) {
        UUID schoolId = TenantContext.get();
        SubjectClassCoef coefficient = coefs.findBySchoolIdAndSubjectIdAndClassId(schoolId, subjectId, classId)
                .orElseThrow(() -> ApiException.notFound("Affectation matière-classe"));
        coefs.delete(coefficient);
        UUID sessionId = currentSessionId(schoolId);
        if (sessionId != null) jdbc.update("DELETE FROM academic_curriculum_subject WHERE school_id=? AND academic_session_id=? AND class_id=? AND subject_id=?", schoolId, sessionId, classId, subjectId);
    }

    /** Keep the current session's curriculum in sync with the compatibility tab. */
    private void syncCurrentSessionCurriculum(SchoolClass schoolClass, Subject subject, int coefficient) {
        UUID schoolId = TenantContext.get(); UUID sessionId = currentSessionId(schoolId);
        if (sessionId == null) return;
        jdbc.update("""
                INSERT INTO academic_curriculum_subject(school_id,academic_session_id,class_id,subject_id,display_order,coefficient,max_score,mandatory,pass_threshold)
                VALUES (?,?,?, ?, (SELECT coalesce(max(display_order),0)+1 FROM academic_curriculum_subject x WHERE x.school_id=? AND x.academic_session_id=? AND x.class_id=?), ?,20,true,10)
                ON CONFLICT(school_id,academic_session_id,class_id,subject_id) DO UPDATE SET coefficient=excluded.coefficient, updated_at=now(), version=academic_curriculum_subject.version+1
                """, schoolId, sessionId, schoolClass.getId(), subject.getId(), schoolId, sessionId, schoolClass.getId(), coefficient);
    }

    private UUID currentSessionId(UUID schoolId) {
        return jdbc.query("SELECT id FROM academic_session WHERE school_id=? AND is_current ORDER BY start_date DESC LIMIT 1",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, schoolId);
    }

    private ClassCoefView classCoefView(SchoolClass schoolClass, Subject subject, SubjectClassCoef coefficient) {
        return new ClassCoefView(schoolClass.getId(), schoolClass.getName(), schoolClass.getSubsystem(),
                subject.getId(), subject.getCode(), coefficient.getCoef(), subject.getCoef());
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

    // ---- Session-versioned curriculum --------------------------------------

    @Transactional(readOnly = true)
    public CurriculumView curriculum(UUID academicSessionId, UUID classId) {
        UUID schoolId = TenantContext.get();
        Map<String, Object> session = jdbc.query("SELECT code, label FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? Map.of("code", rs.getString(1), "label", rs.getString(2)) : null,
                academicSessionId, schoolId);
        if (session == null) throw ApiException.notFound("Session académique");
        SchoolClass cls = classes.findByIdAndSchoolId(classId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));

        List<SubjectGroupView> groups = jdbc.query(
                "SELECT id, code, label->>'fr', label->>'en', display_order, show_subtotal, show_rank, average_policy, version "
              + "FROM academic_subject_group WHERE school_id=? AND academic_session_id=? ORDER BY display_order, code",
                (rs, n) -> new SubjectGroupView(rs.getObject(1, UUID.class), rs.getString(2),
                        labels(rs.getString(3), rs.getString(4)), rs.getInt(5), rs.getBoolean(6),
                        rs.getBoolean(7), rs.getString(8), rs.getLong(9)), schoolId, academicSessionId);

        List<CurriculumSubjectView> subjects = jdbc.query(
                "SELECT c.id, c.subject_id, s.code, COALESCE(s.label->>'fr', s.label->>'en', s.code), "
              + "c.group_id, g.code, c.display_order, c.coefficient, c.max_score, c.mandatory, c.pass_threshold, "
              + "c.show_subject_rank, c.remark_required, t.id, t.employee_id, t.employee_name, t.employee_code, "
              + "t.role, t.source, t.active, t.version, c.version "
              + "FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id "
              + "LEFT JOIN academic_subject_group g ON g.id=c.group_id "
              + "LEFT JOIN LATERAL (SELECT ast.id, ast.employee_id, e.name AS employee_name, e.code AS employee_code, "
              + "ast.role, ast.source, ast.active, ast.version FROM academic_class_subject_teacher ast "
              + "JOIN employee e ON e.id=ast.employee_id WHERE ast.school_id=? AND ast.academic_session_id=? "
              + "AND ast.class_id=? AND ast.subject_id=c.subject_id AND ast.active=true "
              + "ORDER BY CASE ast.role WHEN 'RESPONSIBLE' THEN 0 WHEN 'HOMEROOM' THEN 1 ELSE 2 END, ast.created_at LIMIT 1) t ON true "
              + "WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=? ORDER BY c.display_order, s.code",
                (rs, n) -> {
                    UUID teacherId = rs.getObject(14, UUID.class);
                    CurriculumTeacherView teacher = teacherId == null ? null : new CurriculumTeacherView(
                            rs.getObject(14, UUID.class), rs.getObject(15, UUID.class), rs.getString(16),
                            rs.getString(17), rs.getString(18), rs.getString(19), rs.getBoolean(20), rs.getLong(21));
                    return new CurriculumSubjectView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                            rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6),
                            rs.getInt(7), rs.getInt(8), rs.getBigDecimal(9), rs.getBoolean(10), rs.getBigDecimal(11),
                            rs.getBoolean(12), rs.getBoolean(13), teacher, rs.getLong(22));
                }, schoolId, academicSessionId, classId, schoolId, academicSessionId, classId);
        return new CurriculumView(academicSessionId, (String) session.get("code"), (String) session.get("label"),
                classId, cls.getName(), groups, subjects);
    }

    @Transactional
    public SubjectGroupView upsertCurriculumGroup(UUID id, SubjectGroupUpsert in) {
        UUID schoolId = TenantContext.get();
        assertSession(in.academicSessionId());
        String code = in.code().trim().toUpperCase();
        if (in.displayOrder() < 1) throw ApiException.badRequest("L'ordre du groupe doit être supérieur ou égal à 1");
        UUID existingId = jdbc.query("SELECT id FROM academic_subject_group WHERE school_id=? AND academic_session_id=? AND code=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, schoolId, in.academicSessionId(), code);
        if (existingId != null && (id == null || !existingId.equals(id))) {
            throw ApiException.conflict("Le code du groupe existe déjà dans cette session");
        }
        String fr = label(in.label(), "fr", code), en = label(in.label(), "en", fr);
        boolean subtotal = in.showSubtotal() == null || in.showSubtotal();
        boolean rank = in.showRank() != null && in.showRank();
        String policy = in.averagePolicy() == null || in.averagePolicy().isBlank()
                ? "WEIGHTED_COEFFICIENT" : in.averagePolicy().trim().toUpperCase();
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("INSERT INTO academic_subject_group(id,school_id,academic_session_id,code,label,display_order,show_subtotal,show_rank,average_policy) "
                      + "VALUES (?,?,?, ?, jsonb_build_object('fr',?,'en',?), ?,?,?,?)",
                    id, schoolId, in.academicSessionId(), code, fr, en, -1000000, subtotal, rank, policy);
        } else {
            Map<String, Object> current = jdbc.query("SELECT academic_session_id,version FROM academic_subject_group WHERE id=? AND school_id=?",
                    rs -> rs.next() ? Map.of("session", rs.getObject(1, UUID.class), "version", rs.getLong(2)) : null, id, schoolId);
            if (current == null) throw ApiException.notFound("Groupe de matières");
            if (!in.academicSessionId().equals(current.get("session"))) throw ApiException.badRequest("Le groupe n'appartient pas à cette session");
            assertVersion(in.version(), (Long) current.get("version"), "Le groupe de matières");
            int updated = jdbc.update("UPDATE academic_subject_group SET code=?,label=jsonb_build_object('fr',?,'en',?),display_order=-1000000,show_subtotal=?,show_rank=?,average_policy=?,version=version+1 WHERE id=? AND school_id=?",
                    code, fr, en, subtotal, rank, policy, id, schoolId);
            if (updated != 1) throw ApiException.conflict("Le groupe de matières a été modifié entre-temps");
        }
        reorderCurriculumGroups(schoolId, in.academicSessionId(), id, in.displayOrder());
        return curriculumGroup(id);
    }

    @Transactional
    public void deleteCurriculumGroup(UUID id) {
        UUID schoolId = TenantContext.get();
        int updated = jdbc.update("DELETE FROM academic_subject_group WHERE id=? AND school_id=?", id, schoolId);
        if (updated != 1) throw ApiException.notFound("Groupe de matières");
    }

    @Transactional
    public CurriculumSubjectView upsertCurriculumSubject(CurriculumSubjectUpsert in) {
        UUID schoolId = TenantContext.get();
        assertSession(in.academicSessionId());
        SchoolClass cls = classes.findByIdAndSchoolId(in.classId(), schoolId).orElseThrow(() -> ApiException.notFound("Classe"));
        Subject subject = subjects.findByIdAndSchoolId(in.subjectId(), schoolId).orElseThrow(() -> ApiException.notFound("Matière"));
        if (subject.getSubsystem() != null && !subject.getSubsystem().equalsIgnoreCase(cls.getSubsystem())) {
            throw ApiException.badRequest("Cette matière appartient au sous-système " + subject.getSubsystem() + " et ne peut pas être affectée à " + cls.getSubsystem());
        }
        if (in.groupId() != null) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM academic_subject_group WHERE id=? AND school_id=? AND academic_session_id=?",
                    Integer.class, in.groupId(), schoolId, in.academicSessionId());
            if (count == null || count == 0) throw ApiException.badRequest("Le groupe sélectionné n'appartient pas à cette session");
        }
        Map<String, Object> current = jdbc.query("SELECT id,display_order,coefficient,max_score,mandatory,pass_threshold,show_subject_rank,remark_required,version "
                        + "FROM academic_curriculum_subject WHERE school_id=? AND academic_session_id=? AND class_id=? AND subject_id=?",
                rs -> rs.next() ? Map.of("id", rs.getObject(1, UUID.class), "displayOrder", rs.getInt(2), "coefficient", rs.getInt(3),
                        "maxScore", rs.getBigDecimal(4), "mandatory", rs.getBoolean(5), "passThreshold", rs.getBigDecimal(6),
                        "showRank", rs.getBoolean(7), "remarkRequired", rs.getBoolean(8), "version", rs.getLong(9)) : null,
                schoolId, in.academicSessionId(), in.classId(), in.subjectId());
        int order = in.displayOrder() == null ? current == null ? nextCurriculumOrder(schoolId, in.academicSessionId(), in.classId()) : (Integer) current.get("displayOrder") : in.displayOrder();
        int coefficient = in.coefficient() == null ? current == null ? Math.max(1, subject.getCoef()) : (Integer) current.get("coefficient") : in.coefficient();
        BigDecimal maxScore = in.maxScore() == null ? current == null ? BigDecimal.valueOf(20) : (BigDecimal) current.get("maxScore") : in.maxScore();
        boolean mandatory = in.mandatory() == null ? current == null || (Boolean) current.get("mandatory") : in.mandatory();
        BigDecimal threshold = in.passThreshold() == null ? current == null ? BigDecimal.TEN : (BigDecimal) current.get("passThreshold") : in.passThreshold();
        boolean showRank = in.showSubjectRank() == null ? current == null || (Boolean) current.get("showRank") : in.showSubjectRank();
        boolean remarkRequired = in.remarkRequired() == null ? current != null && (Boolean) current.get("remarkRequired") : in.remarkRequired();
        if (order < 1 || coefficient < 1) throw ApiException.badRequest("L'ordre et le coefficient doivent être supérieurs ou égaux à 1");
        if (maxScore.signum() <= 0 || threshold.signum() < 0 || threshold.compareTo(maxScore) > 0) throw ApiException.badRequest("Le barème et le seuil de réussite sont invalides");
        if (current == null) {
            jdbc.update("INSERT INTO academic_curriculum_subject(school_id,academic_session_id,class_id,subject_id,group_id,display_order,coefficient,max_score,mandatory,pass_threshold,show_subject_rank,remark_required) "
                      + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    schoolId, in.academicSessionId(), in.classId(), in.subjectId(), in.groupId(), order, coefficient, maxScore, mandatory, threshold, showRank, remarkRequired);
        } else {
            assertVersion(in.version(), (Long) current.get("version"), "La configuration de matière");
            int updated = jdbc.update("UPDATE academic_curriculum_subject SET group_id=?,display_order=?,coefficient=?,max_score=?,mandatory=?,pass_threshold=?,show_subject_rank=?,remark_required=?,updated_at=now(),version=version+1 WHERE id=? AND school_id=?",
                    in.groupId(), order, coefficient, maxScore, mandatory, threshold, showRank, remarkRequired, current.get("id"), schoolId);
            if (updated != 1) throw ApiException.conflict("La configuration de matière a été modifiée entre-temps");
        }
        reorderCurriculumSubjects(schoolId, in.academicSessionId(), in.classId(), current == null
                ? jdbc.queryForObject("SELECT id FROM academic_curriculum_subject WHERE school_id=? AND academic_session_id=? AND class_id=? AND subject_id=?",
                UUID.class, schoolId, in.academicSessionId(), in.classId(), in.subjectId())
                : (UUID) current.get("id"), order);
        syncLegacyCoefficientIfCurrent(in.academicSessionId(), in.classId(), in.subjectId(), coefficient);
        return curriculum(in.academicSessionId(), in.classId()).subjects().stream()
                .filter(x -> x.subjectId().equals(in.subjectId())).findFirst()
                .orElseThrow(() -> ApiException.conflict("La matière n'a pas pu être chargée après enregistrement"));
    }

    @Transactional
    public void deleteCurriculumSubject(UUID academicSessionId, UUID classId, UUID subjectId) {
        UUID schoolId = TenantContext.get();
        assertSession(academicSessionId);
        int updated = jdbc.update("DELETE FROM academic_curriculum_subject WHERE school_id=? AND academic_session_id=? AND class_id=? AND subject_id=?",
                schoolId, academicSessionId, classId, subjectId);
        if (updated != 1) throw ApiException.notFound("Affectation matière-classe");
        if (academicSessionId.equals(currentSessionId(schoolId))) {
            coefs.findBySchoolIdAndSubjectIdAndClassId(schoolId, subjectId, classId).ifPresent(coefs::delete);
        }
    }

    @Transactional
    public CurriculumTeacherView upsertCurriculumTeacher(CurriculumTeacherUpsert in) {
        UUID schoolId = TenantContext.get();
        assertSession(in.academicSessionId());
        SchoolClass cls = classes.findByIdAndSchoolId(in.classId(), schoolId).orElseThrow(() -> ApiException.notFound("Classe"));
        employees.findByIdAndSchoolId(in.employeeId(), schoolId).orElseThrow(() -> ApiException.notFound("Enseignant"));
        bindTeacherSection(in.employeeId(), cls.getLevel());
        String role = in.role().trim().toUpperCase();
        if (!List.of("RESPONSIBLE", "ASSISTANT", "HOMEROOM").contains(role)) throw ApiException.badRequest("Rôle enseignant invalide");
        String source = in.source() == null || in.source().isBlank() ? "MANUAL" : in.source().trim().toUpperCase();
        if (!List.of("TIMETABLE", "HOMEROOM", "MANUAL").contains(source)) throw ApiException.badRequest("Source d'affectation invalide");
        if (in.effectiveFrom() != null && in.effectiveTo() != null && in.effectiveFrom().isAfter(in.effectiveTo())) throw ApiException.badRequest("La période d'affectation est invalide");
        if ("RESPONSIBLE".equals(role)) jdbc.update("UPDATE academic_class_subject_teacher SET active=false,updated_at=now(),version=version+1 WHERE school_id=? AND academic_session_id=? AND class_id=? AND subject_id=? AND role='RESPONSIBLE' AND employee_id<>?",
                schoolId, in.academicSessionId(), in.classId(), in.subjectId(), in.employeeId());
        UUID id = jdbc.query("SELECT id FROM academic_class_subject_teacher WHERE school_id=? AND academic_session_id=? AND class_id=? AND subject_id=? AND employee_id=? AND role=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, schoolId, in.academicSessionId(), in.classId(), in.subjectId(), in.employeeId(), role);
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("INSERT INTO academic_class_subject_teacher(id,school_id,academic_session_id,class_id,subject_id,employee_id,role,effective_from,effective_to,source,active) VALUES (?,?,?,?,?,?,?,?,?,?,true)",
                    id, schoolId, in.academicSessionId(), in.classId(), in.subjectId(), in.employeeId(), role, in.effectiveFrom(), in.effectiveTo(), source);
        } else {
            Map<String, Object> current = jdbc.query("SELECT version FROM academic_class_subject_teacher WHERE id=? AND school_id=?",
                    rs -> rs.next() ? Map.of("version", rs.getLong(1)) : null, id, schoolId);
            assertVersion(in.version(), current == null ? null : (Long) current.get("version"), "L'affectation de l'enseignant");
            jdbc.update("UPDATE academic_class_subject_teacher SET effective_from=?,effective_to=?,source=?,active=true,updated_at=now(),version=version+1 WHERE id=? AND school_id=?",
                    in.effectiveFrom(), in.effectiveTo(), source, id, schoolId);
        }
        return curriculumTeacher(id);
    }

    @Transactional
    public void deleteCurriculumTeacher(UUID id) {
        int updated = jdbc.update("DELETE FROM academic_class_subject_teacher WHERE id=? AND school_id=?", id, TenantContext.get());
        if (updated != 1) throw ApiException.notFound("Affectation de l'enseignant");
    }

    private SubjectGroupView curriculumGroup(UUID id) {
        return jdbc.query("SELECT id,code,label->>'fr',label->>'en',display_order,show_subtotal,show_rank,average_policy,version FROM academic_subject_group WHERE id=? AND school_id=?",
                rs -> rs.next() ? new SubjectGroupView(rs.getObject(1, UUID.class), rs.getString(2), labels(rs.getString(3), rs.getString(4)), rs.getInt(5), rs.getBoolean(6), rs.getBoolean(7), rs.getString(8), rs.getLong(9)) : null,
                id, TenantContext.get());
    }

    private CurriculumTeacherView curriculumTeacher(UUID id) {
        return jdbc.query("SELECT t.id,t.employee_id,e.name,e.code,t.role,t.source,t.active,t.version FROM academic_class_subject_teacher t JOIN employee e ON e.id=t.employee_id WHERE t.id=? AND t.school_id=?",
                rs -> rs.next() ? new CurriculumTeacherView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getLong(8)) : null,
                id, TenantContext.get());
    }

    private int nextCurriculumOrder(UUID schoolId, UUID sessionId, UUID classId) {
        Integer value = jdbc.queryForObject("SELECT coalesce(max(display_order),0)+1 FROM academic_curriculum_subject WHERE school_id=? AND academic_session_id=? AND class_id=?",
                Integer.class, schoolId, sessionId, classId);
        return value == null ? 1 : value;
    }

    private void reorderCurriculumGroups(UUID schoolId, UUID sessionId, UUID targetId, int requestedOrder) {
        List<UUID> ids = jdbc.query("SELECT id,display_order FROM academic_subject_group WHERE school_id=? AND academic_session_id=? ORDER BY display_order,id",
                (rs, rowNum) -> Map.entry(rs.getObject(1, UUID.class), rs.getInt(2)), schoolId, sessionId)
                .stream().map(Map.Entry::getKey).filter(id -> !id.equals(targetId)).collect(Collectors.toCollection(ArrayList::new));
        int index = Math.max(0, Math.min(requestedOrder - 1, ids.size()));
        ids.add(index, targetId);
        jdbc.update("UPDATE academic_subject_group SET display_order=-display_order-1000000 WHERE school_id=? AND academic_session_id=?", schoolId, sessionId);
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("UPDATE academic_subject_group SET display_order=? WHERE id=? AND school_id=?", i + 1, ids.get(i), schoolId);
        }
    }

    private void reorderCurriculumSubjects(UUID schoolId, UUID sessionId, UUID classId, UUID targetId, int requestedOrder) {
        List<UUID> ids = jdbc.query("SELECT id,display_order FROM academic_curriculum_subject WHERE school_id=? AND academic_session_id=? AND class_id=? ORDER BY display_order,id",
                (rs, rowNum) -> Map.entry(rs.getObject(1, UUID.class), rs.getInt(2)), schoolId, sessionId, classId)
                .stream().map(Map.Entry::getKey).filter(id -> !id.equals(targetId)).collect(Collectors.toCollection(ArrayList::new));
        int index = Math.max(0, Math.min(requestedOrder - 1, ids.size()));
        ids.add(index, targetId);
        jdbc.update("UPDATE academic_curriculum_subject SET display_order=-display_order-1000000 WHERE school_id=? AND academic_session_id=? AND class_id=?", schoolId, sessionId, classId);
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("UPDATE academic_curriculum_subject SET display_order=? WHERE id=? AND school_id=?", i + 1, ids.get(i), schoolId);
        }
    }

    private void syncLegacyCoefficientIfCurrent(UUID sessionId, UUID classId, UUID subjectId, int coefficient) {
        UUID schoolId = TenantContext.get();
        if (!sessionId.equals(currentSessionId(schoolId))) return;
        SubjectClassCoef row = coefs.findBySchoolIdAndSubjectIdAndClassId(schoolId, subjectId, classId).orElseGet(() -> {
            SubjectClassCoef fresh = new SubjectClassCoef(); fresh.setSchoolId(schoolId); fresh.setSubjectId(subjectId); fresh.setClassId(classId); return fresh;
        });
        row.setCoef(coefficient); coefs.save(row);
    }

    private void assertSession(UUID sessionId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM academic_session WHERE id=? AND school_id=?", Integer.class, sessionId, TenantContext.get());
        if (count == null || count == 0) throw ApiException.notFound("Session académique");
    }

    private static void assertVersion(Long requested, Long current, String label) {
        if (requested != null && (current == null || !requested.equals(current))) throw ApiException.conflict(label + " a été modifiée par un autre utilisateur");
    }

    private static String label(Map<String, String> labels, String key, String fallback) {
        if (labels == null) return fallback;
        String value = labels.get(key); return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static Map<String, String> labels(String fr, String en) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        if (fr != null && !fr.isBlank()) result.put("fr", fr);
        if (en != null && !en.isBlank()) result.put("en", en);
        return result;
    }

    private Subject findOrCreateSubject(UUID schoolId, String rawCode, String subsystem, String rawLabel, Set<String> created) {
        String code = (rawCode.trim().length() > 8 ? rawCode.trim().substring(0, 8) : rawCode.trim()).toUpperCase();
        return subjects.findBySchoolIdOrderByCode(schoolId).stream()
                // A null subsystem means the catalog subject is shared by both
                // systems; it is still the same course when a class-level FR/EN
                // coefficient is imported.
                .filter(s -> code.equals(s.getCode())
                        && (s.getSubsystem() == null
                        || java.util.Objects.equals(subsystem, normSubsystem(s.getSubsystem()))))
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
