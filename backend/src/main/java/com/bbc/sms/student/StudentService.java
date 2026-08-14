package com.bbc.sms.student;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.foundation.enrollment.EnrollmentService;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.ParcoursContext.Scope;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.SetupService;
import com.bbc.sms.student.dto.StudentDtos.*;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.time.LocalDate;

@Service
public class StudentService {

    private final StudentRepository repo;
    private final SchoolClassRepository classes;
    private final SetupService setup;
    private final TeacherScopeService teacherScope;
    private final EnrollmentService enrollmentService;
    private final AuthorizationPolicyService policy;
    private final JdbcTemplate jdbc;

    public StudentService(StudentRepository repo, SchoolClassRepository classes, SetupService setup,
                          TeacherScopeService teacherScope, EnrollmentService enrollmentService,
                          AuthorizationPolicyService policy, JdbcTemplate jdbc) {
        this.repo = repo;
        this.classes = classes;
        this.setup = setup;
        this.teacherScope = teacherScope;
        this.enrollmentService = enrollmentService;
        this.policy = policy;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<? extends DirectoryView> list(String className) {
        UUID schoolId = TenantContext.get();
        if (teacherScope.restricted()) return teacherList(className, schoolId);
        List<Student> rows = (className == null || className.isBlank())
                ? repo.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId)
                : repo.findBySchoolIdAndClassNameAndActiveTrueOrderByLastNameAsc(schoolId, className);
        Scope scope = ParcoursContext.get();
        // Un professeur principal ne voit que les élèves de ses classes.
        Set<UUID> allowed = teacherScope.allowedClassIds();
        return rows.stream()
                .filter(s -> allowed == null || (s.getClassId() != null && allowed.contains(s.getClassId())))
                .filter(s -> inScope(scope, s.getLevel(), s.getSubsystem()))
                .filter(s -> policy.decide("STUDENT_DIRECTORY_VIEW", policyContext(s, null, null)).allowed())
                .map(this::toView).toList();
    }

