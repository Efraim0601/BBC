package com.bbc.sms.promotion;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.AccessScopeService;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.ParcoursContext.Scope;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.promotion.dto.PromotionDtos.*;
import com.bbc.sms.setup.Section;
import com.bbc.sms.setup.SectionRepository;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.student.StudentService;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Configuration du passage de classe : le mapping de progression (quelle classe
 * vient après) et les règles de décision (le seuil de moyenne).
 *
 * <p>Séparé de {@link PromotionService} pour que les droits le soient aussi :
 * configurer relève des Paramètres, exécuter relève de la direction pédagogique.
 */
@Service
public class ProgressionService {

    private static final String CLASS_PLACEHOLDER = "?";

    private final SchoolClassRepository classes;
    private final SectionRepository sections;
    private final StudentRepository students;
    private final PromotionRuleRepository rules;
    private final AccessScopeService accessScope;
    private final JdbcTemplate jdbc;

    public ProgressionService(SchoolClassRepository classes, SectionRepository sections,
                              StudentRepository students, PromotionRuleRepository rules,
                              AccessScopeService accessScope, JdbcTemplate jdbc) {
        this.classes = classes;
        this.sections = sections;
        this.students = students;
        this.rules = rules;
        this.accessScope = accessScope;
        this.jdbc = jdbc;
    }

    // ---- Lecture d'ensemble --------------------------------------------------

    @Transactional(readOnly = true)
    public PromotionConfig config() {
        String current = currentAcademicYear();
        return new PromotionConfig(current, nextAcademicYear(current), progression(), listRules());
    }

    /** Les classes du parcours actif, ordonnées par section puis par rang pédagogique. */
    @Transactional(readOnly = true)
    public List<ProgressionView> progression() {
        UUID schoolId = TenantContext.get();
        Map<String, Section> sectionById = new HashMap<>();
        for (Section s : sections.findBySchoolIdOrderByLabel(schoolId)) sectionById.put(s.getId(), s);

        Map<UUID, SchoolClass> all = new LinkedHashMap<>();
        for (SchoolClass c : classes.findBySchoolIdOrderByName(schoolId)) all.put(c.getId(), c);

        Scope scope = ParcoursContext.get();
        Set<UUID> allowed = accessScope.allowedClassIds();

        return all.values().stream()
                .filter(c -> allowed == null || allowed.contains(c.getId()))
                .filter(c -> StudentService.inScope(scope, c.getLevel(), c.getSubsystem()))
                .sorted(Comparator.comparing(SchoolClass::getSectionId)
                        .thenComparingInt(SchoolClass::getGradeOrder)
                        .thenComparing(SchoolClass::getName))
                .map(c -> {
                    SchoolClass next = c.getNextClassId() == null ? null : all.get(c.getNextClassId());
                    Section section = sectionById.get(c.getSectionId());
                    return new ProgressionView(
                            c.getId(), c.getName(), c.getSectionId(),
                            section == null ? c.getSectionId() : section.getLabel(),
                            c.getSubsystem(), c.getLevel(), c.getGradeOrder(),
                            next == null ? null : next.getId(),
                            next == null ? null : next.getName(),
                            c.isTerminal(),
                            // Par NOM, comme le fait la simulation : un élève importé avant les
                            // sélecteurs de classe porte le libellé sans l'identifiant, et un
                            // décompte par identifiant le rendrait invisible.
                            students.countBySchoolIdAndClassNameAndActiveTrue(schoolId, c.getName()));
                })
                .toList();
    }

    // ---- Mapping de progression ----------------------------------------------

    /**
     * Enregistre le mapping ligne par ligne. Une classe ne peut pas se succéder à
     * elle-même — rester dans sa classe, c'est un redoublement, pas une progression —
     * ni pointer vers la classe d'une autre école.
     */
    @Transactional
    public List<ProgressionView> saveProgression(ProgressionUpdate in) {
        UUID schoolId = TenantContext.get();
        for (ProgressionLine line : in.lines()) {
            SchoolClass c = classes.findByIdAndSchoolId(line.classId(), schoolId)
                    .orElseThrow(() -> ApiException.notFound("Classe"));
            SchoolClass next = null;
            if (line.nextClassId() != null) {
                next = classes.findByIdAndSchoolId(line.nextClassId(), schoolId)
                        .orElseThrow(() -> ApiException.badRequest(
                                "Classe d'accueil inconnue pour « " + c.getName() + " »"));
                if (next.getId().equals(c.getId())) {
                    throw ApiException.badRequest(
                            "« " + c.getName() + " » ne peut pas être sa propre classe suivante — "
                          + "c'est un redoublement, pas un passage");
                }
            }
            if (line.terminal() && next != null) {
                throw ApiException.badRequest(
                        "« " + c.getName() + " » est marquée classe de sortie : elle ne peut pas "
                      + "avoir de classe suivante");
            }
            c.setGradeOrder(Math.max(0, line.gradeOrder()));
            c.setNextClassId(next == null ? null : next.getId());
            c.setTerminal(line.terminal());
            classes.save(c);
        }
        return progression();
    }

