package com.bbc.sms.student;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.foundation.enrollment.EnrollmentService;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.ParcoursContext.Scope;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.SetupService;
import com.bbc.sms.setup.dto.SetupDtos.ClassView;
import com.bbc.sms.student.dto.StudentDtos.*;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

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
        // A class filter is a roster request, not a legacy Student.className
        // lookup.  That distinction matters for a paired bilingual cohort:
        // either programme class must resolve to the same active students.
        if (className != null && !className.isBlank()) {
            UUID sessionId = currentSessionId(schoolId);
            SchoolClass requested = classes.findBySchoolIdAndName(schoolId, className.trim()).orElse(null);
            if (sessionId != null && requested != null) return roster(sessionId, requested.getId());
        }
        List<Student> rows = (className == null || className.isBlank())
                ? repo.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId)
                : repo.findBySchoolIdAndClassNameAndActiveTrueOrderByLastNameAsc(schoolId, className);
        Scope scope = ParcoursContext.get();
        // Un professeur principal ne voit que les élèves de ses classes ; un
        // admin de section, ceux de son cycle.
        Set<UUID> allowed = teacherScope.allowedClassIds();
        return rows.stream()
                .filter(s -> visible(s, allowed))
                .filter(s -> inScope(scope, s.getLevel(), s.getSubsystem()))
                .filter(s -> policy.decide("STUDENT_DIRECTORY_VIEW", policyContext(s, null, null)).allowed())
                .map(this::toView).toList();
    }

    /** Class options for the student-directory filter. */
    @Transactional(readOnly = true)
    public List<ClassView> classOptions() {
        return setup.listClassesForStudentDirectory();
    }

    /**
     * Academic screens use the active enrollment in the requested session and
     * class as their roster. Student.className/classId is only a current legacy
     * projection and must not decide who appears in a historical or future
     * session bulletin/PV.
     */
    @Transactional(readOnly = true)
    public List<? extends DirectoryView> roster(UUID sessionId, UUID classId) {
        // Enrollment history/roster is protected by ENROLLMENT_VIEW.  This
        // endpoint is the academic student-directory projection instead: it
        // resolves active enrollment rows, then applies STUDENT_DIRECTORY_VIEW
        // with the server-resolved class/session/student context below.
        SchoolClass requestedClass = classes.findByIdAndSchoolId(classId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Classe"));
        List<StudentEnrollment> active = enrollmentService.activeRosterRecordsForDirectory(sessionId, classId);
        if (active.isEmpty()) return List.of();

        Collection<UUID> studentIds = active.stream().map(StudentEnrollment::getStudentId).toList();
        Map<UUID, Student> students = new HashMap<>();
        for (Student student : repo.findBySchoolIdAndIdInAndActiveTrue(TenantContext.get(), studentIds)) {
            students.put(student.getId(), student);
        }
        Scope scope = ParcoursContext.get();
        boolean teacher = teacherScope.restricted();
        LocalDate rosterDate = sessionStartDate(sessionId);
        return active.stream()
                // A bilingual cohort has one enrollment row but two programme
                // classes. Scope the request with the selected programme class,
                // never with the enrollment row's original FR/EN snapshot.
                .filter(e -> inScope(scope, requestedClass.getLevel(), requestedClass.getSubsystem()))
                .map(e -> {
                    Student student = students.get(e.getStudentId());
                    if (student == null || !policy.decide("STUDENT_DIRECTORY_VIEW",
                            policyContext(student, sessionId, rosterDate, classId)).allowed()) return null;
                    return teacher
                            ? toTeacherView(student, requestedClass.getId(), requestedClass.getName(),
                            requestedClass.getSubsystem(), requestedClass.getLevel())
                            : toView(student, requestedClass.getId(), requestedClass.getName(),
                            requestedClass.getSubsystem(), requestedClass.getLevel());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * L'élève est-il dans le périmètre du compte ? Les élèves déjà affectés se
     * jugent sur leur classe ; ceux qui ne le sont pas encore — les inscriptions
     * du jour — restent visibles de l'admin de leur section, qui a précisément
     * pour tâche de les placer.
     */
    private boolean visible(Student s, Set<UUID> allowed) {
        if (allowed == null) return true;                        // compte non cloisonné
        if (s.getClassId() != null) return allowed.contains(s.getClassId());
        return teacherScope.unassignedVisible(s.getLevel());
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
        if (teacherScope.adminSection() != null) {
            // Son cycle borne l'acces, y compris pour un eleve sans classe : ce
            // sont ses inscriptions du jour, qu'il doit pouvoir ouvrir pour les
            // affecter. La projection « enseignant » ci-dessous les refuserait.
            teacherScope.assertStudent(id);
            return toView(student);
        }
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
        assertNoDuplicate(in, null);
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
        // Une fiche dont on ne touche ni le nom, ni le NIU, ni la classe ne crée
        // aucun doublon nouveau : la contrôler redemanderait une confirmation à
        // chaque correction d'un numéro de téléphone, pour un homonyme déjà assumé.
        if (identityChanged(s, in)) assertNoDuplicate(in, id);
        apply(s, in);
        s = repo.saveAndFlush(s);
        enrollmentService.syncCurrent(s);
        return toView(s);
    }

    /** Le nom, le NIU ou la classe de la fiche diffèrent-ils de ce qui est enregistré ? */
    private static boolean identityChanged(Student s, StudentUpsert in) {
        if (!nameKey(s.getLastName(), s.getFirstName()).equals(nameKey(in.lastName(), in.firstName()))) return true;
        if (!Objects.equals(blankToNull(s.getNiu()), blankToNull(in.niu()))) return true;
        return !Objects.equals(s.getClassId(), in.classId());
    }

    // ---- Détection des doublons ---------------------------------------------

    /**
     * Les fiches déjà au registre qui ressemblent à celle qu'on saisit. L'écran
     * l'interroge au fil de la frappe pour prévenir AVANT l'enregistrement ; la
     * même recherche garde l'écriture juste en dessous, de sorte qu'un doublon
     * reste impossible même sans passer par l'écran.
     */
    @Transactional(readOnly = true)
    public DuplicateCheckResult checkDuplicates(String lastName, String firstName, LocalDate dob,
                                                String niu, UUID classId, UUID excludeId) {
        List<DuplicateMatch> matches = findDuplicates(lastName, firstName, dob, niu, classId, excludeId);
        if (matches.isEmpty()) return new DuplicateCheckResult(false, false, false, null, List.of());
        DuplicateMatch first = matches.get(0);
        return new DuplicateCheckResult(true, first.sameClass(), first.sameNiu(),
                duplicateMessage(first, blankToNull(niu)), matches);
    }

    /**
     * Refuse l'écriture d'une fiche qui en redouble une autre.
     *
     * <p>Deux enfants peuvent réellement porter le même nom : l'homonyme n'est donc
     * qu'un doute, levé par une confirmation explicite ({@code allowDuplicate}). Le
     * NIU, lui, est l'identifiant officiel de l'élève — deux fiches ne peuvent pas
     * le partager, et aucune confirmation ne lève ce refus.
     */
    private void assertNoDuplicate(StudentUpsert in, UUID excludeId) {
        String niu = blankToNull(in.niu());
        List<DuplicateMatch> matches =
                findDuplicates(in.lastName(), in.firstName(), in.dob(), niu, in.classId(), excludeId);
        if (matches.isEmpty()) return;
        DuplicateMatch niuHit = matches.stream().filter(DuplicateMatch::sameNiu).findFirst().orElse(null);
        if (niuHit != null) throw ApiException.conflict(duplicateMessage(niuHit, niu));
        if (in.allowDuplicate()) return;
        throw ApiException.conflict(duplicateMessage(matches.get(0), niu));
    }

    /**
     * Balaye les élèves actifs de l'établissement — pas seulement ceux de la classe
     * visée : le même enfant inscrit deux fois dans deux classes est précisément le
     * doublon que l'école ne voit jamais. Une fiche retirée (suppression douce) ne
     * compte pas, sinon une réinscription serait bloquée par son propre passé.
     *
     * <p>La comparaison des noms se fait sur la clé normalisée de l'import : accents,
     * casse, tirets et espaces doubles ne doivent pas faire passer un doublon pour un
     * nouvel élève.
     */
    private List<DuplicateMatch> findDuplicates(String lastName, String firstName, LocalDate dob,
                                                String niu, UUID classId, UUID excludeId) {
        String key = nameKey(lastName, firstName);
        boolean hasName = !normalise(lastName).isEmpty() && !normalise(firstName).isEmpty();
        String niuKey = blankToNull(niu);
        if (!hasName && niuKey == null) return List.of();

        List<DuplicateMatch> out = new ArrayList<>();
        for (StudentRepository.DuplicateRow r : repo.findProjectedBySchoolIdAndActiveTrue(TenantContext.get())) {
            if (excludeId != null && excludeId.equals(r.getId())) continue;   // la fiche qu'on modifie
            String rowNiu = blankToNull(r.getNiu());
            boolean sameNiu = niuKey != null && rowNiu != null && niuKey.equalsIgnoreCase(rowNiu);
            boolean sameName = hasName && key.equals(nameKey(r.getLastName(), r.getFirstName()));
            if (!sameNiu && !sameName) continue;
            out.add(new DuplicateMatch(r.getId(), r.getMatricule(),
                    displayName(r.getLastName(), r.getFirstName()),
                    r.getClassId(), r.getClassName(), r.getLevel(), r.getSubsystem(), r.getDob(), rowNiu,
                    classId != null && classId.equals(r.getClassId()),
                    sameNiu, sameName, dob != null && dob.equals(r.getDob())));
        }
        // Le premier de la liste porte le message : d'abord le NIU (refus ferme),
        // puis la même classe, puis la même date de naissance — du plus certain au
        // plus douteux.
        out.sort(Comparator.comparing(DuplicateMatch::sameNiu).reversed()
                .thenComparing(Comparator.comparing(DuplicateMatch::sameClass).reversed())
                .thenComparing(Comparator.comparing(DuplicateMatch::sameDob).reversed())
                .thenComparing(DuplicateMatch::name));
        return out;
    }

    /** Le message montré à l'utilisateur, adapté à ce qui a réellement concordé. */
    private static String duplicateMessage(DuplicateMatch m, String niu) {
        String who = m.name() + " (" + m.matricule()
                + (m.className() == null || m.className().isBlank() ? "" : ", " + m.className()) + ")";
        if (m.sameNiu()) return "Le NIU " + niu + " est déjà attribué à " + who + ".";
        if (m.sameClass()) return "Cet élève existe déjà dans cette classe : " + who + ".";
        return "Cet élève existe déjà dans la base de données : " + who + ".";
    }

    @Transactional
    public void delete(UUID id) {
        Student s = requireAction(id, "STUDENT_PROFILE_DEACTIVATE");
        s.setActive(false);   // soft delete — keeps financial/academic history intact
        repo.save(s);
    }

    /**
     * Retire d'un seul geste les élèves cochés dans la liste. Chaque identifiant
     * est traité à part : une fiche hors périmètre ou déjà retirée est rapportée
     * sans faire échouer les autres. Les contrôles précèdent toute écriture, la
     * transaction reste donc saine malgré les erreurs collectées.
     */
    @Transactional
    public BulkDeleteResult deleteAll(List<UUID> ids) {
        int deleted = 0;
        List<BulkDeleteError> errors = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            try {
                teacherScope.assertStudent(id);
                Student s = find(id);
                s.setActive(false);
                repo.save(s);
                deleted++;
            } catch (ApiException e) {
                errors.add(new BulkDeleteError(id, e.getMessage()));
            }
        }
        return new BulkDeleteResult(deleted, errors.size(), errors);
    }

    /**
     * Import a register into one class, in a way that survives being run twice.
     *
     * Each row is matched against the pupils already in the target class — by NIU
     * when the row carries one, otherwise by normalised last + first name. A match
     * is ENRICHED rather than duplicated: only the fields still empty on file are
     * filled in, so a partial or out-of-date register can never erase what has
     * already been captured. A row matching nobody creates the pupil, as before.
     * Pupils created earlier in the same batch join the index immediately, so a
     * register listing the same child twice still yields a single record. Une ligne
     * dont l'homonyme est inscrit dans une AUTRE classe crée bien la fiche — le
     * transfert et l'homonyme ne se distinguent pas d'eux-mêmes — mais ressort en
     * avertissement dans le bilan.
     *
     * That idempotence is also what makes the endpoint safe to retry: when a slow
     * link drops the connection mid-import, re-sending the same batch converges on
     * the same result instead of doubling the class.
     *
     * Each row is validated on its own — a bad row is reported and skipped, the rest
     * still import. Matricules come from a running counter checked against the
     * school's existing ones, held in memory so the batch stays unique within itself
     * (a COUNT-based query can't see rows not yet flushed).
     */
    @Transactional
    public StudentImportResult importForClass(StudentImportRequest in) {
        policy.require("STUDENT_IMPORT", PolicyResourceContext.empty().forSchool(TenantContext.get()));
        UUID schoolId = TenantContext.get();
        SchoolClass cls = resolveImportClass(in, schoolId);
        teacherScope.assertClass(cls.getId());

        // Everything the matching needs, read once up front (see the repository note).
        Map<String, Student> byNiu = new HashMap<>();
        Map<String, Student> byName = new HashMap<>();
        for (Student s : repo.findBySchoolIdAndClassIdAndActiveTrue(schoolId, cls.getId())) {
            String niu = blankToNull(s.getNiu());
            if (niu != null) byNiu.merge(niu, s, StudentService::mostComplete);
            byName.merge(nameKey(s.getLastName(), s.getFirstName()), s, StudentService::mostComplete);
        }
        Set<String> schoolNiu = new HashSet<>(repo.findActiveNiusBySchoolId(schoolId));
        Set<String> usedMatricules = new HashSet<>(repo.findMatriculesBySchoolId(schoolId));

        // Nom → classe des élèves du RESTE de l'établissement. Un homonyme qui y dort
        // n'arrête pas l'import — un changement de classe et deux enfants de même nom
        // se ressemblent trop pour trancher tout seul — mais il est signalé, à charge
        // de l'école de fusionner les deux fiches si c'est bien le même enfant.
        Map<String, String> elsewhere = new HashMap<>();
        for (StudentRepository.DuplicateRow r : repo.findProjectedBySchoolIdAndActiveTrue(schoolId)) {
            if (cls.getId().equals(r.getClassId())) continue;
            String where = blankToNull(r.getClassName());
            elsewhere.putIfAbsent(nameKey(r.getLastName(), r.getFirstName()),
                    where == null ? "sans classe" : where);
        }

        long seq = repo.countBySchoolIdAndActiveTrue(schoolId) + 1001;
        List<StudentImportWarning> warnings = new ArrayList<>();
        List<StudentImportError> errors = new ArrayList<>();
        int created = 0, updated = 0, unchanged = 0, fieldsFilled = 0;
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
                String niu = blankToNull(row.niu());
                String key = nameKey(lastName, firstName);

                // ---- Already in this class? Complete the record instead of adding one.
                Student match = niu != null ? byNiu.get(niu) : null;
                if (match == null) match = byName.get(key);
                if (match != null) {
                    int filled = fillBlanks(match, row, sex, niu);
                    if (filled > 0) {
                        syncPrimaryContact(match);
                        repo.save(match);
                        updated++;
                        fieldsFilled += filled;
                    } else {
                        unchanged++;
                    }
                    // A NIU learned from this row makes the pupil findable by it next time.
                    if (niu != null) {
                        byNiu.putIfAbsent(niu, match);
                        schoolNiu.add(niu);
                    }
                    continue;
                }

                // ---- New to this class. The NIU must still be free school-wide:
                // otherwise the pupil is on file elsewhere and importing here would
                // create a second record under the same official identifier.
                if (niu != null && schoolNiu.contains(niu)) {
                    throw new IllegalArgumentException(
                            "NIU " + niu + " déjà attribué à un élève d'une autre classe");
                }

                String matricule;
                do { matricule = "BBC-" + seq++; }
                while (!usedMatricules.add(matricule));

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

                String other = elsewhere.get(key);
                if (other != null) {
                    warnings.add(new StudentImportWarning(lineNo, label,
                            "Un élève de ce nom est déjà inscrit en « " + other
                                    + " » — la fiche a été créée, à vérifier."));
                }

                // Index the newcomer so a second mention of the same child later in
                // this very batch enriches it rather than adding another record.
                byName.putIfAbsent(key, s);
                if (niu != null) {
                    byNiu.putIfAbsent(niu, s);
                    schoolNiu.add(niu);
                }
            } catch (RuntimeException ex) {
                errors.add(new StudentImportError(lineNo, label.isBlank() ? "?" : label, ex.getMessage()));
            }
        }
        return new StudentImportResult(created, updated, unchanged, fieldsFilled, errors.size(), errors, warnings);
    }

    /**
     * Matching key for a pupil's name. Registers are retyped by hand from one year
     * to the next, so accents, capitalisation, hyphens and double spaces all drift:
     * "MBALLA  Jean-Pierre", "Mballa Jean pierre" and "MBALLA JEAN-PIERRE" must all
     * resolve to the same child. The NUL separator keeps last and first name apart,
     * so "AB C" and "A BC" cannot collide.
     */
    private static String nameKey(String lastName, String firstName) {
        return normalise(lastName) + '\0' + normalise(firstName);
    }

    private static String normalise(String s) {
        if (s == null) return "";
        String unaccented = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return unaccented.replaceAll("[^\\p{Alnum}]+", " ").trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Which of two records to enrich when a class already holds duplicates (earlier
     * imports created some): pick the fullest, so the enrichment lands on the record
     * the school is most likely to keep.
     */
    private static Student mostComplete(Student a, Student b) {
        return completeness(b) > completeness(a) ? b : a;
    }

    private static int completeness(Student s) {
        int n = s.getDob() != null ? 1 : 0;
        for (String v : new String[]{
                s.getNiu(), s.getSex(), s.getBirthplace(), s.getParentName(), s.getParentPhone(),
                s.getFatherName(), s.getFatherPhone(), s.getFatherEmail(),
                s.getMotherName(), s.getMotherPhone(), s.getMotherEmail(),
                s.getGuardianName(), s.getGuardianPhone(), s.getGuardianEmail(),
                s.getGuardianRelation()}) {
            if (blankToNull(v) != null) n++;
        }
        return n;
    }

    /**
     * Copy the row onto a pupil already on file, but only where that pupil has
     * nothing yet — an incomplete or stale register must never overwrite data the
     * school has already captured. Returns how many fields were actually filled, so
     * a run that changes nothing can be reported as such rather than as an update.
     */
    private static int fillBlanks(Student s, StudentImportRow row, String sex, String niu) {
        int n = 0;
        n += fill(s.getNiu(), niu, s::setNiu);
        n += fill(s.getSex(), sex, s::setSex);
        n += fill(s.getBirthplace(), row.birthplace(), s::setBirthplace);
        n += fill(s.getParentName(), row.parentName(), s::setParentName);
        n += fill(s.getParentPhone(), row.parentPhone(), s::setParentPhone);
        n += fill(s.getFatherName(), row.fatherName(), s::setFatherName);
        n += fill(s.getFatherPhone(), row.fatherPhone(), s::setFatherPhone);
        n += fill(s.getFatherEmail(), row.fatherEmail(), s::setFatherEmail);
        n += fill(s.getMotherName(), row.motherName(), s::setMotherName);
        n += fill(s.getMotherPhone(), row.motherPhone(), s::setMotherPhone);
        n += fill(s.getMotherEmail(), row.motherEmail(), s::setMotherEmail);
        n += fill(s.getGuardianName(), row.guardianName(), s::setGuardianName);
        n += fill(s.getGuardianPhone(), row.guardianPhone(), s::setGuardianPhone);
        n += fill(s.getGuardianEmail(), row.guardianEmail(), s::setGuardianEmail);
        n += fill(s.getGuardianRelation(), row.guardianRelation(), s::setGuardianRelation);
        if (s.getDob() == null && row.dob() != null) { s.setDob(row.dob()); n++; }
        // `repeats` is a boolean with no empty state: the register can raise it,
        // never clear it — clearing would be an overwrite, not a fill.
        if (row.repeats() && !s.isRepeats()) { s.setRepeats(true); n++; }
        return n;
    }

    private static int fill(String current, String incoming, Consumer<String> setter) {
        if (blankToNull(current) != null) return 0;      // already known — leave it alone
        String value = blankToNull(incoming);
        if (value == null) return 0;
        setter.accept(value);
        return 1;
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
            // On n'affecte que dans une classe de son périmètre : sans ce contrôle,
            // un compte cloisonné sortirait un élève de sa portée d'un simple
            // changement de classe — et le perdrait de vue au passage.
            teacherScope.assertClass(cls.getId());
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
            // Le cycle vient du parcours choisi ou, pour un admin de section, de
            // son verrou : son inscription porte sa section même sans en-tête.
            if (lvl == null) lvl = ParcoursContext.effectiveLevel();
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

    /** L'élève tel qu'on le nomme partout : NOM de famille en capitales, puis prénom. */
    private static String displayName(String lastName, String firstName) {
        return (lastName == null ? "" : lastName.toUpperCase()) + " " + (firstName == null ? "" : firstName);
    }

    private StudentView toView(Student s) {
        return toView(s, s.getClassId(), s.getClassName(), s.getSubsystem(), s.getLevel());
    }

    private StudentView toView(Student s, UUID classId, String className, String subsystem, String level) {
        String name = displayName(s.getLastName(), s.getFirstName());
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
                SELECT q.student_id,q.school_class_id,q.name,q.subsystem,q.level
                  FROM (
                    SELECT e.student_id,pc.id school_class_id,pc.name,pc.subsystem,pc.level,
                           st.last_name,st.first_name,
                           row_number() OVER (
                               PARTITION BY e.student_id
                               ORDER BY CASE WHEN pc.id=e.school_class_id THEN 0 ELSE 1 END,lower(pc.name)
                           ) programme_rank
                      FROM student_enrollment e
                      JOIN student st ON st.id=e.student_id AND st.school_id=e.school_id AND st.active=true
                      JOIN school_class pc ON pc.school_id=e.school_id
                       AND (pc.id=e.school_class_id OR (
                            e.cohort_id IS NOT NULL AND EXISTS (
                                SELECT 1 FROM academic_cohort_programme p
                                 WHERE p.school_id=e.school_id
                                   AND p.academic_session_id=e.academic_session_id
                                   AND p.cohort_id=e.cohort_id
                                   AND p.school_class_id=pc.id AND p.active)))
                     WHERE e.school_id=? AND e.academic_session_id=? AND e.status='ACTIVE'
                       AND e.enrolled_on<=? AND (e.exited_on IS NULL OR e.exited_on>=?)
                """);
        List<Object> args = new ArrayList<>(List.of(schoolId, sessionId, date, date));
        String allowedPlaceholders = String.join(",", java.util.Collections.nCopies(allowedClasses.size(), "?"));
        sql.append(" AND pc.id IN (").append(allowedPlaceholders).append(")");
        args.addAll(allowedClasses);
        if (className != null && !className.isBlank()) {
            sql.append(" AND pc.name=?");
            args.add(className.trim());
        }
        if (scope != null) {
            sql.append(" AND lower(pc.level)=lower(?) AND lower(pc.subsystem)=lower(?)");
            args.add(scope.level()); args.add(scope.subsystem());
        }
        sql.append(" ) q WHERE q.programme_rank=1 ORDER BY lower(q.name), lower(q.last_name), lower(q.first_name)");
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

    private EnrollmentProjection activeEnrollment(UUID studentId, UUID sessionId, LocalDate date) {
        if (sessionId == null) return null;
        Set<UUID> allowedClasses = teacherScope.allowedClassIds(sessionId, date);
        if (allowedClasses != null && allowedClasses.isEmpty()) return null;
        StringBuilder sql = new StringBuilder("""
                SELECT e.student_id,c.id school_class_id,c.name,c.subsystem,c.level
                  FROM student_enrollment e
                  JOIN school_class c ON c.school_id=e.school_id
                   AND (c.id=e.school_class_id OR (
                        e.cohort_id IS NOT NULL AND EXISTS (
                            SELECT 1 FROM academic_cohort_programme p
                             WHERE p.school_id=e.school_id
                               AND p.academic_session_id=e.academic_session_id
                               AND p.cohort_id=e.cohort_id
                               AND p.school_class_id=c.id AND p.active)))
                 WHERE e.school_id=? AND e.student_id=? AND e.academic_session_id=? AND e.status='ACTIVE'
                   AND e.enrolled_on<=? AND (e.exited_on IS NULL OR e.exited_on>=?)
                """);
        List<Object> args = new ArrayList<>(List.of(TenantContext.get(), studentId, sessionId, date, date));
        if (allowedClasses != null) {
            sql.append(" AND c.id IN (")
                    .append(String.join(",", java.util.Collections.nCopies(allowedClasses.size(), "?")))
                    .append(")");
            args.addAll(allowedClasses);
        }
        Scope scope = ParcoursContext.get();
        if (scope != null) {
            sql.append(" AND lower(c.level)=lower(?) AND lower(c.subsystem)=lower(?)");
            args.add(scope.level());
            args.add(scope.subsystem());
        }
        sql.append(" ORDER BY CASE WHEN c.id=e.school_class_id THEN 0 ELSE 1 END,e.enrolled_on DESC,e.created_at DESC,c.name LIMIT 1");
        List<EnrollmentProjection> rows = jdbc.query(sql.toString(), (rs, n) -> new EnrollmentProjection(rs.getObject("student_id", UUID.class),
                rs.getObject("school_class_id", UUID.class), rs.getString("name"),
                rs.getString("subsystem"), rs.getString("level")), args.toArray());
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
        // A current session can begin after the wall-clock date during
        // pre-session setup.  Use the same bounded session-start fallback as
        // roster authorization instead of handing V2 an out-of-session date.
        LocalDate date = effectiveDate != null ? effectiveDate : effectiveDate(resolvedSession);
        UUID resolvedClass = classId != null ? classId : activeEnrollmentClass(student.getId(), resolvedSession, date);
        return new PolicyResourceContext(schoolId, resolvedSession, date, ParcoursContext.get(), resolvedClass,
                null, student.getId(), null, null, null, null, student.getLevel());
    }

    private UUID currentSessionId(UUID schoolId) {
        return jdbc.query("SELECT id FROM academic_session WHERE school_id=? AND is_current=true ORDER BY start_date DESC LIMIT 1",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, schoolId);
    }

    private UUID activeEnrollmentClass(UUID studentId, UUID sessionId, LocalDate date) {
        EnrollmentProjection enrollment = activeEnrollment(studentId, sessionId, date);
        return enrollment == null ? null : enrollment.classId();
    }

    private LocalDate sessionStartDate(UUID sessionId) {
        if (sessionId == null) return LocalDate.now();
        return jdbc.query("SELECT start_date FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : LocalDate.now(),
                sessionId, TenantContext.get());
    }
}
