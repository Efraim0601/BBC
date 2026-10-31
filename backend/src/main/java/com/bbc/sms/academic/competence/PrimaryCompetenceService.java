package com.bbc.sms.academic.competence;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Primary competence-based grading: per-level grids of competences, sub-competences (taught as
 * subjects) and evaluation types, and their application to a class. Applying a grid builds the
 * class curriculum (one subject per sub-competence, grouped by competence, coefficient = points)
 * and one sequence evaluation per evaluation type, weighted by its points. The existing sequence
 * engine then yields points obtained / points × 20 for each subject, and the coefficient makes
 * the overall average the grand total over the maximum.
 */
@Service
public class PrimaryCompetenceService {
    /** Subject and subject-group codes generated from a grid start with this prefix. */
    public static final String CODE_PREFIX = "APC_";
    /** Group of subjects shown on the card but not counted in the total (e.g. religion). */
    public static final String NOT_COUNTED_GROUP = CODE_PREFIX + "CX";
    /** Primary pupils sit five sequences; the second sequence of the third term is not used. */
    public static final String UNUSED_SEQUENCE = "S6";

    /** Evaluation types in display order, with the assessment-code suffix and labels. */
    public enum EvaluationType {
        ORAL("O", "Oral", "Oral"), WRITTEN("E", "Écrit", "Written"),
        PRACTICAL("P", "Pratique", "Practical"), ATTITUDE("S", "Savoir-être", "Attitude");
        public final String suffix, fr, en;
        EvaluationType(String suffix, String fr, String en) { this.suffix = suffix; this.fr = fr; this.en = en; }
        public static EvaluationType fromAssessmentCode(String code) {
            if (code == null) return null;
            for (EvaluationType type : values()) if (code.toUpperCase(Locale.ROOT).endsWith("_" + type.suffix)) return type;
            return null;
        }
    }

    public record GridItem(String competenceCode, Map<String, String> competenceLabel, String code,
                           Map<String, String> label, BigDecimal points, BigDecimal oral, BigDecimal written,
                           BigDecimal practical, BigDecimal attitude, String alternativeGroup, boolean countsInTotal) {}
    public record GridView(UUID id, String subsystem, int level, int version, BigDecimal totalPoints, List<GridItem> items) {}
    public record GridSaveRequest(List<GridItem> items) {}
    public record ApplyResult(UUID classId, String className, String subsystem, int level, int gridVersion,
                              int subjects, int assessmentsCreated, int assessmentsUpdated, int assessmentsKept,
                              List<String> retiredSubjects) {}
    public record ClassStatus(UUID classId, String className, Integer suggestedLevel, Integer appliedLevel,
                              Integer appliedVersion, Integer latestVersion) {}