    /** Enchaînement des cycles : la fin de la maternelle nourrit le primaire, etc. */
    private static final Map<String, String> NEXT_CYCLE =
            Map.of("maternelle", "primary", "primary", "secondary");

    /**
     * Déduit l'ordre et les enchaînements à partir des libellés officiels
     * (SIL → CP → CE1…, Form 1 → Form 2…). Une classe monte vers celle du rang
     * suivant dans sa section, en gardant sa lettre quand elle existe (5e A → 4e A) ;
     * la dernière d'un cycle enchaîne sur la première du cycle suivant (CM2 → 6ème).
     * Ce n'est qu'une proposition : elle s'enregistre, puis se corrige à la main.
     */
    @Transactional
    public List<ProgressionView> autoProgression() {
        UUID schoolId = TenantContext.get();
        Scope scope = ParcoursContext.get();

        // Les cibles se cherchent dans toute l'école — CM2 doit pouvoir viser la 6ème
        // même quand l'administrateur travaille dans le seul parcours « Primaire FR ».
        List<SchoolClass> all = classes.findBySchoolIdOrderByName(schoolId);
        // Un admin de cycle ne réécrit l'enchaînement que de ses propres classes,
        // même si les cibles, elles, se cherchent dans toute l'école.
        Set<UUID> allowed = accessScope.allowedClassIds();
        List<SchoolClass> editable = all.stream()
                .filter(c -> allowed == null || allowed.contains(c.getId()))
                .filter(c -> StudentService.inScope(scope, c.getLevel(), c.getSubsystem()))
                .toList();

        // 1. Rang pédagogique de chaque classe reconnue, et sa lettre de section.
        Map<UUID, ClassLadder.Match> matched = new HashMap<>();
        for (SchoolClass c : all) {
            ClassLadder.Match m = ClassLadder.match(c.getLevel(), c.getSubsystem(), c.getName());
            if (m != null) matched.put(c.getId(), m);
        }

        // 2. Classes indexées par section puis par rang, pour trouver « celle d'après ».
        Map<String, Map<Integer, List<SchoolClass>>> bySectionAndOrder = new HashMap<>();
        for (SchoolClass c : all) {
            ClassLadder.Match m = matched.get(c.getId());
            if (m == null) continue;
            bySectionAndOrder
                    .computeIfAbsent(c.getSectionId(), k -> new HashMap<>())
                    .computeIfAbsent(m.order(), k -> new ArrayList<>())
                    .add(c);
        }

        for (SchoolClass c : editable) {
            ClassLadder.Match m = matched.get(c.getId());
            if (m == null) continue;                       // libellé non reconnu : laissé à l'admin
            c.setGradeOrder(m.order());

            List<SchoolClass> above = bySectionAndOrder
                    .getOrDefault(c.getSectionId(), Map.of())
                    .getOrDefault(m.order() + 1, List.of());
            SchoolClass target = pick(above, matched, m.suffix());

            // Fin de cycle : on bascule sur la première classe du cycle suivant, dans
            // le même sous-système (CM2 → 6ème, Class 6 → Form 1).
            if (target == null && m.lastRung()) {
                target = pick(entryClassesOf(all, matched, NEXT_CYCLE.get(c.getLevel()), c.getSubsystem()),
                              matched, m.suffix());
            }

            c.setNextClassId(target == null ? null : target.getId());
            // Seule la fin du secondaire est une vraie sortie d'établissement. Ailleurs,
            // une classe sans suite reste « non configurée » : la simulation renverra
            // l'élève au conseil plutôt que de le déclarer diplômé à tort.
            c.setTerminal(target == null && m.lastRung() && "secondary".equals(c.getLevel()));
            classes.save(c);
        }
        return progression();
    }

