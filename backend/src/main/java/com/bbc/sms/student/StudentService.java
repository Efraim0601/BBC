package com.bbc.sms.student;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.ParcoursContext.Scope;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.SetupService;
import com.bbc.sms.student.dto.StudentDtos.*;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class StudentService {

    private final StudentRepository repo;
    private final SchoolClassRepository classes;
    private final SetupService setup;

    public StudentService(StudentRepository repo, SchoolClassRepository classes, SetupService setup) {
        this.repo = repo;
        this.classes = classes;
        this.setup = setup;
    }

    @Transactional(readOnly = true)
    public List<StudentView> list(String className) {
        UUID schoolId = TenantContext.get();
        List<Student> rows = (className == null || className.isBlank())
                ? repo.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId)
                : repo.findBySchoolIdAndClassNameAndActiveTrueOrderByLastNameAsc(schoolId, className);
        Scope scope = ParcoursContext.get();
        return rows.stream()
                .filter(s -> inScope(scope, s.getLevel(), s.getSubsystem()))
                .map(this::toView).toList();
    }

    /**
     * Keep a row when no parcours scope is active, or when its level+subsystem match it.
     * Unassigned rows (null level and/or subsystem) stay visible in every parcours so
     * they do not become "ghost" students after create-without-class.
     */
    public static boolean inScope(Scope scope, String level, String subsystem) {
        if (scope == null) return true;
        if (level == null || level.isBlank() || subsystem == null || subsystem.isBlank()) return true;
        return scope.level().equalsIgnoreCase(level)
                && scope.subsystem().equalsIgnoreCase(subsystem);
    }

    @Transactional(readOnly = true)
    public StudentView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public StudentView create(StudentUpsert in) {
        UUID schoolId = TenantContext.get();
        Student s = new Student();
        s.setSchoolId(schoolId);
        s.setMatricule(nextMatricule(schoolId));
        s.setPhotoHue(ThreadLocalRandom.current().nextInt(0, 360));
        apply(s, in);
        return toView(repo.save(s));
    }

    @Transactional
    public StudentView update(UUID id, StudentUpsert in) {
        Student s = find(id);
        apply(s, in);
        return toView(repo.save(s));
    }

    @Transactional
    public void delete(UUID id) {
        Student s = find(id);
        s.setActive(false);   // soft delete — keeps financial/academic history intact
        repo.save(s);
    }

    /**
     * Bulk-create students into one existing class. Each row is validated on its
     * own: a bad row is skipped and reported, the rest still import. Matricules
     * are handed out from a local running counter so a whole batch stays unique
     * within itself (a COUNT-based query can't see rows not yet flushed).
     */
    @Transactional
    public StudentImportResult importForClass(StudentImportRequest in) {
        UUID schoolId = TenantContext.get();
        SchoolClass cls = resolveImportClass(in, schoolId);

        long seq = repo.countBySchoolIdAndActiveTrue(schoolId) + 1001;
        Set<String> usedMatricules = new HashSet<>();
        Set<String> usedNiu = new HashSet<>();
        List<StudentImportError> errors = new ArrayList<>();
        int created = 0;
        int lineNo = 0;

        for (StudentImportRow row : in.rows()) {
            lineNo++;
            String[] fl = resolveName(row);   // [lastName, firstName]
            String lastName = fl[0], firstName = fl[1];
            String label = (lastName + " " + firstName).trim();
            try {
                if (firstName.isBlank() || lastName.isBlank()) {
                    throw new IllegalArgumentException("Nom et prénom obligatoires");
                }
                String sex = blankToNull(row.sex());
                if (sex != null) {
                    sex = sex.trim().toUpperCase();
                    if (!sex.equals("M") && !sex.equals("F")) {
                        throw new IllegalArgumentException("Sexe invalide (attendu M ou F)");
                    }
                }
                // Skip pupils already on file for this NIU so re-importing a register
                // is idempotent (the source NIU can even repeat within one batch).
                String niu = blankToNull(row.niu());
                if (niu != null) {
                    if (usedNiu.contains(niu) || repo.existsBySchoolIdAndNiuAndActiveTrue(schoolId, niu)) {
                        throw new IllegalArgumentException("NIU déjà présent (" + niu + ") — ignoré");
                    }
                    usedNiu.add(niu);
                }

                String matricule;
                do { matricule = "BBC-" + seq++; }
                while (usedMatricules.contains(matricule) || repo.existsBySchoolIdAndMatricule(schoolId, matricule));
                usedMatricules.add(matricule);

                Student s = new Student();
                s.setSchoolId(schoolId);
                s.setMatricule(matricule);
                s.setNiu(niu);
                s.setPhotoHue(ThreadLocalRandom.current().nextInt(0, 360));
                s.setFirstName(firstName);
                s.setLastName(lastName);
                s.setSex(sex);
                s.setDob(row.dob());
                s.setBirthplace(blankToNull(row.birthplace()));
                s.setRepeats(row.repeats());
                s.setClassId(cls.getId());
                s.setClassName(cls.getName());
                s.setSubsystem(cls.getSubsystem());
                s.setLevel(cls.getLevel());
                s.setParentName(blankToNull(row.parentName()));
                s.setParentPhone(blankToNull(row.parentPhone()));
                s.setFatherName(blankToNull(row.fatherName()));
                s.setFatherPhone(blankToNull(row.fatherPhone()));
                s.setFatherEmail(blankToNull(row.fatherEmail()));
                s.setMotherName(blankToNull(row.motherName()));
                s.setMotherPhone(blankToNull(row.motherPhone()));
                s.setMotherEmail(blankToNull(row.motherEmail()));
                s.setGuardianName(blankToNull(row.guardianName()));
                s.setGuardianPhone(blankToNull(row.guardianPhone()));
                s.setGuardianEmail(blankToNull(row.guardianEmail()));
                s.setGuardianRelation(blankToNull(row.guardianRelation()));
                // Same legacy contact rule as manual creation: père → mère → tuteur.
                syncPrimaryContact(s);
                repo.save(s);
                created++;
            } catch (RuntimeException ex) {
                errors.add(new StudentImportError(lineNo, label.isBlank() ? "?" : label, ex.getMessage()));
            }
        }
        return new StudentImportResult(created, errors.size(), errors);
    }

    /** Bind the batch to an existing class, or find-or-create one from the newClass spec. */
    private SchoolClass resolveImportClass(StudentImportRequest in, UUID schoolId) {
        if (in.classId() != null) {
            return classes.findByIdAndSchoolId(in.classId(), schoolId)
                    .orElseThrow(() -> ApiException.badRequest("Classe inconnue"));
        }
        if (in.newClass() != null) {
            return setup.findOrCreateClass(in.newClass().name(), in.newClass().subsystem(), in.newClass().level());
        }
        throw ApiException.badRequest("Classe cible obligatoire");
    }

    /**
     * Resolve a row's last/first name. Prefer the split fields; otherwise split the
     * combined "Nom et Prénom" column — official registers write the FAMILY name (in
     * caps) first, then the given names, sometimes padded with " - -" separators.
     * First whitespace token → last name, the rest → first name.
     */
    private static String[] resolveName(StudentImportRow row) {
        String last = row.lastName() == null ? "" : row.lastName().trim();
        String first = row.firstName() == null ? "" : row.firstName().trim();
        if (!last.isEmpty() || !first.isEmpty()) return new String[]{last, first};

        String combined = row.name() == null ? "" : row.name().trim();
        combined = combined.replaceAll("(\\s+-)+\\s*$", "").trim();   // drop trailing " - -" padding
        combined = combined.replaceAll("\\s+", " ");
        if (combined.isEmpty()) return new String[]{"", ""};
        int sp = combined.indexOf(' ');
        if (sp < 0) return new String[]{combined, "-"};              // single token — keep name non-null
        return new String[]{combined.substring(0, sp).trim(), combined.substring(sp + 1).trim()};
    }

    private Student find(UUID id) {
        return repo.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Élève"));
    }

    private void apply(Student s, StudentUpsert in) {
        UUID schoolId = TenantContext.get();
        s.setFirstName(in.firstName());
        s.setLastName(in.lastName());
        s.setNiu(blankToNull(in.niu()));
        s.setSex(blankToNull(in.sex()));   // "" would violate CHECK (sex IN ('M','F'))
        s.setDob(in.dob());
        s.setBirthplace(blankToNull(in.birthplace()));
        s.setRepeats(in.repeats());

        if (in.classId() != null) {
            // Authoritative path: bind a real class and copy its name/subsystem/level so
            // free typing can never create phantom classes (review issue #1).
            SchoolClass cls = classes.findByIdAndSchoolId(in.classId(), schoolId)
                    .orElseThrow(() -> ApiException.badRequest("Classe inconnue"));
            s.setClassId(cls.getId());
            s.setClassName(cls.getName());
            s.setSubsystem(cls.getSubsystem());
            s.setLevel(cls.getLevel());
        } else {
            // Unassigned student (no class yet) — stamp the active parcours when the
            // client did not send level/subsystem, so the pupil stays findable.
            s.setClassId(null);
            s.setClassName(blankToNull(in.className()));
            String sub = blankToNull(in.subsystem());
            String lvl = blankToNull(in.level());
            Scope scope = ParcoursContext.get();
            if (sub == null && scope != null) sub = scope.subsystem();
            if (lvl == null && scope != null) lvl = scope.level();
            s.setSubsystem(sub);
            s.setLevel(lvl);
        }

        s.setParentName(in.parentName());
        s.setParentPhone(in.parentPhone());
        s.setFatherName(blankToNull(in.fatherName()));
        s.setFatherPhone(blankToNull(in.fatherPhone()));
        s.setFatherEmail(blankToNull(in.fatherEmail()));
        s.setMotherName(blankToNull(in.motherName()));
        s.setMotherPhone(blankToNull(in.motherPhone()));
        s.setMotherEmail(blankToNull(in.motherEmail()));
        s.setGuardianName(blankToNull(in.guardianName()));
        s.setGuardianPhone(blankToNull(in.guardianPhone()));
        s.setGuardianEmail(blankToNull(in.guardianEmail()));
        s.setGuardianRelation(blankToNull(in.guardianRelation()));
        // Keep legacy primary contact in sync for SMS / older screens.
        syncPrimaryContact(s);
    }

    /** Prefer father → mother → guardian → explicit parentName for the legacy fields. */
    private static void syncPrimaryContact(Student s) {
        if (blankToNull(s.getParentName()) == null) {
            if (s.getFatherName() != null) {
                s.setParentName(s.getFatherName());
                if (s.getParentPhone() == null) s.setParentPhone(s.getFatherPhone());
            } else if (s.getMotherName() != null) {
                s.setParentName(s.getMotherName());
                if (s.getParentPhone() == null) s.setParentPhone(s.getMotherPhone());
            } else if (s.getGuardianName() != null) {
                s.setParentName(s.getGuardianName());
                if (s.getParentPhone() == null) s.setParentPhone(s.getGuardianPhone());
            }
        }
    }

    /** Turn empty/blank input into null so optional, CHECK-constrained columns stay valid. */
    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String nextMatricule(UUID schoolId) {
        long n = repo.countBySchoolIdAndActiveTrue(schoolId) + 1001;
        String code;
        do {
            code = "BBC-" + n++;
        } while (repo.existsBySchoolIdAndMatricule(schoolId, code));
        return code;
    }

    private StudentView toView(Student s) {
        String name = s.getLastName().toUpperCase() + " " + s.getFirstName();
        return new StudentView(s.getId(), s.getMatricule(), s.getNiu(), s.getFirstName(), s.getLastName(),
                name, s.getSex(), s.getDob(), s.getBirthplace(), s.isRepeats(),
                s.getClassId(), s.getClassName(), s.getSubsystem(), s.getLevel(),
                s.getParentName(), s.getParentPhone(),
                s.getFatherName(), s.getFatherPhone(), s.getFatherEmail(),
                s.getMotherName(), s.getMotherPhone(), s.getMotherEmail(),
                s.getGuardianName(), s.getGuardianPhone(), s.getGuardianEmail(), s.getGuardianRelation(),
                s.getPhotoHue());
    }
}