    /**
     * Academic screens use the active enrollment in the requested session and
     * class as their roster. Student.className/classId is only a current legacy
     * projection and must not decide who appears in a historical or future
     * session bulletin/PV.
     */
    @Transactional(readOnly = true)
    public List<? extends DirectoryView> roster(UUID sessionId, UUID classId) {
        List<com.bbc.sms.foundation.enrollment.EnrollmentDtos.EnrollmentView> active =
                enrollmentService.roster(sessionId, classId);
        if (active.isEmpty()) return List.of();

        Collection<UUID> studentIds = active.stream().map(e -> e.studentId()).toList();
        Map<UUID, Student> students = new HashMap<>();
        for (Student student : repo.findBySchoolIdAndIdInAndActiveTrue(TenantContext.get(), studentIds)) {
            students.put(student.getId(), student);
        }
        Scope scope = ParcoursContext.get();
        boolean teacher = teacherScope.restricted();
        LocalDate rosterDate = sessionStartDate(sessionId);
        return active.stream()
                .filter(e -> inScope(scope, e.level(), e.subsystem()))
                .map(e -> {
                    Student student = students.get(e.studentId());
                    if (student == null || !policy.decide("STUDENT_DIRECTORY_VIEW",
                            policyContext(student, sessionId, rosterDate, e.classId())).allowed()) return null;
                    return teacher
                            ? toTeacherView(student, e.classId(), e.className(), e.subsystem(), e.level())
                            : toView(student, e.classId(), e.className(), e.subsystem(), e.level());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
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
    public Object get(UUID id) {
        Student student = requireAction(id, "STUDENT_PROFILE_VIEW");
        if (teacherScope.restricted()) {
            EnrollmentProjection enrollment = activeEnrollment(student.getId(), currentSessionId(TenantContext.get()),
                    effectiveDate(currentSessionId(TenantContext.get())));
            if (enrollment == null) {
                throw ApiException.coded(org.springframework.http.HttpStatus.FORBIDDEN,
                        "ENROLLMENT_SCOPE_MISMATCH", "Aucune inscription active ne correspond à votre périmètre.");
            }
            return toTeacherView(student, enrollment.classId(), enrollment.className(),
                    enrollment.subsystem(), enrollment.level());
        }
        return toView(student);
    }

    @Transactional
    public StudentView create(StudentUpsert in) {
        policy.require("STUDENT_PROFILE_CREATE", PolicyResourceContext.empty().forSchool(TenantContext.get()));
        UUID schoolId = TenantContext.get();
        Student s = new Student();
        s.setSchoolId(schoolId);
        s.setMatricule(nextMatricule(schoolId));
        s.setPhotoHue(ThreadLocalRandom.current().nextInt(0, 360));
        apply(s, in);
        s = repo.saveAndFlush(s);
        enrollmentService.syncCurrent(s);
        return toView(s);
    }

    @Transactional
    public StudentView update(UUID id, StudentUpsert in) {
        Student s = requireAction(id, "STUDENT_PROFILE_EDIT");
        apply(s, in);
        s = repo.saveAndFlush(s);
        enrollmentService.syncCurrent(s);
        return toView(s);
    }

    @Transactional
    public void delete(UUID id) {
        Student s = requireAction(id, "STUDENT_PROFILE_DEACTIVATE");
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
        policy.require("STUDENT_IMPORT", PolicyResourceContext.empty().forSchool(TenantContext.get()));
        UUID schoolId = TenantContext.get();
        SchoolClass cls = resolveImportClass(in, schoolId);
        teacherScope.assertClass(cls.getId());

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
                s = repo.saveAndFlush(s);
                enrollmentService.syncCurrent(s);
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
        return toView(s, s.getClassId(), s.getClassName(), s.getSubsystem(), s.getLevel());
    }

    private StudentView toView(Student s, UUID classId, String className, String subsystem, String level) {
        String name = s.getLastName().toUpperCase() + " " + s.getFirstName();
        return new StudentView(s.getId(), s.getMatricule(), s.getNiu(), s.getFirstName(), s.getLastName(),
                name, s.getSex(), s.getDob(), s.getBirthplace(), s.isRepeats(),
                classId, className, subsystem, level,
                s.getParentName(), s.getParentPhone(),
                s.getFatherName(), s.getFatherPhone(), s.getFatherEmail(),
                s.getMotherName(), s.getMotherPhone(), s.getMotherEmail(),
                s.getGuardianName(), s.getGuardianPhone(), s.getGuardianEmail(), s.getGuardianRelation(),
                s.getPhotoHue());
    }

    private StudentTeacherView toTeacherView(Student s, UUID classId, String className,
                                             String subsystem, String level) {
        String name = s.getLastName().toUpperCase() + " " + s.getFirstName();
        return new StudentTeacherView(s.getId(), s.getMatricule(), s.getNiu(), s.getFirstName(),
                s.getLastName(), name, s.getSex(), s.getDob(), s.isRepeats(), classId,
                className, subsystem, level, s.getPhotoHue());
    }

    /** Query-backed teacher directory: enrollment and class metadata are authoritative. */
    private List<StudentTeacherView> teacherList(String className, UUID schoolId) {
        UUID sessionId = currentSessionId(schoolId);
        if (sessionId == null) return List.of();
        LocalDate date = effectiveDate(sessionId);
        Set<UUID> allowedClasses = teacherScope.allowedClassIds(sessionId, date);
        if (allowedClasses.isEmpty()) return List.of();
        ParcoursContext.Scope scope = ParcoursContext.get();
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT e.student_id,e.school_class_id,c.name,c.subsystem,c.level
                  FROM student_enrollment e
                  JOIN student st ON st.id=e.student_id AND st.school_id=e.school_id AND st.active=true
                  JOIN school_class c ON c.id=e.school_class_id AND c.school_id=e.school_id
                 WHERE e.school_id=? AND e.academic_session_id=? AND e.status='ACTIVE'
                   AND e.enrolled_on<=? AND (e.exited_on IS NULL OR e.exited_on>=?)
                """);
        List<Object> args = new ArrayList<>(List.of(schoolId, sessionId, date, date));
        appendUuidIn(sql, args, "e.school_class_id", allowedClasses);
        if (className != null && !className.isBlank()) { sql.append(" AND c.name=?"); args.add(className.trim()); }
        if (scope != null) {
            sql.append(" AND lower(c.level)=lower(?) AND lower(c.subsystem)=lower(?)");
            args.add(scope.level()); args.add(scope.subsystem());
        }
        sql.append(" ORDER BY lower(c.name), lower(st.last_name), lower(st.first_name)");
        List<EnrollmentProjection> projections = jdbc.query(sql.toString(), (rs, n) -> new EnrollmentProjection(
                rs.getObject("student_id", UUID.class), rs.getObject("school_class_id", UUID.class),
                rs.getString("name"), rs.getString("subsystem"), rs.getString("level")), args.toArray());
        Map<UUID, Student> students = new HashMap<>();
        for (Student student : repo.findBySchoolIdAndIdInAndActiveTrue(schoolId,
                projections.stream().map(EnrollmentProjection::studentId).toList())) students.put(student.getId(), student);
        return projections.stream()
                .filter(p -> students.containsKey(p.studentId()))
                .filter(p -> policy.decide("STUDENT_DIRECTORY_VIEW",
                        policyContext(students.get(p.studentId()), sessionId, date, p.classId())).allowed())
                .map(p -> toTeacherView(students.get(p.studentId()), p.classId(), p.className(),
                        p.subsystem(), p.level()))
                .toList();
    }

    private static void appendUuidIn(StringBuilder sql, List<Object> args, String column, Set<UUID> ids) {
        sql.append(" AND ").append(column).append(" IN (")
                .append("?,".repeat(Math.max(0, ids.size() - 1))).append("?)");
        args.addAll(ids);
    }

    private EnrollmentProjection activeEnrollment(UUID studentId, UUID sessionId, LocalDate date) {
        if (sessionId == null) return null;
        List<EnrollmentProjection> rows = jdbc.query("""
                SELECT e.student_id,e.school_class_id,c.name,c.subsystem,c.level
                  FROM student_enrollment e
                  LEFT JOIN school_class c ON c.id=e.school_class_id AND c.school_id=e.school_id
                 WHERE e.school_id=? AND e.student_id=? AND e.academic_session_id=? AND e.status='ACTIVE'
                   AND e.enrolled_on<=? AND (e.exited_on IS NULL OR e.exited_on>=?)
                 ORDER BY e.enrolled_on DESC,e.created_at DESC LIMIT 1
                """, (rs, n) -> new EnrollmentProjection(rs.getObject("student_id", UUID.class),
                rs.getObject("school_class_id", UUID.class), rs.getString("name"),
                rs.getString("subsystem"), rs.getString("level")), TenantContext.get(), studentId,
                sessionId, date, date);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private LocalDate effectiveDate(UUID sessionId) {
        if (sessionId == null) return LocalDate.now();
        List<List<LocalDate>> bounds = jdbc.query("SELECT start_date,end_date FROM academic_session WHERE id=? AND school_id=?",
                (rs, n) -> List.of(rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class)),
                sessionId, TenantContext.get());
        if (bounds.isEmpty()) return LocalDate.now();
        LocalDate now = LocalDate.now();
        List<LocalDate> range = bounds.getFirst();
        return now.isBefore(range.getFirst()) ? range.getFirst()
                : now.isAfter(range.getLast()) ? range.getLast() : now;
    }

    private record EnrollmentProjection(UUID studentId, UUID classId, String className,
                                        String subsystem, String level) {}

    /** Exact action + server-resolved student/class/session scope for controllers. */
    public Student requireAction(UUID id, String actionCode) {
        Student student = find(id);
        policy.require(actionCode, policyContext(student, null, null));
        return student;
    }

    private PolicyResourceContext policyContext(Student student, UUID sessionId, LocalDate effectiveDate) {
        // The legacy student.class_id projection is never an authorization
        // source; resolve the active enrollment below instead.
        return policyContext(student, sessionId, effectiveDate, null);
    }

    private PolicyResourceContext policyContext(Student student, UUID sessionId,
                                                LocalDate effectiveDate, UUID classId) {
        UUID schoolId = TenantContext.get();
        UUID resolvedSession = sessionId != null ? sessionId : currentSessionId(schoolId);
        LocalDate date = effectiveDate != null ? effectiveDate : LocalDate.now();
        UUID resolvedClass = classId != null ? classId : activeEnrollmentClass(student.getId(), resolvedSession, date);
        return new PolicyResourceContext(schoolId, resolvedSession, date, ParcoursContext.get(), resolvedClass,
                null, student.getId(), null, null, null, null, student.getLevel());
    }

    private UUID currentSessionId(UUID schoolId) {
        return jdbc.query("SELECT id FROM academic_session WHERE school_id=? AND is_current=true ORDER BY start_date DESC LIMIT 1",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, schoolId);
    }

    private UUID activeEnrollmentClass(UUID studentId, UUID sessionId, LocalDate date) {
        if (sessionId == null) return null;
        return jdbc.query("""
                SELECT school_class_id FROM student_enrollment
                 WHERE school_id=? AND student_id=? AND academic_session_id=? AND status='ACTIVE'
                   AND enrolled_on<=? AND (exited_on IS NULL OR exited_on>=?)
                 ORDER BY enrolled_on DESC, created_at DESC LIMIT 1
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), studentId, sessionId, date, date);
    }

    private LocalDate sessionStartDate(UUID sessionId) {
        if (sessionId == null) return LocalDate.now();
        return jdbc.query("SELECT start_date FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : LocalDate.now(),
                sessionId, TenantContext.get());
    }
}
