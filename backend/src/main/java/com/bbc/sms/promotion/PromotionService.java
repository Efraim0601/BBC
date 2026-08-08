package com.bbc.sms.promotion;

import com.bbc.sms.academic.BulletinService;
import com.bbc.sms.academic.Subject;
import com.bbc.sms.academic.SubjectRepository;
import com.bbc.sms.journey.JourneyEntry;
import com.bbc.sms.journey.JourneyRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.AccessScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.promotion.dto.PromotionDtos.*;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Passage de classe de fin d'année.
 *
 * <p>Le module répond en trois temps :
 * <ol>
 *   <li><b>Proposer</b> — la moyenne annuelle (mêmes coefficients que les bulletins)
 *       confrontée à la règle de la classe donne une décision proposée ;</li>
 *   <li><b>Arbitrer</b> — l'administration retient ou corrige chaque proposition, motif
 *       obligatoire dès qu'elle s'en écarte ;</li>
 *   <li><b>Appliquer</b> — la décision déplace réellement l'élève vers la classe
 *       d'accueil du mapping de progression, écrit son parcours et laisse une trace.</li>
 * </ol>
 *
 * <p>Rien n'est jamais appliqué sans passer par la simulation : le serveur recalcule
 * systématiquement moyennes, rangs et propositions au moment d'appliquer — les
 * valeurs affichées côté navigateur ne sont pas des entrées de confiance.
 */
@Service
public class PromotionService {

    /** Décisions qu'un lot peut appliquer (les états d'attente n'en font pas partie). */
    private static final Set<String> APPLICABLE = Set.of(
            "promoted", "repeated", "graduated", "transferred_out", "excluded");

    /** Décisions qui déscolarisent : l'élève sort des effectifs actifs. */
    private static final Set<String> LEAVING = Set.of("graduated", "transferred_out", "excluded");

    private final SchoolClassRepository classes;
    private final StudentRepository students;
    private final SubjectRepository subjects;
    private final BulletinService bulletins;
    private final JourneyRepository journeys;
    private final PromotionBatchRepository batches;
    private final PromotionDecisionRepository decisions;
    private final ProgressionService progression;
    private final AccessScopeService accessScope;
    private final JdbcTemplate jdbc;

    public PromotionService(SchoolClassRepository classes, StudentRepository students,
                            SubjectRepository subjects, BulletinService bulletins,
                            JourneyRepository journeys, PromotionBatchRepository batches,
                            PromotionDecisionRepository decisions, ProgressionService progression,
                            AccessScopeService accessScope, JdbcTemplate jdbc) {
        this.classes = classes;
        this.students = students;
        this.subjects = subjects;
        this.bulletins = bulletins;
        this.journeys = journeys;
        this.batches = batches;
        this.decisions = decisions;
        this.progression = progression;
        this.accessScope = accessScope;
        this.jdbc = jdbc;
    }

    // =====================================================================
    //  Simulation
    // =====================================================================

    @Transactional(readOnly = true)
    public PromotionPreview preview(UUID classId, String academicYear) {
        UUID schoolId = TenantContext.get();
        accessScope.assertClass(classId);
        SchoolClass cls = classes.findByIdAndSchoolId(classId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));

        String year = blank(academicYear) ? progression.currentAcademicYear() : academicYear.trim();
        String nextYear = ProgressionService.nextAcademicYear(year);
        SchoolClass next = nextClassOf(cls, schoolId);
        PromotionRule rule = progression.ruleFor(cls);

        List<Student> present =
                students.findBySchoolIdAndClassNameAndActiveTrueOrderByLastNameAsc(schoolId, cls.getName());
        Map<UUID, PriorDecision> prior = priorDecisions(schoolId, present, year);
        List<Student> roster = rosterOf(cls, present, prior);
        int carried = present.size() - roster.size();

        Map<UUID, Annual> annuals = annualAverages(schoolId, cls.getName(), roster);
        Map<UUID, Integer> ranks = ranks(annuals);
        Map<UUID, Integer> repeats = priorRepeats(schoolId, roster);
        int graded = ranks.size();