    /** Parmi des classes de même rang : celle qui garde la lettre, sinon la première. */
    private static SchoolClass pick(List<SchoolClass> options,
                                    Map<UUID, ClassLadder.Match> matched, String suffix) {
        if (options.isEmpty()) return null;
        return options.stream()
                .filter(x -> {
                    ClassLadder.Match xm = matched.get(x.getId());
                    return xm != null && !xm.suffix().isEmpty() && xm.suffix().equals(suffix);
                })
                .findFirst()
                .orElseGet(() -> options.stream().min(Comparator.comparing(SchoolClass::getName)).orElseThrow());
    }

    /** Classes de premier rang d'un cycle, dans un sous-système donné. */
    private static List<SchoolClass> entryClassesOf(List<SchoolClass> all,
                                                    Map<UUID, ClassLadder.Match> matched,
                                                    String level, String subsystem) {
        if (level == null) return List.of();
        return all.stream()
                .filter(c -> level.equals(c.getLevel()) && subsystem.equals(c.getSubsystem()))
                .filter(c -> { ClassLadder.Match m = matched.get(c.getId()); return m != null && m.order() == 1; })
                .toList();
    }

    // ---- Règles de décision ---------------------------------------------------

    @Transactional(readOnly = true)
    public List<RuleView> listRules() {
        UUID schoolId = TenantContext.get();
        Map<UUID, String> classNames = new HashMap<>();
        for (SchoolClass c : classes.findBySchoolIdOrderByName(schoolId)) classNames.put(c.getId(), c.getName());
        String lock = ParcoursContext.sectionLock();
        return rules.findBySchoolId(schoolId).stream()
                .filter(r -> lock == null || lock.equals(r.getLevel()))
                .sorted(Comparator.comparingInt(PromotionRule::specificity)
                        .thenComparing(r -> scopeLabel(r, classNames)))
                .map(r -> toView(r, classNames))
                .toList();
    }

    @Transactional
    public List<RuleView> saveRule(RuleUpsert in) {
        UUID schoolId = TenantContext.get();

        PromotionRule r = in.id() == null
                ? new PromotionRule()
                : rules.findByIdAndSchoolId(in.id(), schoolId)
                       .orElseThrow(() -> ApiException.notFound("Règle de passage"));
        r.setSchoolId(schoolId);

        UUID classId = in.classId();
        String level = blankToNull(in.level());
        String subsystem = blankToNull(in.subsystem());
        if (classId != null) {
            SchoolClass c = classes.findByIdAndSchoolId(classId, schoolId)
                    .orElseThrow(() -> ApiException.badRequest("Classe inconnue"));
            // Une règle de classe porte son propre parcours : les deux autres champs
            // deviendraient contradictoires.
            level = c.getLevel();
            subsystem = c.getSubsystem();
        }
        if (in.councilMargin().compareTo(in.passMark()) > 0) {
            throw ApiException.badRequest("La zone conseil ne peut pas dépasser le seuil d'admission");
        }
        // Un admin de cycle fixe les seuils de son cycle. La règle générale de
        // l'école (sans niveau) s'appliquerait aux trois : elle lui est fermée.
        if (accessScope.adminSection() != null) accessScope.assertSection(level);

        r.setClassId(classId);
        r.setLevel(level);
        r.setSubsystem(subsystem);
        r.setPassMark(in.passMark());
        r.setCouncilMargin(in.councilMargin());
        r.setMaxRepeats(in.maxRepeats());
        r.setUpdatedBy(currentUserId());
        r.setUpdatedAt(Instant.now());

        if (duplicateScope(schoolId, r)) {
            throw ApiException.conflict("Une règle existe déjà pour ce périmètre — modifiez-la");
        }
        rules.save(r);
        return listRules();
    }

    @Transactional
    public List<RuleView> deleteRule(UUID id) {
        UUID schoolId = TenantContext.get();
        PromotionRule r = rules.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Règle de passage"));
        if (accessScope.adminSection() != null) accessScope.assertSection(r.getLevel());
        if (r.specificity() == 0) {
            throw ApiException.badRequest("La règle générale de l'école ne peut pas être supprimée");
        }
        rules.delete(r);
        return listRules();
    }