    private final JdbcTemplate jdbc;
    private final SchoolClassRepository classes;
    private final AuthorizationPolicyService policy;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public PrimaryCompetenceService(JdbcTemplate jdbc, SchoolClassRepository classes,
                                    AuthorizationPolicyService policy, AuditService audit, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.classes = classes;
        this.policy = policy;
        this.audit = audit;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- grids

    @Transactional
    public List<GridView> grids() {
        ensureDefaults();
        List<GridView> grids = new ArrayList<>();
        for (String subsystem : List.of("FR", "EN")) for (int level = 1; level <= 3; level++) grids.add(latest(subsystem, level));
        return grids;
    }

    @Transactional
    public GridView grid(String subsystem, int level) {
        ensureDefaults();
        return latest(normalizeSubsystem(subsystem), level(level));
    }

    /** Publish a new version of one grid after checking that every total adds up. */
    @Transactional
    public GridView save(String subsystem, int level, GridSaveRequest request) {
        require("CURRICULUM_CATALOG_MANAGE");
        ensureDefaults();
        String normalized = normalizeSubsystem(subsystem);
        int band = level(level);
        List<GridItem> items = request == null || request.items() == null ? List.of() : request.items();
        validate(items);
        int version = Optional.ofNullable(jdbc.queryForObject(
                "SELECT max(version) FROM primary_competence_grid WHERE school_id=? AND subsystem=? AND level_band=?",
                Integer.class, TenantContext.get(), normalized, band)).orElse(0) + 1;
        UUID id = insertGrid(normalized, band, version, items);
        audit.record("PRIMARY_COMPETENCE_GRID_PUBLISHED", "PrimaryCompetenceGrid", id.toString(), null,
                Map.of("subsystem", normalized, "level", band, "version", version), null);
        return latest(normalized, band);
    }

    static void validate(List<GridItem> items) {
        if (items.isEmpty()) throw ApiException.badRequest("La grille doit contenir au moins une sous-compétence.");
        Set<String> codes = new HashSet<>();
        for (GridItem item : items) {
            String code = item.code() == null ? "" : item.code().trim().toUpperCase(Locale.ROOT);
            if (!code.matches("[A-Z0-9]{1,6}")) throw ApiException.badRequest("Code de sous-compétence invalide : " + item.code());
            if (!codes.add(code)) throw ApiException.badRequest("Code de sous-compétence en double : " + code);
            if (item.competenceCode() == null || !item.competenceCode().trim().toUpperCase(Locale.ROOT).matches("C[0-9X]{1,2}"))
                throw ApiException.badRequest(code + " : code de compétence invalide (C1 à C6, ou CX).");
            if (item.points() == null || item.points().signum() <= 0) throw ApiException.badRequest(code + " : les points doivent être positifs.");
            if (item.points().stripTrailingZeros().scale() > 0) throw ApiException.badRequest(code + " : les points doivent être entiers.");
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal part : parts(item)) {
                if (part == null) continue;
                if (part.signum() <= 0) throw ApiException.badRequest(code + " : un type d'évaluation non utilisé doit rester vide, pas 0.");
                sum = sum.add(part);
            }
            if (sum.compareTo(item.points()) != 0)
                throw ApiException.badRequest(code + " : oral + écrit + pratique + savoir-être = " + plain(sum)
                        + ", mais la sous-compétence vaut " + plain(item.points()) + " points.");
        }
        // Alternatives (6A1 / 6A2) replace each other, so they must be worth the same.
        Map<String, BigDecimal> alternatives = new HashMap<>();
        for (GridItem item : items) {
            if (item.alternativeGroup() == null || item.alternativeGroup().isBlank()) continue;
            BigDecimal previous = alternatives.putIfAbsent(item.alternativeGroup().trim().toUpperCase(Locale.ROOT), item.points());
            if (previous != null && previous.compareTo(item.points()) != 0)
                throw ApiException.badRequest("Les sous-compétences du groupe " + item.alternativeGroup() + " doivent valoir le même nombre de points.");
        }
    }

    // ------------------------------------------------------- class application

    /** Primary level of a class from its name: SIL/CP and Class 1-2 → 1, CE1/CE2 and Class 3-4 → 2, CM1/CM2 and Class 5-6 → 3. */
    public static Integer levelFromClassName(String name) {
        String n = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
        if (n.startsWith("SIL") || n.matches("CP\\b.*|CP")) return 1;
        if (n.startsWith("CE")) return 2;
        if (n.startsWith("CM")) return 3;
        Matcher m = Pattern.compile("^CLASS\\s*([1-6])\\b").matcher(n);
        if (m.find()) return (Integer.parseInt(m.group(1)) + 1) / 2;
        return null;
    }

    @Transactional(readOnly = true)
    public List<ClassStatus> classes(UUID sessionId) {
        UUID school = TenantContext.get();
        return jdbc.query("""
                SELECT c.id,c.name,c.subsystem,pc.grid_id,g.level_band,g.version
                  FROM school_class c
                  LEFT JOIN primary_competence_class pc ON pc.class_id=c.id AND pc.school_id=c.school_id AND pc.academic_session_id=?
                  LEFT JOIN primary_competence_grid g ON g.id=pc.grid_id
                 WHERE c.school_id=? AND c.level='primary'
                 ORDER BY c.subsystem,c.grade_order,c.name
                """, (rs, n) -> {
                    Integer suggested = levelFromClassName(rs.getString(2));
                    String subsystem = rs.getString(3);
                    Integer latest = suggested == null || subsystem == null ? null : jdbc.queryForObject(
                            "SELECT max(version) FROM primary_competence_grid WHERE school_id=? AND subsystem=? AND level_band=?",
                            Integer.class, school, subsystem.toUpperCase(Locale.ROOT), suggested);
                    return new ClassStatus(rs.getObject(1, UUID.class), rs.getString(2), suggested,
                            (Integer) rs.getObject(5), (Integer) rs.getObject(6), latest);
                }, sessionId, school);
    }