        List<CandidateView> candidates = new ArrayList<>();
        for (Student s : roster) {
            Annual annual = annuals.get(s.getId());
            BigDecimal average = annual == null ? null : annual.average();
            int priorRepeats = repeats.getOrDefault(s.getId(), 0);
            Proposal p = propose(rule, cls, next, average, priorRepeats);
            candidates.add(new CandidateView(
                    s.getId(), s.getMatricule(), name(s), s.getPhotoHue(),
                    average, ranks.get(s.getId()), graded == 0 ? null : graded,
                    annual == null ? 0 : annual.sequences(), priorRepeats,
                    p.result(), p.reason(),
                    "promoted".equals(p.result()) && next != null ? next.getId() : null,
                    "promoted".equals(p.result()) && next != null ? next.getName() : null,
                    prior.containsKey(s.getId()) ? prior.get(s.getId()).result() : null));
        }

        List<String> warnings = new ArrayList<>();
        if (carried > 0) {
            warnings.add(carried + " élève(s) présents en « " + cls.getName() + " » sont écartés : ils y ont été "
                       + "admis pour " + nextYear + " depuis une autre classe et ont déjà leur décision de " + year + ".");
        }
        if (roster.isEmpty()) {
            warnings.add("Aucun élève actif dans cette classe.");
        } else if (graded == 0) {
            warnings.add("Aucune note saisie : les décisions devront toutes être prises à la main.");
        } else if (graded < roster.size()) {
            warnings.add((roster.size() - graded) + " élève(s) sans aucune note — décision manuelle requise.");
        }
        if (next == null && !cls.isTerminal()) {
            warnings.add("Aucune classe d'accueil configurée pour « " + cls.getName()
                       + " » : renseignez la progression pour que l'admission soit proposée.");
        }
        long reapplied = roster.stream().filter(s -> prior.containsKey(s.getId())).count();
        if (reapplied > 0) {
            warnings.add(reapplied + " élève(s) ont déjà une décision appliquée pour " + year
                       + " — la réappliquer écrasera la précédente.");
        }