    /**
     * La règle qui s'applique à une classe : la plus spécifique parmi celles dont
     * le périmètre la couvre. Une école sans aucune règle retombe sur 10/20.
     */
    @Transactional(readOnly = true)
    public PromotionRule ruleFor(SchoolClass cls) {
        return rules.findBySchoolId(TenantContext.get()).stream()
                .filter(r -> covers(r, cls))
                .max(Comparator.comparingInt(PromotionRule::specificity))
                .orElseGet(() -> {
                    PromotionRule fallback = new PromotionRule();
                    fallback.setSchoolId(cls.getSchoolId());
                    return fallback;                      // 10/20, sans zone conseil
                });
    }

    private static boolean covers(PromotionRule r, SchoolClass cls) {
        if (r.getClassId() != null) return r.getClassId().equals(cls.getId());
        if (r.getLevel() != null && !r.getLevel().equals(cls.getLevel())) return false;
        return r.getSubsystem() == null || r.getSubsystem().equals(cls.getSubsystem());
    }

    private boolean duplicateScope(UUID schoolId, PromotionRule candidate) {
        return rules.findBySchoolId(schoolId).stream()
                .filter(r -> candidate.getId() == null || !r.getId().equals(candidate.getId()))
                .anyMatch(r -> java.util.Objects.equals(r.getClassId(), candidate.getClassId())
                        && (candidate.getClassId() != null
                            || (java.util.Objects.equals(r.getLevel(), candidate.getLevel())
                                && java.util.Objects.equals(r.getSubsystem(), candidate.getSubsystem()))));
    }

    // ---- Année scolaire -------------------------------------------------------

    /** L'année courante de l'établissement, à défaut celle déduite de la date du jour. */
    @Transactional(readOnly = true)
    public String currentAcademicYear() {
        String label = jdbc.query(
                "SELECT label FROM academic_year WHERE school_id = ? AND is_current = true LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, TenantContext.get());
        if (label != null && !label.isBlank()) return label;
        LocalDate today = LocalDate.now();
        int start = today.getMonthValue() >= 8 ? today.getYear() : today.getYear() - 1;
        return start + "-" + (start + 1);
    }

    /** « 2025-2026 » → « 2026-2027 ». Décale d'un an tout libellé de la forme AAAA-AAAA. */
    public static String nextAcademicYear(String label) {
        if (label != null && label.matches("\\d{4}\\s*-\\s*\\d{4}")) {
            int start = Integer.parseInt(label.substring(0, 4));
            return (start + 1) + "-" + (start + 2);
        }
        LocalDate today = LocalDate.now();
        int start = today.getMonthValue() >= 8 ? today.getYear() + 1 : today.getYear();
        return start + "-" + (start + 1);
    }

    /** Crée l'année d'accueil si elle n'existe pas — sans toucher à l'année courante. */
    @Transactional
    public void ensureAcademicYear(String label) {
        UUID schoolId = TenantContext.get();
        Integer startYear = label.matches("\\d{4}.*") ? Integer.parseInt(label.substring(0, 4)) : null;
        jdbc.update("""
                INSERT INTO academic_year (school_id, label, start_year, is_current)
                VALUES (?, ?, ?, false)
                ON CONFLICT (school_id, label) DO NOTHING
                """, schoolId, label, startYear == null ? LocalDate.now().getYear() : startYear);
    }

    // ---- Utilitaires ----------------------------------------------------------

    private RuleView toView(PromotionRule r, Map<UUID, String> classNames) {
        return new RuleView(r.getId(), r.getLevel(), r.getSubsystem(), r.getClassId(),
                r.getClassId() == null ? null : classNames.getOrDefault(r.getClassId(), CLASS_PLACEHOLDER),
                r.getPassMark(), r.getCouncilMargin(), r.getMaxRepeats(),
                scopeLabel(r, classNames), r.specificity());
    }

    private static String scopeLabel(PromotionRule r, Map<UUID, String> classNames) {
        if (r.getClassId() != null) {
            return "Classe " + classNames.getOrDefault(r.getClassId(), CLASS_PLACEHOLDER);
        }
        if (r.getLevel() == null && r.getSubsystem() == null) return "Toute l'école";
        String level = switch (r.getLevel() == null ? "" : r.getLevel()) {
            case "maternelle" -> "Maternelle";
            case "primary" -> "Primaire";
            case "secondary" -> "Secondaire";
            default -> "Tous niveaux";
        };
        return r.getSubsystem() == null ? level : level + " " + r.getSubsystem();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }
}