    /** Whether this class is graded by competence in this session. */
    public boolean usesCompetences(UUID sessionId, UUID classId) {
        if (sessionId == null || classId == null) return false;
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM primary_competence_class WHERE school_id=? AND academic_session_id=? AND class_id=?",
                Integer.class, TenantContext.get(), sessionId, classId);
        return count != null && count > 0;
    }

    /**
     * Set a primary class up from the latest grid of its level: competence groups, one subject per
     * sub-competence, and one evaluation per evaluation type in every sequence used by primary.
     * Evaluations that already hold marks are never resized; subjects outside the grid are retired.
     */
    @Transactional
    public ApplyResult apply(UUID sessionId, UUID classId, Integer requestedLevel) {
        require("CURRICULUM_CATALOG_MANAGE");
        ensureDefaults();
        UUID school = TenantContext.get();
        SchoolClass cls = classes.findByIdAndSchoolId(classId, school).orElseThrow(() -> ApiException.notFound("Classe"));
        if (!"primary".equalsIgnoreCase(cls.getLevel()))
            throw ApiException.badRequest("Seules les classes du primaire utilisent la grille des compétences.");
        String subsystem = normalizeSubsystem(cls.getSubsystem());
        Integer band = requestedLevel != null ? level(requestedLevel) : levelFromClassName(cls.getName());
        if (band == null) throw ApiException.field(HttpStatus.BAD_REQUEST, "PRIMARY_LEVEL_REQUIRED",
                "Indiquez le niveau (I, II ou III) de la classe " + cls.getName() + ".", "level", "Niveau requis");
        LocalDate sessionStart = jdbc.query("SELECT start_date FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null, sessionId, school);
        if (sessionStart == null) throw ApiException.notFound("Session académique");
        GridView grid = latest(subsystem, band);
        boolean french = "FR".equals(subsystem);

        Map<String, UUID> groups = new HashMap<>();
        for (GridItem item : grid.items()) {
            String competence = item.competenceCode().toUpperCase(Locale.ROOT);
            groups.computeIfAbsent(competence, ignored -> upsertGroup(sessionId, competence, item.competenceLabel()));
        }

        Set<String> gridSubjects = new LinkedHashSet<>();
        Map<String, UUID> curriculumIds = new HashMap<>();
        int order = 0;
        for (GridItem item : grid.items()) {
            order++;
            String subjectCode = CODE_PREFIX + item.code().toUpperCase(Locale.ROOT);
            gridSubjects.add(subjectCode);
            UUID subjectId = upsertSubject(subsystem, subjectCode, item.label());
            UUID groupId = groups.get(item.competenceCode().toUpperCase(Locale.ROOT));
            curriculumIds.put(subjectCode, upsertCurriculum(sessionId, classId, subjectId, groupId, order, item));
        }
        List<String> retired = retireOtherSubjects(sessionId, classId, gridSubjects, sessionStart);

        int created = 0, updated = 0, kept = 0;
        List<Map<String, Object>> sequences = jdbc.queryForList("""
                SELECT id,code FROM academic_reporting_period
                 WHERE school_id=? AND academic_session_id=? AND period_type='SEQUENCE' AND upper(code)<>?
                 ORDER BY display_order
                """, school, sessionId, UNUSED_SEQUENCE);
        for (Map<String, Object> sequence : sequences) {
            UUID periodId = (UUID) sequence.get("id");
            String periodCode = ((String) sequence.get("code")).toUpperCase(Locale.ROOT);
            for (GridItem item : grid.items()) {
                String subjectCode = CODE_PREFIX + item.code().toUpperCase(Locale.ROOT);
                Set<String> wanted = new HashSet<>();
                int display = 0;
                for (EvaluationType type : EvaluationType.values()) {
                    display++;
                    BigDecimal points = points(item, type);
                    if (points == null) continue;
                    String code = periodCode + "_" + subjectCode + "_" + type.suffix;
                    wanted.add(code);
                    switch (upsertAssessment(sessionId, periodId, classId, subjectCode, curriculumIds.get(subjectCode),
                            code, french ? type.fr : type.en, type.name(), points, display,
                            item.alternativeGroup() == null || item.alternativeGroup().isBlank())) {
                        case CREATED -> created++;
                        case UPDATED -> updated++;
                        case KEPT -> kept++;
                    }
                }
                removeUnusedAssessments(periodId, classId, subjectCode, wanted);
            }
        }
        jdbc.update("""
                INSERT INTO primary_competence_class(school_id,academic_session_id,class_id,grid_id,applied_by)
                VALUES (?,?,?,?,?)
                ON CONFLICT (school_id,academic_session_id,class_id)
                DO UPDATE SET grid_id=excluded.grid_id, applied_at=now(), applied_by=excluded.applied_by
                """, school, sessionId, classId, grid.id(), currentUserId());
        audit.record("PRIMARY_COMPETENCE_GRID_APPLIED", "SchoolClass", classId.toString(), null,
                Map.of("sessionId", sessionId, "subsystem", subsystem, "level", band, "gridVersion", grid.version(),
                        "created", created, "updated", updated, "kept", kept, "retired", retired), null);
        return new ApplyResult(classId, cls.getName(), subsystem, band, grid.version(), gridSubjects.size(),
                created, updated, kept, retired);
    }

    // ---------------------------------------------------------------- grades

    /** National grade scale for primary: CNA (C) below 11, ECA (B) below 15, A below 18, A+ from 18 (mark out of 20). */
    public static String cote(BigDecimal markOutOfTwenty) {
        if (markOutOfTwenty == null) return null;
        if (markOutOfTwenty.compareTo(BigDecimal.valueOf(11)) < 0) return "CNA";
        if (markOutOfTwenty.compareTo(BigDecimal.valueOf(15)) < 0) return "ECA";
        if (markOutOfTwenty.compareTo(BigDecimal.valueOf(18)) < 0) return "A";
        return "A+";
    }

    // ---------------------------------------------------------------- helpers

    private enum Outcome { CREATED, UPDATED, KEPT }

    private Outcome upsertAssessment(UUID sessionId, UUID periodId, UUID classId, String subjectCode, UUID curriculumId,
                                     String code, String label, String type, BigDecimal points, int display, boolean mandatory) {
        UUID school = TenantContext.get();
        Map<String, Object> existing = jdbc.query("""
                SELECT a.id,a.max_score,a.weight,a.label,a.mandatory,a.display_order,
                       EXISTS (SELECT 1 FROM academic_grade g WHERE g.assessment_id=a.id) AS graded
                  FROM academic_assessment a
                 WHERE a.school_id=? AND a.reporting_period_id=? AND a.class_id=? AND a.subject_code=? AND a.code=?
                """, rs -> rs.next() ? Map.<String, Object>of("id", rs.getObject(1, UUID.class), "max", rs.getBigDecimal(2),
                        "graded", rs.getBoolean(7), "same", rs.getBigDecimal(2).compareTo(points) == 0
                                && rs.getBigDecimal(3).compareTo(points) == 0 && label.equals(rs.getString(4))
                                && rs.getBoolean(5) == mandatory && rs.getInt(6) == display) : null,
                school, periodId, classId, subjectCode, code);
        if (existing == null) {
            freeDisplayOrder(periodId, classId, subjectCode, display);
            jdbc.update("""
                    INSERT INTO academic_assessment(school_id,academic_session_id,reporting_period_id,class_id,subject_code,
                        code,label,assessment_type,component_type,max_score,weight,mandatory,display_order,source,curriculum_subject_id)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'COMPETENCE_GRID',?)
                    """, school, sessionId, periodId, classId, subjectCode, code, label, type, type, points, points,
                    mandatory, display, curriculumId);
            return Outcome.CREATED;
        }
        if (Boolean.TRUE.equals(existing.get("same"))) return Outcome.KEPT;
        // Marks already entered are never invalidated: a graded evaluation keeps its maximum.
        if (Boolean.TRUE.equals(existing.get("graded")) && ((BigDecimal) existing.get("max")).compareTo(points) != 0) return Outcome.KEPT;
        freeDisplayOrder(periodId, classId, subjectCode, display, (UUID) existing.get("id"));
        jdbc.update("""
                UPDATE academic_assessment SET label=?,assessment_type=?,component_type=?,max_score=?,weight=?,mandatory=?,
                       display_order=?,curriculum_subject_id=?,updated_at=now(),version=version+1
                 WHERE id=? AND school_id=?
                """, label, type, type, points, points, mandatory, display, curriculumId, existing.get("id"), school);
        return Outcome.UPDATED;
    }

    /** Move another evaluation of the same subject out of the way of the display slot we need. */
    private void freeDisplayOrder(UUID periodId, UUID classId, String subjectCode, int display, UUID... keep) {
        UUID keepId = keep.length == 0 ? null : keep[0];
        jdbc.update("""
                UPDATE academic_assessment SET display_order=display_order+100
                 WHERE school_id=? AND reporting_period_id=? AND class_id=? AND subject_code=? AND display_order=?
                   AND (?::uuid IS NULL OR id<>?::uuid)
                """, TenantContext.get(), periodId, classId, subjectCode, display, keepId, keepId);
    }

    private void removeUnusedAssessments(UUID periodId, UUID classId, String subjectCode, Set<String> wanted) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id,a.code FROM academic_assessment a
                 WHERE a.school_id=? AND a.reporting_period_id=? AND a.class_id=? AND a.subject_code=?
                   AND NOT EXISTS (SELECT 1 FROM academic_grade g WHERE g.assessment_id=a.id)
                """, TenantContext.get(), periodId, classId, subjectCode);
        for (Map<String, Object> row : rows) {
            if (!wanted.contains(row.get("code"))) jdbc.update("DELETE FROM academic_assessment WHERE id=?", row.get("id"));
        }
    }

    private UUID upsertGroup(UUID sessionId, String competence, Map<String, String> label) {
        UUID school = TenantContext.get();
        String code = CODE_PREFIX + competence;
        Map<String, String> effective = label == null || label.isEmpty()
                ? PrimaryCompetenceDefaults.COMPETENCES.getOrDefault(competence, Map.of("fr", competence, "en", competence)) : label;
        String policy = NOT_COUNTED_GROUP.equals(code) ? "NOT_COUNTED" : "POINTS";
        UUID id = jdbc.query("SELECT id FROM academic_subject_group WHERE school_id=? AND academic_session_id=? AND code=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, school, sessionId, code);
        if (id != null) {
            jdbc.update("UPDATE academic_subject_group SET label=?::jsonb,show_subtotal=true,average_policy=?,version=version+1 WHERE id=?",
                    json(effective), policy, id);
            return id;
        }
        id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO academic_subject_group(id,school_id,academic_session_id,code,label,display_order,show_subtotal,show_rank,average_policy)
                VALUES (?,?,?,?,?::jsonb,(SELECT coalesce(max(display_order),0)+1 FROM academic_subject_group WHERE school_id=? AND academic_session_id=?),true,false,?)
                """, id, school, sessionId, code, json(effective), school, sessionId, policy);
        return id;
    }

    private UUID upsertSubject(String subsystem, String code, Map<String, String> label) {
        UUID school = TenantContext.get();
        UUID id = jdbc.query("SELECT id FROM subject WHERE school_id=? AND subsystem=? AND code=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, school, subsystem, code);
        if (id != null) {
            jdbc.update("UPDATE subject SET label=?::jsonb WHERE id=?", json(label), id);
            return id;
        }
        id = UUID.randomUUID();
        jdbc.update("INSERT INTO subject(id,school_id,code,label,coef,subsystem) VALUES (?,?,?,?::jsonb,1,?)",
                id, school, code, json(label), subsystem);
        return id;
    }

    private UUID upsertCurriculum(UUID sessionId, UUID classId, UUID subjectId, UUID groupId, int order, GridItem item) {
        UUID school = TenantContext.get();
        int coefficient = item.points().intValueExact();
        boolean mandatory = item.alternativeGroup() == null || item.alternativeGroup().isBlank();
        UUID id = jdbc.query("SELECT id FROM academic_curriculum_subject WHERE school_id=? AND academic_session_id=? AND class_id=? AND subject_id=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, school, sessionId, classId, subjectId);
        // Display orders are unique per class only by convention; park the row far away, then settle it.
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO academic_curriculum_subject(id,school_id,academic_session_id,class_id,subject_id,group_id,display_order,
                        coefficient,max_score,mandatory,pass_threshold,show_subject_rank,remark_required)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,false,false)
                    """, id, school, sessionId, classId, subjectId, groupId, 1000 + order, coefficient, item.points(), mandatory,
                    item.points().divide(BigDecimal.valueOf(2)));
        } else {
            jdbc.update("""
                    UPDATE academic_curriculum_subject SET group_id=?,display_order=?,coefficient=?,max_score=?,mandatory=?,pass_threshold=?,
                           active_to=NULL,updated_at=now(),version=version+1
                     WHERE id=?
                    """, groupId, 1000 + order, coefficient, item.points(), mandatory, item.points().divide(BigDecimal.valueOf(2)), id);
        }
        return id;
    }

    /**
     * Take subjects that are not part of the grid off the class. A subject that already has
     * evaluations is ended the day before the session starts, keeping its history readable;
     * one without evaluations is removed.
     */
    private List<String> retireOtherSubjects(UUID sessionId, UUID classId, Set<String> gridSubjects, LocalDate sessionStart) {
        UUID school = TenantContext.get();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id,s.code,c.active_from,
                       EXISTS (SELECT 1 FROM academic_assessment a WHERE a.school_id=c.school_id AND a.academic_session_id=c.academic_session_id
                                  AND a.class_id=c.class_id AND upper(a.subject_code)=upper(s.code)) AS assessed
                  FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id
                 WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=?
                """, school, sessionId, classId);
        List<String> retired = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String code = ((String) row.get("code")).toUpperCase(Locale.ROOT);
            if (gridSubjects.contains(code)) continue;
            retired.add(code);
            if (Boolean.TRUE.equals(row.get("assessed"))) {
                jdbc.update("UPDATE academic_curriculum_subject SET active_from=NULL,active_to=?,updated_at=now(),version=version+1 WHERE id=?",
                        sessionStart.minusDays(1), row.get("id"));
            } else {
                jdbc.update("DELETE FROM academic_curriculum_subject WHERE id=?", row.get("id"));
            }
        }
        // Settle the grid subjects into display slots 1..n now that nothing else competes for them.
        jdbc.update("""
                UPDATE academic_curriculum_subject SET display_order=display_order-1000
                 WHERE school_id=? AND academic_session_id=? AND class_id=? AND display_order>1000
                """, school, sessionId, classId);
        return retired;
    }

    private void ensureDefaults() {
        UUID school = TenantContext.get();
        for (String subsystem : List.of("FR", "EN")) for (int level = 1; level <= 3; level++) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM primary_competence_grid WHERE school_id=? AND subsystem=? AND level_band=?",
                    Integer.class, school, subsystem, level);
            if (count != null && count > 0) continue;
            List<GridItem> items = PrimaryCompetenceDefaults.grid(subsystem, level).stream()
                    .map(d -> new GridItem(d.competenceCode(), PrimaryCompetenceDefaults.COMPETENCES.get(d.competenceCode()),
                            d.code(), d.label(), d.points(), d.oral(), d.written(), d.practical(), d.attitude(),
                            d.alternativeGroup(), d.countsInTotal()))
                    .toList();
            validate(items);
            insertGrid(subsystem, level, 1, items);
        }
    }

    private UUID insertGrid(String subsystem, int level, int version, List<GridItem> items) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO primary_competence_grid(id,school_id,subsystem,level_band,version,created_by) VALUES (?,?,?,?,?,?)",
                id, TenantContext.get(), subsystem, level, version, currentUserId());
        int order = 0;
        for (GridItem item : items) {
            String competence = item.competenceCode().trim().toUpperCase(Locale.ROOT);
            Map<String, String> competenceLabel = item.competenceLabel() == null || item.competenceLabel().isEmpty()
                    ? PrimaryCompetenceDefaults.COMPETENCES.getOrDefault(competence, Map.of("fr", competence, "en", competence))
                    : item.competenceLabel();
            jdbc.update("""
                    INSERT INTO primary_competence_grid_item(grid_id,competence_code,competence_label,code,label,points,
                        oral_points,written_points,practical_points,attitude_points,alternative_group,counts_in_total,display_order)
                    VALUES (?,?,?::jsonb,?,?::jsonb,?,?,?,?,?,?,?,?)
                    """, id, competence, json(competenceLabel), item.code().trim().toUpperCase(Locale.ROOT), json(item.label()),
                    item.points(), item.oral(), item.written(), item.practical(), item.attitude(),
                    item.alternativeGroup() == null || item.alternativeGroup().isBlank() ? null : item.alternativeGroup().trim().toUpperCase(Locale.ROOT),
                    item.countsInTotal() && !"CX".equals(competence), ++order);
        }
        return id;
    }

    private GridView latest(String subsystem, int level) {
        Map<String, Object> grid = jdbc.query("""
                SELECT id,version FROM primary_competence_grid WHERE school_id=? AND subsystem=? AND level_band=?
                 ORDER BY version DESC LIMIT 1
                """, rs -> rs.next() ? Map.<String, Object>of("id", rs.getObject(1, UUID.class), "version", rs.getInt(2)) : null,
                TenantContext.get(), subsystem, level);
        if (grid == null) throw ApiException.notFound("Grille des compétences");
        List<GridItem> items = jdbc.query("""
                SELECT competence_code,competence_label::text,code,label::text,points,oral_points,written_points,
                       practical_points,attitude_points,alternative_group,counts_in_total
                  FROM primary_competence_grid_item WHERE grid_id=? ORDER BY display_order
                """, (rs, n) -> new GridItem(rs.getString(1), map(rs.getString(2)), rs.getString(3), map(rs.getString(4)),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8),
                        rs.getBigDecimal(9), rs.getString(10), rs.getBoolean(11)), grid.get("id"));
        return new GridView((UUID) grid.get("id"), subsystem, level, (Integer) grid.get("version"), total(items), items);
    }

    /** Grand total: counted items, with one item per alternative group. */
    static BigDecimal total(List<GridItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        Set<String> seen = new HashSet<>();
        for (GridItem item : items) {
            if (!item.countsInTotal()) continue;
            if (item.alternativeGroup() != null && !item.alternativeGroup().isBlank() && !seen.add(item.alternativeGroup())) continue;
            total = total.add(item.points());
        }
        return total;
    }

    private static List<BigDecimal> parts(GridItem item) {
        return Arrays.asList(item.oral(), item.written(), item.practical(), item.attitude());
    }

    private static BigDecimal points(GridItem item, EvaluationType type) {
        return switch (type) {
            case ORAL -> item.oral();
            case WRITTEN -> item.written();
            case PRACTICAL -> item.practical();
            case ATTITUDE -> item.attitude();
        };
    }

    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private static int level(int level) {
        if (level < 1 || level > 3) throw ApiException.badRequest("Le niveau doit être I, II ou III (1, 2 ou 3).");
        return level;
    }

    private static String normalizeSubsystem(String subsystem) {
        String value = subsystem == null ? "" : subsystem.trim().toUpperCase(Locale.ROOT);
        if (!value.equals("FR") && !value.equals("EN")) throw ApiException.badRequest("Le sous-système doit être FR ou EN.");
        return value;
    }

    private String json(Map<String, String> value) {
        try { return mapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private Map<String, String> map(String json) {
        try { return json == null ? Map.of() : mapper.readValue(json, new TypeReference<Map<String, String>>() {}); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private void require(String action) {
        policy.require(action, PolicyResourceContext.empty().forSchool(TenantContext.get()));
    }

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }
}