        return new PromotionPreview(cls.getId(), cls.getName(), cls.getLevel(), cls.getSubsystem(),
                year, nextYear,
                next == null ? null : next.getId(), next == null ? null : next.getName(),
                cls.isTerminal(),
                rule.getPassMark(), rule.getCouncilMargin(), rule.getMaxRepeats(),
                scopeOf(rule, cls), roster.size(), graded, candidates, warnings);
    }

    // =====================================================================
    //  Application
    // =====================================================================

    /**
     * Applique un lot de décisions. Tout se joue dans une seule transaction : soit
     * la classe entière bascule, soit rien — on ne laisse pas une promotion à moitié
     * faite entre deux années scolaires.
     */
    @Transactional
    public PromotionResult apply(PromotionApply in) {
        UUID schoolId = TenantContext.get();
        accessScope.assertClass(in.classId());
        SchoolClass cls = classes.findByIdAndSchoolId(in.classId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));

        String year = in.academicYear().trim();
        String nextYear = in.nextAcademicYear().trim();
        if (year.equals(nextYear)) {
            throw ApiException.badRequest("L'année d'accueil doit différer de l'année qui se termine");
        }
        progression.ensureAcademicYear(nextYear);

        SchoolClass defaultTarget = nextClassOf(cls, schoolId);
        PromotionRule rule = progression.ruleFor(cls);

        List<Student> present =
                students.findBySchoolIdAndClassNameAndActiveTrueOrderByLastNameAsc(schoolId, cls.getName());
        Map<UUID, PriorDecision> prior = priorDecisions(schoolId, present, year);
        List<Student> roster = rosterOf(cls, present, prior);
        Map<UUID, Student> byId = roster.stream().collect(Collectors.toMap(Student::getId, s -> s));
        Map<UUID, Annual> annuals = annualAverages(schoolId, cls.getName(), roster);
        Map<UUID, Integer> ranks = ranks(annuals);
        Map<UUID, Integer> repeats = priorRepeats(schoolId, roster);
        int graded = ranks.size();

        UUID userId = currentUserId();
        PromotionBatch batch = new PromotionBatch();
        batch.setSchoolId(schoolId);
        batch.setAcademicYear(year);
        batch.setNextAcademicYear(nextYear);
        batch.setClassId(cls.getId());
        batch.setClassName(cls.getName());
        batch.setAppliedBy(userId);
        batch.setAppliedAt(Instant.now());
        batch = batches.save(batch);

        List<String> warnings = new ArrayList<>();
        int applied = 0, promoted = 0, repeated = 0, graduated = 0, other = 0, overriddenCount = 0;

        for (PromotionLine line : in.lines()) {
            Student student = byId.get(line.studentId());
            if (student == null) {
                PriorDecision done = prior.get(line.studentId());
                warnings.add(done == null
                        ? "Élève ignoré : il n'est plus inscrit en " + cls.getName() + "."
                        : "Élève ignoré : sa décision de " + year + " a déjà été prise en « "
                          + done.fromClassName() + " ». Le promouvoir ici lui ferait sauter une classe.");
                continue;
            }
            String result = line.result().trim();
            if (!APPLICABLE.contains(result)) {
                throw ApiException.badRequest("Décision « " + result + " » invalide pour "
                        + name(student) + " — tranchez entre admis, redouble, diplômé, transféré ou exclu");
            }

            Annual annual = annuals.get(student.getId());
            BigDecimal average = annual == null ? null : annual.average();
            int priorRepeats = repeats.getOrDefault(student.getId(), 0);
            Proposal proposal = propose(rule, cls, defaultTarget, average, priorRepeats);

            boolean overridden = !result.equals(proposal.result());
            String reason = blank(line.reason()) ? null : line.reason().trim();
            if (overridden && reason == null) {
                throw ApiException.badRequest("Motif obligatoire pour " + name(student)
                        + " : la décision retenue (" + label(result) + ") s'écarte de la proposition ("
                        + label(proposal.result()) + ")");
            }

            SchoolClass target = resolveTarget(schoolId, cls, defaultTarget, result, line, student);

            // --- 1. L'élève bascule réellement -------------------------------
            if ("promoted".equals(result)) {
                student.setClassId(target.getId());
                student.setClassName(target.getName());
                student.setSubsystem(target.getSubsystem());
                student.setLevel(target.getLevel());
                student.setRepeats(false);
            } else if ("repeated".equals(result)) {
                student.setClassId(cls.getId());
                student.setRepeats(true);
            }
            if (LEAVING.contains(result)) {
                student.setActive(false);
            }
            students.save(student);

            // --- 2. Le parcours enregistre l'année qui se termine --------------
            writeJourney(schoolId, student, cls, year, result, average,
                    ranks.get(student.getId()), graded == 0 ? null : graded,
                    decisionText(year, result, average, overridden, reason), userId);

            // --- 3. …et ouvre l'année suivante quand l'élève reste scolarisé ---
            if (target != null && !LEAVING.contains(result)) {
                openNextYear(schoolId, student, target, nextYear, userId);
            }

            // --- 4. La trace : proposition, décision retenue, motif -------------
            writeDecision(schoolId, batch.getId(), student, cls, target, year, average,
                    ranks.get(student.getId()), graded == 0 ? null : graded,
                    annual == null ? 0 : annual.sequences(), priorRepeats,
                    proposal.result(), result, overridden, reason, userId);

            applied++;
            if (overridden) overriddenCount++;
            switch (result) {
                case "promoted" -> promoted++;
                case "repeated" -> repeated++;
                case "graduated" -> graduated++;
                default -> other++;
            }
        }

        batch.setStudentsTotal(applied);
        batch.setPromotedCount(promoted);
        batch.setRepeatedCount(repeated);
        batch.setGraduatedCount(graduated);
        batch.setOtherCount(other);
        batch.setOverriddenCount(overriddenCount);
        batches.save(batch);

        return new PromotionResult(batch.getId(), applied, promoted, repeated,
                graduated, other, overriddenCount, warnings);
    }

    @Transactional(readOnly = true)
    public List<BatchView> history() {
        UUID schoolId = TenantContext.get();
        Map<UUID, String> userNames = new HashMap<>();
        jdbc.query("SELECT id, display_name FROM app_user WHERE school_id = ?",
                rs -> { userNames.put(UUID.fromString(rs.getString(1)), rs.getString(2)); }, schoolId);
        return batches.findTop50BySchoolIdOrderByAppliedAtDesc(schoolId).stream()
                .map(b -> new BatchView(b.getId(), b.getAcademicYear(), b.getNextAcademicYear(),
                        b.getClassName(), b.getStudentsTotal(), b.getPromotedCount(),
                        b.getRepeatedCount(), b.getGraduatedCount(), b.getOtherCount(),
                        b.getOverriddenCount(),
                        b.getAppliedBy() == null ? null : userNames.get(b.getAppliedBy()),
                        b.getAppliedAt()))
                .toList();
    }

    // =====================================================================
    //  Moteur de décision
    // =====================================================================

    private record Proposal(String result, String reason) {}

    /**
     * La proposition automatique. Elle ne tranche que ce qui est net : au moindre
     * doute — pas de note, zone conseil, redoublements déjà nombreux, classe
     * d'accueil manquante — elle renvoie « à examiner » plutôt que de décider.
     */
    private Proposal propose(PromotionRule rule, SchoolClass cls, SchoolClass next,
                             BigDecimal average, int priorRepeats) {
        if (average == null) {
            return new Proposal("undecided", "Aucune note saisie pour cette année");
        }
        BigDecimal pass = rule.getPassMark();
        if (average.compareTo(pass) >= 0) {
            if (cls.isTerminal()) {
                return new Proposal("graduated",
                        "Moyenne " + fmt(average) + "/20 en classe de sortie");
            }
            if (next == null) {
                return new Proposal("review",
                        "Moyenne " + fmt(average) + "/20 suffisante, mais aucune classe d'accueil configurée");
            }
            return new Proposal("promoted",
                    "Moyenne " + fmt(average) + "/20 ≥ seuil " + fmt(pass));
        }
        BigDecimal floor = pass.subtract(rule.getCouncilMargin());
        if (rule.getCouncilMargin().signum() > 0 && average.compareTo(floor) >= 0) {
            return new Proposal("review",
                    "Moyenne " + fmt(average) + "/20 en zone conseil (" + fmt(floor) + " à " + fmt(pass) + ")");
        }
        Integer max = rule.getMaxRepeats();
        if (max != null && priorRepeats >= max) {
            return new Proposal("review",
                    priorRepeats + " redoublement(s) déjà au parcours — maximum autorisé : " + max);
        }
        return new Proposal("repeated", "Moyenne " + fmt(average) + "/20 < seuil " + fmt(pass));
    }

    // =====================================================================
    //  Moyennes annuelles
    // =====================================================================

    /** @param average moyenne annuelle, null si l'élève n'a aucune note */
    private record Annual(BigDecimal average, int sequences) {}

    /**
     * Moyenne annuelle de chaque élève de la classe : moyenne des séquences
     * évaluées, chaque séquence étant elle-même la moyenne pondérée Σ(note×coef)/Σ(coef).
     *
     * <p>Les coefficients sont ceux des bulletins ({@link BulletinService#coefsForClass}),
     * surcharge par classe comprise : la moyenne qui décide du passage est exactement
     * celle que l'élève lit sur son bulletin. Les séquences sans note ne comptent pas —
     * une année interrompue n'est pas pénalisée par des zéros implicites.
     */
    private Map<UUID, Annual> annualAverages(UUID schoolId, String className, List<Student> roster) {
        if (roster.isEmpty()) return Map.of();
        List<Subject> subjectList = subjects.findBySchoolIdOrderByCode(schoolId);
        Map<String, Integer> coefs = bulletins.coefsForClass(schoolId, subjectList, className);
        Set<String> known = subjectList.stream().map(Subject::getCode).collect(Collectors.toSet());

        // Σ(note×coef) et Σ(coef) par élève et par séquence, en une seule requête.
        Map<UUID, Map<Integer, BigDecimal[]>> acc = new HashMap<>();
        String placeholders = roster.stream().map(s -> "?").collect(Collectors.joining(","));
        Object[] args = new Object[roster.size() + 1];
        args[0] = schoolId;
        for (int i = 0; i < roster.size(); i++) args[i + 1] = roster.get(i).getId();

        jdbc.query("SELECT student_id, sequence, subject_code, mark FROM grade "
                 + "WHERE school_id = ? AND student_id IN (" + placeholders + ")",
                rs -> {
                    String code = rs.getString("subject_code");
                    if (!known.contains(code)) return;              // matière retirée du catalogue
                    Integer coef = coefs.get(code);
                    if (coef == null || coef <= 0) return;
                    UUID studentId = UUID.fromString(rs.getString("student_id"));
                    int sequence = rs.getInt("sequence");
                    BigDecimal mark = rs.getBigDecimal("mark");
                    BigDecimal[] cell = acc.computeIfAbsent(studentId, k -> new HashMap<>())
                            .computeIfAbsent(sequence, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    cell[0] = cell[0].add(mark.multiply(BigDecimal.valueOf(coef)));
                    cell[1] = cell[1].add(BigDecimal.valueOf(coef));
                }, args);

        Map<UUID, Annual> out = new LinkedHashMap<>();
        for (Student s : roster) {
            Map<Integer, BigDecimal[]> perSequence = acc.get(s.getId());
            if (perSequence == null || perSequence.isEmpty()) {
                out.put(s.getId(), new Annual(null, 0));
                continue;
            }
            BigDecimal sum = BigDecimal.ZERO;
            int counted = 0;
            for (BigDecimal[] cell : perSequence.values()) {
                if (cell[1].signum() <= 0) continue;
                sum = sum.add(cell[0].divide(cell[1], 2, RoundingMode.HALF_UP));
                counted++;
            }
            out.put(s.getId(), counted == 0
                    ? new Annual(null, 0)
                    : new Annual(sum.divide(BigDecimal.valueOf(counted), 2, RoundingMode.HALF_UP), counted));
        }
        return out;
    }

    /** Rang décroissant sur la moyenne annuelle, ex æquo partagés, non notés exclus. */
    private Map<UUID, Integer> ranks(Map<UUID, Annual> annuals) {
        List<Map.Entry<UUID, Annual>> scored = annuals.entrySet().stream()
                .filter(e -> e.getValue().average() != null)
                .sorted(Comparator.comparing((Map.Entry<UUID, Annual> e) -> e.getValue().average()).reversed())
                .toList();
        Map<UUID, Integer> out = new HashMap<>();
        BigDecimal previous = null;
        int rank = 0;
        for (int i = 0; i < scored.size(); i++) {
            BigDecimal average = scored.get(i).getValue().average();
            if (previous == null || average.compareTo(previous) != 0) rank = i + 1;
            out.put(scored.get(i).getKey(), rank);
            previous = average;
        }
        return out;
    }

    /**
     * Redoublements déjà au parcours. Le journal fait foi ; l'ancien drapeau
     * « redouble » de la fiche prend le relais pour les élèves saisis avant le
     * module Parcours, sans quoi leur historique paraîtrait vierge.
     */
    private Map<UUID, Integer> priorRepeats(UUID schoolId, List<Student> roster) {
        Map<UUID, Integer> out = new HashMap<>();
        if (roster.isEmpty()) return out;
        String placeholders = roster.stream().map(s -> "?").collect(Collectors.joining(","));
        Object[] args = new Object[roster.size() + 1];
        args[0] = schoolId;
        for (int i = 0; i < roster.size(); i++) args[i + 1] = roster.get(i).getId();
        jdbc.query("SELECT student_id, COUNT(*) AS n FROM journey_entry "
                 + "WHERE school_id = ? AND result = 'repeated' AND student_id IN (" + placeholders + ") "
                 + "GROUP BY student_id",
                rs -> { out.put(UUID.fromString(rs.getString("student_id")), rs.getInt("n")); }, args);
        for (Student s : roster) {
            if (out.getOrDefault(s.getId(), 0) == 0 && s.isRepeats()) out.put(s.getId(), 1);
        }
        return out;
    }

    /** @param fromClassId classe depuis laquelle la décision de l'année a été prise */
    private record PriorDecision(String result, UUID fromClassId, String fromClassName) {}

    /** Décisions déjà appliquées pour cette année, avec leur classe d'origine. */
    private Map<UUID, PriorDecision> priorDecisions(UUID schoolId, List<Student> roster, String year) {
        Map<UUID, PriorDecision> out = new HashMap<>();
        if (roster.isEmpty()) return out;
        String placeholders = roster.stream().map(s -> "?").collect(Collectors.joining(","));
        Object[] args = new Object[roster.size() + 2];
        args[0] = schoolId;
        args[1] = year;
        for (int i = 0; i < roster.size(); i++) args[i + 2] = roster.get(i).getId();
        jdbc.query("SELECT student_id, final_result, from_class_id, from_class_name FROM promotion_decision "
                 + "WHERE school_id = ? AND academic_year = ? AND student_id IN (" + placeholders + ")",
                rs -> {
                    String from = rs.getString("from_class_id");
                    out.put(UUID.fromString(rs.getString("student_id")),
                            new PriorDecision(rs.getString("final_result"),
                                    from == null ? null : UUID.fromString(from),
                                    rs.getString("from_class_name")));
                }, args);
        return out;
    }

    /**
     * Les élèves que cette classe a réellement scolarisés pendant l'année qui se termine.
     *
     * <p>Passer les classes de bas en haut (CM2 puis 6ème) amène dans la 6ème des élèves
     * qui viennent d'y être admis pour l'année suivante : les promouvoir à leur tour leur
     * ferait sauter une classe. On écarte donc ceux dont la décision de l'année a déjà été
     * prise ailleurs, et on garde ceux qui ont été traités depuis CETTE classe, pour
     * pouvoir corriger un lot.
     */
    private static List<Student> rosterOf(SchoolClass cls, List<Student> present,
                                          Map<UUID, PriorDecision> prior) {
        return present.stream().filter(s -> {
            PriorDecision d = prior.get(s.getId());
            return d == null || d.fromClassId() == null || cls.getId().equals(d.fromClassId());
        }).toList();
    }

    // =====================================================================
    //  Écritures
    // =====================================================================

    /** Classe d'accueil retenue : celle demandée, sinon celle du mapping. */
    private SchoolClass resolveTarget(UUID schoolId, SchoolClass cls, SchoolClass defaultTarget,
                                      String result, PromotionLine line, Student student) {
        if ("repeated".equals(result)) return cls;
        if (LEAVING.contains(result)) return null;

        SchoolClass target = defaultTarget;
        if (line.toClassId() != null) {
            target = classes.findByIdAndSchoolId(line.toClassId(), schoolId)
                    .orElseThrow(() -> ApiException.badRequest(
                            "Classe d'accueil inconnue pour " + name(student)));
        }
        if (target == null) {
            throw ApiException.badRequest("Classe d'accueil obligatoire pour " + name(student)
                    + " : configurez la progression de « " + cls.getName() + " » ou choisissez une classe");
        }
        if (target.getId().equals(cls.getId())) {
            throw ApiException.badRequest("La classe d'accueil de " + name(student)
                    + " est sa classe actuelle — utilisez « Redouble »");
        }
        return target;
    }

    /** Clôt l'année dans le parcours de l'élève (une entrée par élève et par année). */
    private void writeJourney(UUID schoolId, Student student, SchoolClass cls, String year,
                              String result, BigDecimal average, Integer rank, Integer classSize,
                              String decision, UUID userId) {
        JourneyEntry e = journeys.findBySchoolIdAndStudentIdAndAcademicYear(schoolId, student.getId(), year)
                .orElseGet(JourneyEntry::new);
        e.setSchoolId(schoolId);
        e.setStudentId(student.getId());
        e.setAcademicYear(year);
        e.setClassName(cls.getName());
        e.setLevel(cls.getLevel());
        e.setSubsystem(cls.getSubsystem());
        e.setResult(result);
        e.setGeneralAverage(average);
        e.setRank(rank);
        e.setClassSize(classSize);
        e.setDecision(decision);
        e.setRecordedBy(userId);
        e.setRecordedAt(Instant.now());
        journeys.save(e);
    }

    /**
     * Ouvre l'année suivante dans le parcours. Une entrée déjà close (admis,
     * redoublé…) n'est pas touchée : seule une année « en cours » se réaligne sur
     * la nouvelle classe, pour qu'un lot rejoué ne réécrive pas de l'histoire.
     */
    private void openNextYear(UUID schoolId, Student student, SchoolClass target,
                              String nextYear, UUID userId) {
        JourneyEntry e = journeys
                .findBySchoolIdAndStudentIdAndAcademicYear(schoolId, student.getId(), nextYear)
                .orElseGet(() -> {
                    JourneyEntry fresh = new JourneyEntry();
                    fresh.setSchoolId(schoolId);
                    fresh.setStudentId(student.getId());
                    fresh.setAcademicYear(nextYear);
                    fresh.setResult("in_progress");
                    return fresh;
                });
        if (!"in_progress".equals(e.getResult())) return;
        e.setClassName(target.getName());
        e.setLevel(target.getLevel());
        e.setSubsystem(target.getSubsystem());
        e.setRecordedBy(userId);
        journeys.save(e);
    }

    private void writeDecision(UUID schoolId, UUID batchId, Student student, SchoolClass from,
                               SchoolClass to, String year, BigDecimal average, Integer rank,
                               Integer classSize, int sequences, int priorRepeats,
                               String proposed, String finalResult, boolean overridden,
                               String reason, UUID userId) {
        PromotionDecision d = decisions
                .findBySchoolIdAndStudentIdAndAcademicYear(schoolId, student.getId(), year)
                .orElseGet(PromotionDecision::new);
        d.setSchoolId(schoolId);
        d.setBatchId(batchId);
        d.setStudentId(student.getId());
        d.setAcademicYear(year);
        d.setFromClassId(from.getId());
        d.setFromClassName(from.getName());
        d.setToClassId(to == null ? null : to.getId());
        d.setToClassName(to == null ? null : to.getName());
        d.setAnnualAverage(average);
        d.setRank(rank);
        d.setClassSize(classSize);
        d.setSequencesCounted(sequences);
        d.setPriorRepeats(priorRepeats);
        d.setProposedResult(proposed);
        d.setFinalResult(finalResult);
        d.setOverridden(overridden);
        d.setOverrideReason(reason);
        d.setDecidedBy(userId);
        d.setDecidedAt(Instant.now());
        decisions.save(d);
    }

    // =====================================================================
    //  Utilitaires
    // =====================================================================

    private SchoolClass nextClassOf(SchoolClass cls, UUID schoolId) {
        return cls.getNextClassId() == null
                ? null
                : classes.findByIdAndSchoolId(cls.getNextClassId(), schoolId).orElse(null);
    }

    private String scopeOf(PromotionRule rule, SchoolClass cls) {
        if (rule.getClassId() != null) return "Classe " + cls.getName();
        if (rule.getLevel() == null && rule.getSubsystem() == null) return "Toute l'école";
        String level = switch (rule.getLevel() == null ? "" : rule.getLevel()) {
            case "maternelle" -> "Maternelle";
            case "primary" -> "Primaire";
            case "secondary" -> "Secondaire";
            default -> "Tous niveaux";
        };
        return rule.getSubsystem() == null ? level : level + " " + rule.getSubsystem();
    }

    private static String decisionText(String year, String result, BigDecimal average,
                                       boolean overridden, String reason) {
        StringBuilder sb = new StringBuilder("Conseil de fin d'année ").append(year)
                .append(" — ").append(label(result));
        if (average != null) sb.append(" (moyenne ").append(fmt(average)).append("/20)");
        if (overridden) sb.append(" · Décision manuelle : ").append(reason);
        return sb.toString();
    }

    private static String label(String result) {
        return switch (result) {
            case "promoted" -> "Admis";
            case "repeated" -> "Redouble";
            case "graduated" -> "Diplômé";
            case "transferred_out" -> "Transféré";
            case "excluded" -> "Exclu";
            case "review" -> "À examiner";
            case "undecided" -> "Indécidable";
            default -> result;
        };
    }

    private static String fmt(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String name(Student s) {
        return s.getLastName().toUpperCase() + " " + s.getFirstName();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }
}
