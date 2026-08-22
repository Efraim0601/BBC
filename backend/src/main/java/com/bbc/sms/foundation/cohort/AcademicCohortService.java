package com.bbc.sms.foundation.cohort;

import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.Section;
import com.bbc.sms.setup.SectionRepository;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.bbc.sms.foundation.cohort.CohortDtos.*;

@Service
public class AcademicCohortService {
    private final JdbcTemplate jdbc;
    private final AcademicCohortResolver resolver;
    private final SchoolClassRepository classes;
    private final SectionRepository sections;
    private final StudentRepository students;
    private final StudentEnrollmentRepository enrollments;
    private final AuthorizationPolicyService policy;

    public AcademicCohortService(JdbcTemplate jdbc, AcademicCohortResolver resolver,
                                 SchoolClassRepository classes, SectionRepository sections,
                                 StudentRepository students, StudentEnrollmentRepository enrollments,
                                 AuthorizationPolicyService policy) {
        this.jdbc = jdbc;
        this.resolver = resolver;
        this.classes = classes;
        this.sections = sections;
        this.students = students;
        this.enrollments = enrollments;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public List<CohortView> list(UUID sessionId) {
        requireSchool("ACADEMIC_STRUCTURE_VIEW");
        if (sessionId == null) throw ApiException.badRequest("La session académique est obligatoire");
        return jdbc.query("""
                SELECT h.id,h.academic_session_id,s.label,h.code,h.display_name,h.level,
                       h.mode,h.attendance_mode,h.status,h.version
                  FROM academic_cohort h
                  JOIN academic_session s ON s.id=h.academic_session_id
                 WHERE h.school_id=? AND h.academic_session_id=?
                 ORDER BY h.level,h.display_name,h.code
                """, (rs, n) -> toView(
                    rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                    rs.getString(8), rs.getString(9), rs.getLong(10)),
                TenantContext.get(), sessionId);
    }

    @Transactional(readOnly = true)
    public List<ClassOption> classOptions(UUID sessionId) {
        requireSchool("ACADEMIC_STRUCTURE_VIEW");
        if (sessionId == null) throw ApiException.badRequest("La session académique est obligatoire");
        Map<String, Section> sectionById = sections.findBySchoolIdOrderByLabel(TenantContext.get()).stream()
                .collect(java.util.stream.Collectors.toMap(Section::getId, x -> x));
        return classes.findBySchoolIdOrderByName(TenantContext.get()).stream()
                .map(c -> new ClassOption(c.getId(), c.getName(), c.getLevel(), c.getSubsystem(),
                        sectionById.get(c.getSectionId()) == null ? null : sectionById.get(c.getSectionId()).getLabel()))
                .toList();
    }

    @Transactional
    public CohortView upsert(UUID id, CohortUpsert in) {
        requireSchool("CLASS_MANAGE");
        UUID schoolId = TenantContext.get();
        if (in == null || in.academicSessionId() == null) throw ApiException.badRequest("La session est obligatoire");
        String mode = normMode(in.mode());
        String level = normLevel(in.level());
        String attendance = normAttendance(in.attendanceMode());
        Map<String, Object> session = session(in.academicSessionId());
        if (session == null) throw ApiException.notFound("Session académique");
        SchoolClass fr = classFor(in.francophoneClassId());
        if (!"FR".equalsIgnoreCase(fr.getSubsystem())) throw ApiException.badRequest("La classe francophone doit appartenir au sous-système FR");
        assertCompatible(fr, level);
        SchoolClass en = in.anglophoneClassId() == null ? null : classFor(in.anglophoneClassId());
        if (en != null) {
            if (!"EN".equalsIgnoreCase(en.getSubsystem())) throw ApiException.badRequest("La classe anglophone doit appartenir au sous-système EN");
            assertCompatible(en, level);
        }
        if ("SHARED_BILINGUAL".equals(mode) && en == null) throw ApiException.badRequest("Un groupe bilingue doit avoir une classe anglophone");
        if ("SHARED_BILINGUAL".equals(mode) && !isEarlyLevel(level)) {
            throw ApiException.badRequest("Les groupes bilingues partagés sont disponibles pour la maternelle et le primaire");
        }
        if (!"SHARED_BILINGUAL".equals(mode) && en != null) throw ApiException.badRequest("Un groupe à programme unique ne peut avoir qu'une classe");

        List<SchoolClass> requested = en == null ? List.of(fr) : List.of(fr, en);
        Set<UUID> requestedIds = requested.stream().map(SchoolClass::getId).collect(java.util.stream.Collectors.toSet());
        for (SchoolClass cls : requested) {
            UUID occupied = occupiedCohort(in.academicSessionId(), cls.getId());
            if (occupied != null && (id == null || !occupied.equals(id))
                    && !isCompatibilityWrapper(occupied, in.academicSessionId(), cls.getId())) {
                throw ApiException.conflict("La classe « " + cls.getName() + " » est déjà dans un groupe actif pour cette session");
            }
        }

        String code = in.code().trim();
        String duplicate = jdbc.query("SELECT id FROM academic_cohort WHERE school_id=? AND academic_session_id=? AND code=?",
                rs -> rs.next() ? rs.getString(1) : null, schoolId, in.academicSessionId(), code);
        if (duplicate != null && (id == null || !duplicate.equals(id.toString()))) throw ApiException.conflict("Ce code de groupe existe déjà dans la session");

        UUID cohortId = id == null ? UUID.randomUUID() : id;
        if (id == null) {
            jdbc.update("""
                    INSERT INTO academic_cohort(id,school_id,academic_session_id,code,display_name,level,mode,attendance_mode,status)
                    VALUES (?,?,?,?,?,?,?,?,'ACTIVE')
                    """, cohortId, schoolId, in.academicSessionId(), code, in.displayName().trim(), level, mode, attendance);
        } else {
            Map<String, Object> current = cohortRow(id, in.academicSessionId());
            if (current == null) throw ApiException.notFound("Groupe-classe");
            Set<UUID> currentIds = jdbc.query("""
                    SELECT school_class_id FROM academic_cohort_programme
                     WHERE school_id=? AND academic_session_id=? AND cohort_id=? AND active
                    """, (rs, n) -> rs.getObject(1, UUID.class), schoolId, in.academicSessionId(), id)
                    .stream().collect(java.util.stream.Collectors.toSet());
            if (!currentIds.equals(requestedIds)) {
                throw ApiException.badRequest("Pour changer les classes d’un groupe, créez un nouveau groupe afin de conserver l’historique des bulletins et présences.");
            }
            jdbc.update("""
                    UPDATE academic_cohort
                       SET code=?,display_name=?,level=?,mode=?,attendance_mode=?,version=version+1,updated_at=now()
                     WHERE id=? AND school_id=? AND academic_session_id=?
                    """, code, in.displayName().trim(), level, mode, attendance,
                    id, schoolId, in.academicSessionId());
        }
        for (SchoolClass cls : requested) adoptCompatibilityWrapper(cohortId, in.academicSessionId(), cls);
        addProgramme(cohortId, in.academicSessionId(), fr, 1);
        if (en != null) addProgramme(cohortId, in.academicSessionId(), en, 2);
        return find(cohortId, in.academicSessionId());
    }

    @Transactional(readOnly = true)
    public CohortView find(UUID id, UUID sessionId) {
        requireSchool("ACADEMIC_STRUCTURE_VIEW");
        Map<String, Object> row = cohortRow(id, sessionId);
        if (row == null) throw ApiException.notFound("Groupe-classe");
        return toView((UUID) row.get("id"), (UUID) row.get("sessionId"), (String) row.get("sessionLabel"),
                (String) row.get("code"), (String) row.get("displayName"), (String) row.get("level"),
                (String) row.get("mode"), (String) row.get("attendanceMode"), (String) row.get("status"),
                ((Number) row.get("version")).longValue());
    }

    @Transactional(readOnly = true)
    public PathwayPreview pathwayPreview(UUID sourceSessionId, UUID targetSessionId, UUID sourceCohortId) {
        requireSchool("PROGRESSION_VIEW");
        if (sourceSessionId == null || targetSessionId == null || sourceCohortId == null) {
            throw ApiException.badRequest("Les sessions et le groupe source sont obligatoires");
        }
        assertForwardSession(sourceSessionId, targetSessionId);
        Map<String, Object> source = cohortRow(sourceCohortId, sourceSessionId);
        if (source == null) throw ApiException.notFound("Groupe source");
        Map<String, Object> sessions = jdbc.query("""
                SELECT (SELECT label FROM academic_session WHERE id=? AND school_id=?),
                       (SELECT label FROM academic_session WHERE id=? AND school_id=?)
                """, rs -> rs.next() ? Map.of("source", rs.getString(1), "target", rs.getString(2)) : null,
                sourceSessionId, TenantContext.get(), targetSessionId, TenantContext.get());
        if (sessions == null || sessions.get("source") == null || sessions.get("target") == null) throw ApiException.notFound("Session académique");
        List<PathwayTargetView> targets = jdbc.query("""
                SELECT h.id,h.display_name,h.level,h.mode,
                       coalesce(string_agg(c.name || ' (' || p.subsystem || ')', ' · ' ORDER BY p.display_order), '')
                  FROM academic_cohort h
                  LEFT JOIN academic_cohort_programme p ON p.cohort_id=h.id AND p.active
                  LEFT JOIN school_class c ON c.id=p.school_class_id
                 WHERE h.school_id=? AND h.academic_session_id=? AND h.status<>'ARCHIVED'
                 GROUP BY h.id,h.display_name,h.level,h.mode
                 ORDER BY h.level,h.display_name
                """, (rs,n) -> new PathwayTargetView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), null),
                TenantContext.get(), targetSessionId);
        List<PathwayStudentView> rows = jdbc.query("""
                SELECT st.id,st.matricule,trim(st.last_name || ' ' || st.first_name),
                       e.cohort_id,coalesce(h.display_name,e.class_name_snapshot),
                       pc.target_cohort_id,coalesce(th.display_name,''),coalesce(pc.status,'DRAFT'),coalesce(pc.version,0)
                  FROM student_enrollment e
                  JOIN student st ON st.id=e.student_id AND st.school_id=e.school_id
                  LEFT JOIN academic_cohort h ON h.id=e.cohort_id
                  LEFT JOIN student_pathway_choice pc ON pc.school_id=e.school_id AND pc.student_id=e.student_id
                    AND pc.source_session_id=? AND pc.target_session_id=?
                  LEFT JOIN academic_cohort th ON th.id=pc.target_cohort_id
                 WHERE e.school_id=? AND e.academic_session_id=?
                   AND e.status='ACTIVE' AND (e.cohort_id=? OR (e.cohort_id IS NULL AND e.school_class_id IN
                     (SELECT school_class_id FROM academic_cohort_programme WHERE cohort_id=?)))
                 ORDER BY st.last_name,st.first_name,st.matricule
                """, (rs,n) -> new PathwayStudentView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getString(5), rs.getObject(6, UUID.class), rs.getString(7), rs.getString(8), rs.getLong(9)),
                sourceSessionId, targetSessionId, TenantContext.get(), sourceSessionId, sourceCohortId, sourceCohortId);
        return new PathwayPreview(sourceSessionId, (String) sessions.get("source"), targetSessionId,
                (String) sessions.get("target"), sourceCohortId, (String) source.get("displayName"), targets, rows);
    }

    @Transactional
    public PathwayApplyResult applyPathway(PathwayApply in) {
        requireSchool("PROMOTION_COMMIT");
        if (in == null || in.choices() == null || in.choices().isEmpty()) throw ApiException.badRequest("Sélectionnez au moins un élève");
        assertForwardSession(in.sourceSessionId(), in.targetSessionId());
        Map<String, Object> source = cohortRow(in.sourceCohortId(), in.sourceSessionId());
        if (source == null) throw ApiException.notFound("Groupe source");
        int saved = 0, confirmed = 0, planned = 0;
        List<String> warnings = new ArrayList<>();
        for (PathwayChoice choice : in.choices()) {
            Map<String, Object> target = cohortRow(choice.targetCohortId(), in.targetSessionId());
            if (target == null) { warnings.add("Groupe cible introuvable pour " + choice.studentId()); continue; }
            if (!isActiveSourceStudent(choice.studentId(), in.sourceSessionId(), in.sourceCohortId())) {
                warnings.add("Élève hors du groupe source : " + choice.studentId()); continue;
            }
            UUID id = jdbc.query("SELECT id FROM student_pathway_choice WHERE school_id=? AND student_id=? AND target_session_id=?",
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                    TenantContext.get(), choice.studentId(), in.targetSessionId());
            String status = in.confirm() ? "CONFIRMED" : "DRAFT";
            if (id == null) {
                jdbc.update("""
                        INSERT INTO student_pathway_choice
                            (school_id,student_id,source_session_id,target_session_id,source_cohort_id,target_cohort_id,status,reason,chosen_by,chosen_at)
                        VALUES (?,?,?,?,?,?,?,?,?,now())
                        """, TenantContext.get(), choice.studentId(), in.sourceSessionId(), in.targetSessionId(),
                        in.sourceCohortId(), choice.targetCohortId(), status, choice.reason(), currentUserId());
            } else {
                jdbc.update("""
                        UPDATE student_pathway_choice
                           SET target_cohort_id=?,status=?,reason=?,chosen_by=?,chosen_at=now(),version=version+1,updated_at=now()
                         WHERE id=? AND school_id=?
                        """, choice.targetCohortId(), status, choice.reason(), currentUserId(), id, TenantContext.get());
            }
            saved++;
            if (!in.confirm()) continue;
            confirmed++;
            if (plannedEnrollment(choice.studentId(), in.targetSessionId(), choice.targetCohortId())) continue;
            UUID classId = resolver.preferredClassForCohort(in.targetSessionId(), choice.targetCohortId());
            if (classId == null) { warnings.add("Le groupe cible n'a pas de programme : " + choice.targetCohortId()); continue; }
            Map<String, Object> targetClass = jdbc.query("SELECT name,level,subsystem FROM school_class WHERE id=? AND school_id=?",
                    rs -> rs.next() ? Map.of("name", rs.getString(1), "level", rs.getString(2), "subsystem", rs.getString(3)) : null,
                    classId, TenantContext.get());
            LocalDate targetStart = jdbc.query("SELECT start_date FROM academic_session WHERE id=? AND school_id=?",
                    rs -> rs.next() ? rs.getObject(1, LocalDate.class) : LocalDate.now(),
                    in.targetSessionId(), TenantContext.get());
            UUID enrollmentId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO student_enrollment
                        (id,school_id,student_id,academic_session_id,school_class_id,cohort_id,class_name_snapshot,level_snapshot,subsystem_snapshot,status,enrolled_on,planned_on,source,reason)
                    VALUES (?,?,?,?,?,?,?,?,?,'PLANNED',?,?, 'PATHWAY',?)
                    """, enrollmentId, TenantContext.get(), choice.studentId(), in.targetSessionId(), classId,
                    choice.targetCohortId(), targetClass.get("name"), targetClass.get("level"), targetClass.get("subsystem"),
                    targetStart, targetStart, choice.reason() == null ? "Choix de parcours manuel" : choice.reason());
            planned++;
        }
        return new PathwayApplyResult(saved, confirmed, planned, warnings, Instant.now());
    }

    private CohortView toView(UUID id, UUID sessionId, String sessionLabel, String code, String name,
                              String level, String mode, String attendanceMode, String status, long version) {
        List<ProgrammeView> programmes = jdbc.query("""
                SELECT p.id,p.school_class_id,c.name,c.subsystem,c.level,p.report_card_enabled,p.active
                  FROM academic_cohort_programme p JOIN school_class c ON c.id=p.school_class_id
                 WHERE p.school_id=? AND p.academic_session_id=? AND p.cohort_id=?
                 ORDER BY p.display_order,p.subsystem
                """, (rs,n) -> new ProgrammeView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6), rs.getBoolean(7)),
                TenantContext.get(), sessionId, id);
        int count = jdbc.queryForObject("SELECT count(*) FROM student_enrollment WHERE school_id=? AND academic_session_id=? AND cohort_id=? AND status='ACTIVE'",
                Integer.class, TenantContext.get(), sessionId, id);
        return new CohortView(id, sessionId, sessionLabel, code, name, level, mode, attendanceMode, status,
                count, programmes, version);
    }

    private void addProgramme(UUID cohortId, UUID sessionId, SchoolClass schoolClass, int order) {
        UUID schoolId = TenantContext.get();
        UUID existingCohort = jdbc.query("""
                SELECT cohort_id FROM academic_cohort_programme
                 WHERE school_id=? AND academic_session_id=? AND school_class_id=?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, schoolId, sessionId, schoolClass.getId());
        if (existingCohort != null && !existingCohort.equals(cohortId)) {
            throw ApiException.conflict("La classe « " + schoolClass.getName() + " » est déjà dans un autre groupe pour cette session");
        }
        jdbc.update("""
                INSERT INTO academic_cohort_programme
                    (school_id,academic_session_id,cohort_id,school_class_id,subsystem,display_order)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT (school_id,academic_session_id,cohort_id,school_class_id)
                DO UPDATE SET display_order=excluded.display_order,active=true,version=academic_cohort_programme.version+1,updated_at=now()
                """, schoolId, sessionId, cohortId, schoolClass.getId(), schoolClass.getSubsystem(), order);
        jdbc.update("""
                UPDATE student_enrollment e SET cohort_id=?
                 WHERE e.school_id=? AND e.academic_session_id=? AND e.school_class_id=? AND e.status IN ('ACTIVE','PLANNED')
                """, cohortId, schoolId, sessionId, schoolClass.getId());
    }

    private UUID occupiedCohort(UUID sessionId, UUID classId) {
        return jdbc.query("""
                SELECT cohort_id FROM academic_cohort_programme
                 WHERE school_id=? AND academic_session_id=? AND school_class_id=? AND active
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), sessionId, classId);
    }

    /**
     * V165 creates one compatibility wrapper per legacy class.  Pairing is a
     * controlled merge of those wrappers: memberships move to the new group,
     * while the old wrapper is archived and its programme link removed.
     */
    private void adoptCompatibilityWrapper(UUID targetCohortId, UUID sessionId, SchoolClass cls) {
        UUID old = occupiedCohort(sessionId, cls.getId());
        if (old == null || old.equals(targetCohortId)) return;
        if (!isCompatibilityWrapper(old, sessionId, cls.getId())) {
            throw ApiException.conflict("La classe « " + cls.getName() + " » est déjà dans un groupe actif pour cette session");
        }
        jdbc.update("""
                UPDATE student_enrollment SET cohort_id=?
                 WHERE school_id=? AND academic_session_id=? AND cohort_id=? AND status IN ('ACTIVE','PLANNED')
                """, targetCohortId, TenantContext.get(), sessionId, old);
        jdbc.update("DELETE FROM academic_cohort_programme WHERE school_id=? AND academic_session_id=? AND cohort_id=? AND school_class_id=?",
                TenantContext.get(), sessionId, old, cls.getId());
        jdbc.update("UPDATE academic_cohort SET status='ARCHIVED',version=version+1,updated_at=now() WHERE school_id=? AND id=?",
                TenantContext.get(), old);
    }

    private boolean isCompatibilityWrapper(UUID cohortId, UUID sessionId, UUID classId) {
        return jdbc.query("""
                SELECT h.mode='SINGLE_PROGRAMME' AND h.code=('CLASS-' || replace(c.id::text, '-', ''))
                  FROM academic_cohort h JOIN school_class c ON c.id=?
                 WHERE h.school_id=? AND h.academic_session_id=? AND h.id=?
                """, rs -> rs.next() && rs.getBoolean(1), classId, TenantContext.get(), sessionId, cohortId);
    }

    private Map<String, Object> cohortRow(UUID id, UUID sessionId) {
        return jdbc.query("""
                SELECT h.id,h.academic_session_id,s.label,h.code,h.display_name,h.level,h.mode,h.attendance_mode,h.status,h.version
                  FROM academic_cohort h JOIN academic_session s ON s.id=h.academic_session_id
                 WHERE h.school_id=? AND h.id=? AND h.academic_session_id=?
                """, rs -> rs.next() ? Map.of("id", rs.getObject(1, UUID.class), "sessionId", rs.getObject(2, UUID.class),
                        "sessionLabel", rs.getString(3), "code", rs.getString(4), "displayName", rs.getString(5),
                        "level", rs.getString(6), "mode", rs.getString(7), "attendanceMode", rs.getString(8),
                        "status", rs.getString(9), "version", rs.getLong(10)) : null,
                TenantContext.get(), id, sessionId);
    }

    private Map<String, Object> session(UUID id) {
        return jdbc.query("SELECT id,label,start_date,end_date FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? Map.of("id", rs.getObject(1, UUID.class), "label", rs.getString(2),
                        "start", rs.getObject(3, LocalDate.class), "end", rs.getObject(4, LocalDate.class)) : null,
                id, TenantContext.get());
    }

    private void assertForwardSession(UUID sourceSessionId, UUID targetSessionId) {
        Map<String, Object> source = session(sourceSessionId);
        Map<String, Object> target = session(targetSessionId);
        if (source == null || target == null) throw ApiException.notFound("Session académique");
        LocalDate sourceStart = (LocalDate) source.get("start");
        LocalDate targetStart = (LocalDate) target.get("start");
        if (sourceStart == null || targetStart == null || !targetStart.isAfter(sourceStart)) {
            throw ApiException.badRequest("La session cible doit suivre la session source");
        }
    }

    private SchoolClass classFor(UUID id) {
        if (id == null) throw ApiException.badRequest("La classe francophone est obligatoire");
        return classes.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Classe"));
    }

    private void assertCompatible(SchoolClass cls, String level) {
        if (!level.equalsIgnoreCase(cls.getLevel())) throw ApiException.badRequest("Les classes du groupe doivent avoir le même niveau");
    }

    private boolean isActiveSourceStudent(UUID studentId, UUID sessionId, UUID cohortId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM student_enrollment WHERE school_id=? AND student_id=? AND academic_session_id=? AND status='ACTIVE' AND cohort_id=?",
                Integer.class, TenantContext.get(), studentId, sessionId, cohortId);
        return count != null && count > 0;
    }

    private boolean plannedEnrollment(UUID studentId, UUID sessionId, UUID cohortId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM student_enrollment WHERE school_id=? AND student_id=? AND academic_session_id=? AND status IN ('ACTIVE','PLANNED') AND cohort_id=?",
                Integer.class, TenantContext.get(), studentId, sessionId, cohortId);
        return count != null && count > 0;
    }

    private UUID currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof com.bbc.sms.platform.security.AppUserPrincipal p ? p.userId() : null;
    }

    private void requireSchool(String action) {
        policy.require(action, PolicyResourceContext.empty().forSchool(TenantContext.get()));
    }

    private static String normMode(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("SINGLE_PROGRAMME", "SHARED_BILINGUAL").contains(v)) throw ApiException.badRequest("Mode de groupe invalide");
        return v;
    }

    private static String normAttendance(String value) {
        String v = value == null || value.isBlank() ? "DAILY_SHARED" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DAILY_SHARED", "PROGRAMME").contains(v)) throw ApiException.badRequest("Mode de présence invalide");
        return v;
    }

    private static String normLevel(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!List.of("maternelle", "primary", "secondary").contains(v)) throw ApiException.badRequest("Niveau invalide");
        return v;
    }

    private static boolean isEarlyLevel(String value) {
        return "maternelle".equalsIgnoreCase(value) || "primary".equalsIgnoreCase(value);
    }
}
