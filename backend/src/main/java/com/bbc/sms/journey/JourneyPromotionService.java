package com.bbc.sms.journey;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static com.bbc.sms.journey.dto.JourneyPromotionDtos.*;

@Service
public class JourneyPromotionService {
    private static final Set<String> DECISIONS = Set.of("PROMOTE", "REPEAT", "REVIEW", "GRADUATE", "HOLD");
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public JourneyPromotionService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ProgressionPathView> paths(UUID sourceSessionId, UUID targetSessionId) {
        assertSession(sourceSessionId); assertSession(targetSessionId);
        return jdbc.query("""
            SELECT p.*, sc.name source_name, tc.name target_name,
                   g.version_no graph_version_no,g.status graph_status
              FROM class_progression_path p
              JOIN school_class sc ON sc.id=p.source_class_id
              LEFT JOIN school_class tc ON tc.id=p.target_class_id
              JOIN progression_graph_version g ON g.id=p.graph_version_id
             WHERE p.school_id=? AND p.source_session_id=? AND p.target_session_id=? AND p.active
               AND g.id=(
                   SELECT g2.id FROM progression_graph_version g2
                    WHERE g2.school_id=p.school_id AND g2.source_session_id=p.source_session_id
                      AND g2.target_session_id=p.target_session_id
                      AND g2.status IN ('DRAFT','PUBLISHED')
                    ORDER BY CASE WHEN g2.status='DRAFT' THEN 0 ELSE 1 END, g2.version_no DESC
                    LIMIT 1
               )
             ORDER BY sc.level, sc.subsystem, sc.name
            """, this::path, TenantContext.get(), sourceSessionId, targetSessionId);
    }

    @Transactional(readOnly = true)
    public List<ProgressionGraphView> graphVersions(UUID sourceSessionId, UUID targetSessionId) {
        assertSession(sourceSessionId); assertSession(targetSessionId);
        List<ProgressionGraphView> graphs = jdbc.query("""
            SELECT g.*, ss.label source_label, ts.label target_label,
                   count(p.id) FILTER (WHERE p.active) edge_count
              FROM progression_graph_version g
              JOIN academic_session ss ON ss.id=g.source_session_id
              JOIN academic_session ts ON ts.id=g.target_session_id
              LEFT JOIN class_progression_path p ON p.graph_version_id=g.id
             WHERE g.school_id=? AND g.source_session_id=? AND g.target_session_id=?
             GROUP BY g.id,ss.label,ts.label
             ORDER BY g.version_no DESC
            """, this::graph, TenantContext.get(), sourceSessionId, targetSessionId);
        return graphs.stream()
                .map(value -> new ProgressionGraphView(value.id(), value.sourceSessionId(), value.sourceSessionLabel(),
                        value.targetSessionId(), value.targetSessionLabel(), value.versionNo(), value.status(),
                        value.copiedFromId(), value.publishedAt(), value.version(), value.edgeCount(),
                        validateGraph(value.id())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgressionGraphPreviewView previewGraphCopy(ProgressionGraphCopyRequest in) {
        assertSession(in.sourceSessionId());
        assertSession(in.targetSessionId());
        UUID sourceId = in.fromGraphVersionId() == null
                ? latestGraphId(in.sourceSessionId(), in.targetSessionId()) : in.fromGraphVersionId();
        if (sourceId == null) throw ApiException.notFound("Version de graphe à copier");
        ProgressionGraphView source = graphById(sourceId);
        SessionInfo targetSource = session(in.sourceSessionId());
        SessionInfo targetTarget = session(in.targetSessionId());
        Integer nextVersion = jdbc.queryForObject("SELECT coalesce(max(version_no),0)+1 FROM progression_graph_version WHERE school_id=? AND source_session_id=? AND target_session_id=?",
                Integer.class, TenantContext.get(), in.sourceSessionId(), in.targetSessionId());
        List<ProgressionPathView> added = pathsForGraph(source.id()).stream()
                .map(path -> new ProgressionPathView(null, in.sourceSessionId(), path.sourceClassId(), path.sourceClassName(),
                        in.targetSessionId(), path.targetClassId(), path.targetClassName(), path.terminal(), path.active(), 0,
                        null, nextVersion == null ? 1 : nextVersion, "DRAFT", path.edgeType(), path.displayOrder(),
                        path.allowSkip(), path.skipReason()))
                .toList();
        ProgressionGraphView proposed = new ProgressionGraphView(null, in.sourceSessionId(), targetSource.label(),
                in.targetSessionId(), targetTarget.label(), nextVersion == null ? 1 : nextVersion, "DRAFT",
                source.id(), null, 0, added.size(), source.blockers());
        return new ProgressionGraphPreviewView(source, proposed, added, List.of(), List.of());
    }

    @Transactional
    public ProgressionGraphView copyGraph(ProgressionGraphCopyRequest in) {
        ProgressionGraphPreviewView preview = previewGraphCopy(in);
        UUID sourceId = preview.source().id();
        UUID graphId = createDraftGraph(in.sourceSessionId(), in.targetSessionId(), sourceId);
        ProgressionGraphView result = graphById(graphId);
        audit.record("PROGRESSION_GRAPH_COPIED", "ProgressionGraphVersion", graphId.toString(), preview.source(), result, null);
        return result;
    }

    @Transactional
    public ProgressionGraphView publishGraph(UUID id, Long expectedVersion) {
        ProgressionGraphView before = graphById(id);
        if (!"DRAFT".equals(before.status())) throw ApiException.conflict("Seule une version DRAFT peut être publiée.");
        if (expectedVersion != null && expectedVersion != before.version()) {
            throw ApiException.staleVersion("La version du graphe a changé. Rechargez avant de publier.", before.version(), expectedVersion);
        }
        List<String> blockers = validateGraph(id);
        if (!blockers.isEmpty()) {
            throw ApiException.blockers("PROGRESSION_GRAPH_INVALID", "Corrigez le graphe avant publication.", blockers);
        }
        jdbc.update("""
            UPDATE progression_graph_version SET status='ARCHIVED', version=version+1
             WHERE school_id=? AND source_session_id=? AND target_session_id=?
               AND status='PUBLISHED' AND id<>?
            """, TenantContext.get(), before.sourceSessionId(), before.targetSessionId(), id);
        int changed = jdbc.update("""
            UPDATE progression_graph_version SET status='PUBLISHED',published_at=now(),
                   published_by=?,version=version+1
             WHERE id=? AND school_id=? AND status='DRAFT' AND (CAST(? AS bigint) IS NULL OR version=CAST(? AS bigint))
            """, currentUser(), id, TenantContext.get(), expectedVersion, expectedVersion);
        if (changed == 0) throw ApiException.staleVersion("La version du graphe a changé. Rechargez avant de publier.", graphVersion(id), expectedVersion == null ? -1 : expectedVersion);
        ProgressionGraphView result = graphById(id);
        audit.record("PROGRESSION_GRAPH_PUBLISHED", "ProgressionGraphVersion", id.toString(), before, result, null);
        return result;
    }

    @Transactional
    public ProgressionGraphView archiveGraph(UUID id, Long expectedVersion) {
        ProgressionGraphView before = graphById(id);
        if (expectedVersion != null && expectedVersion != before.version()) {
            throw ApiException.staleVersion("La version du graphe a changé. Rechargez avant de l'archiver.", before.version(), expectedVersion);
        }
        int changed = jdbc.update("UPDATE progression_graph_version SET status='ARCHIVED',version=version+1 WHERE id=? AND school_id=? AND status<>'ARCHIVED' AND (CAST(? AS bigint) IS NULL OR version=CAST(? AS bigint))",
                id, TenantContext.get(), expectedVersion, expectedVersion);
        if (changed == 0) throw ApiException.staleVersion("La version du graphe a changé.", graphVersion(id), expectedVersion == null ? -1 : expectedVersion);
        ProgressionGraphView result = graphById(id);
        audit.record("PROGRESSION_GRAPH_ARCHIVED", "ProgressionGraphVersion", id.toString(), before, result, null);
        return result;
    }

    @Transactional
    public ProgressionPathView savePath(ProgressionPathUpsert in) {
        UUID school = TenantContext.get();
        var sourceSession = session(in.sourceSessionId());
        var targetSession = session(in.targetSessionId());
        if (!targetSession.start.isAfter(sourceSession.end)) {
            throw ApiException.badRequest("La session cible doit commencer après la session source");
        }
        var source = schoolClass(in.sourceClassId());
        ClassInfo target = null;
        if (in.terminal()) {
            if (in.targetClassId() != null) throw ApiException.badRequest("Une classe terminale ne doit pas avoir de classe cible");
        } else {
            if (in.targetClassId() == null) throw ApiException.badRequest("Choisissez la classe de progression");
            target = schoolClass(in.targetClassId());
            if (!Objects.equals(source.subsystem, target.subsystem)) {
                throw ApiException.badRequest("Les classes source et cible doivent appartenir au même sous-système");
            }
        }
        String edgeType = clean(in.edgeType());
        if (edgeType == null) edgeType = in.terminal() ? "TERMINAL" : "DEFAULT";
        edgeType = edgeType.toUpperCase(Locale.ROOT);
        if (!Set.of("DEFAULT", "ALTERNATIVE", "TERMINAL").contains(edgeType)) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "EDGE_TYPE_INVALID",
                    "Le type d'arête doit être DEFAULT, ALTERNATIVE ou TERMINAL.", "edgeType", "Use DEFAULT, ALTERNATIVE, or TERMINAL.");
        }
        boolean allowSkip = Boolean.TRUE.equals(in.allowSkip());
        if (allowSkip && (in.skipReason() == null || in.skipReason().isBlank())) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "SKIP_REASON_REQUIRED",
                    "Un motif est obligatoire pour un saut de niveau.", "skipReason", "A reason is required for a skipped level.");
        }
        UUID graphVersion = ensureDraftGraphVersion(in.sourceSessionId(), in.targetSessionId());
        if (in.graphVersionId() != null && !in.graphVersionId().equals(graphVersion)) {
            throw ApiException.staleVersion("Le graphe de progression a changé. Rechargez avant de l'enregistrer.", graphVersion(graphVersion), 0);
        }
        UUID id = jdbc.query("""
            SELECT id FROM class_progression_path
             WHERE school_id=? AND graph_version_id=? AND source_class_id=? AND edge_type=?
             ORDER BY active DESC, display_order, version DESC LIMIT 1
            """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, school, graphVersion,
            in.sourceClassId(), edgeType);
        if (id == null) {
            id = UUID.randomUUID();
            try {
                jdbc.update("""
                    INSERT INTO class_progression_path
                        (id,school_id,source_session_id,source_class_id,target_session_id,target_class_id,terminal,graph_version_id,edge_type,display_order,allow_skip,skip_reason)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """, id, school, in.sourceSessionId(), in.sourceClassId(), in.targetSessionId(), in.targetClassId(), in.terminal(), graphVersion,
                        edgeType, in.displayOrder() == null ? 1 : in.displayOrder(), allowSkip, clean(in.skipReason()));
            } catch (DataIntegrityViolationException ex) {
                throw ApiException.conflict("Cette arête de progression existe déjà dans la version du graphe.");
            }
        } else {
            int changed = jdbc.update("""
                UPDATE class_progression_path SET target_class_id=?,terminal=?,active=true,
                       edge_type=?,display_order=?,allow_skip=?,skip_reason=?,version=version+1,updated_at=now()
                 WHERE id=? AND school_id=? AND (CAST(? AS bigint) IS NULL OR version=CAST(? AS bigint))
                """, in.targetClassId(), in.terminal(), edgeType,
                    in.displayOrder() == null ? 1 : in.displayOrder(), allowSkip, clean(in.skipReason()),
                    id, school, in.version(), in.version());
            if (changed == 0) throw ApiException.staleVersion("La règle de progression a été modifiée. Rechargez avant de l'enregistrer.",
                    currentPathVersion(id), in.version() == null ? -1 : in.version());
        }
        ProgressionPathView saved = pathById(id);
        audit.record("PROGRESSION_PATH_SAVED", "ClassProgressionPath", id.toString(), null, saved, null);
        return saved;
    }

    @Transactional
    public void deletePath(UUID id) {
        ProgressionPathView before = pathById(id);
        int changed = jdbc.update("UPDATE class_progression_path SET active=false, version=version+1, updated_at=now() WHERE id=? AND school_id=?",
                id, TenantContext.get());
        if (changed == 0) throw ApiException.notFound("Parcours de progression");
        audit.record("PROGRESSION_PATH_DISABLED", "ClassProgressionPath", id.toString(), before, null, null);
    }

    @Transactional(readOnly = true)
    public List<PromotionRuleView> rules(UUID sessionId) {
        assertSession(sessionId);
        return jdbc.query("""
            SELECT r.*,rs.id rule_set_id,rs.version_no rule_set_version,rs.status rule_set_status FROM promotion_rule r
              JOIN promotion_rule_set rs ON rs.id=r.rule_set_id
             WHERE r.school_id=? AND r.academic_session_id=? AND r.active
               AND rs.id=(
                   SELECT rs2.id FROM promotion_rule_set rs2
                    WHERE rs2.school_id=r.school_id AND rs2.academic_session_id=r.academic_session_id
                    ORDER BY CASE WHEN rs2.status='DRAFT' THEN 0 WHEN rs2.status='PUBLISHED' THEN 1 ELSE 2 END,
                             rs2.version_no DESC LIMIT 1
               )
            ORDER BY subsystem NULLS FIRST, level NULLS FIRST
            """, this::rule, TenantContext.get(), sessionId);
    }

    @Transactional
    public PromotionRuleView saveRule(PromotionRuleUpsert in) {
        assertSession(in.academicSessionId());
        if (in.reviewMin().compareTo(in.promoteMin()) > 0) {
            throw ApiException.badRequest("Le seuil de révision ne peut pas dépasser le seuil de promotion");
        }
        UUID school = TenantContext.get();
        String subsystem = clean(in.subsystem());
        String level = clean(in.level());
        UUID ruleSetId = ensureDraftRuleSet(in.academicSessionId());
        UUID id = jdbc.query("""
            SELECT id FROM promotion_rule WHERE school_id=? AND rule_set_id=?
              AND subsystem IS NOT DISTINCT FROM ? AND level IS NOT DISTINCT FROM ? AND active
             ORDER BY version DESC LIMIT 1
            """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                school, ruleSetId, subsystem, level);
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO promotion_rule
                    (id,school_id,academic_session_id,subsystem,level,promote_min,review_min,require_final_average,rule_set_id)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, id, school, in.academicSessionId(), subsystem, level, in.promoteMin(),
                    in.reviewMin(), in.requireFinalAverage(), ruleSetId);
        } else {
            int changed = jdbc.update("""
                UPDATE promotion_rule SET promote_min=?,review_min=?,require_final_average=?,version=version+1,updated_at=now()
                 WHERE id=? AND school_id=? AND active AND (CAST(? AS bigint) IS NULL OR version=CAST(? AS bigint))
                """, in.promoteMin(), in.reviewMin(), in.requireFinalAverage(), id, school, in.version(), in.version());
            if (changed == 0) throw ApiException.staleVersion("La règle de promotion a changé. Rechargez avant de l'enregistrer.",
                    ruleVersion(id), in.version() == null ? -1 : in.version());
        }
        PromotionRuleView saved = jdbc.queryForObject("""
            SELECT r.*,rs.id rule_set_id,rs.version_no rule_set_version,rs.status rule_set_status
              FROM promotion_rule r JOIN promotion_rule_set rs ON rs.id=r.rule_set_id
             WHERE r.id=? AND r.school_id=?
            """, this::rule, id, school);
        audit.record("PROMOTION_RULE_SAVED", "PromotionRule", id.toString(), null, saved, null);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PromotionRuleSetView> ruleSets(UUID sessionId) {
        assertSession(sessionId);
        return jdbc.query("""
            SELECT * FROM promotion_rule_set WHERE school_id=? AND academic_session_id=?
             ORDER BY version_no DESC
            """, (rs, n) -> ruleSet(rs), TenantContext.get(), sessionId);
    }

    @Transactional
    public PromotionRuleSetView saveRuleSet(PromotionRuleSetUpsert in) {
        assertSession(in.academicSessionId());
        String conditions = in.conditions().trim();
        UUID existing = jdbc.query("""
            SELECT id FROM promotion_rule_set
             WHERE school_id=? AND academic_session_id=? AND status='DRAFT'
             ORDER BY version_no DESC LIMIT 1
            """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), in.academicSessionId());
        UUID id;
        if (existing == null) {
            Integer next = jdbc.queryForObject("SELECT coalesce(max(version_no),0)+1 FROM promotion_rule_set WHERE school_id=? AND academic_session_id=?",
                    Integer.class, TenantContext.get(), in.academicSessionId());
            id = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO promotion_rule_set(id,school_id,academic_session_id,version_no,status,conditions)
                VALUES (?,?,?,?, 'DRAFT',?::jsonb)
                """, id, TenantContext.get(), in.academicSessionId(), next == null ? 1 : next, conditions);
        } else {
            int changed = jdbc.update("UPDATE promotion_rule_set SET conditions=?::jsonb,version=version+1 WHERE id=? AND school_id=? AND status='DRAFT' AND (CAST(? AS bigint) IS NULL OR version=CAST(? AS bigint))",
                    conditions, existing, TenantContext.get(), in.version(), in.version());
            if (changed == 0) throw ApiException.staleVersion("La règle de promotion a changé. Rechargez avant de l'enregistrer.", ruleSetVersion(existing), in.version() == null ? -1 : in.version());
            id = existing;
        }
        PromotionRuleSetView result = ruleSetById(id);
        audit.record("PROMOTION_RULE_SET_SAVED", "PromotionRuleSet", id.toString(), null, result, null);
        return result;
    }

    @Transactional
    public PromotionRuleSetView publishRuleSet(UUID id, Long expectedVersion) {
        PromotionRuleSetView before = ruleSetById(id);
        if (!"DRAFT".equals(before.status())) throw ApiException.conflict("Seule une version DRAFT peut être publiée.");
        if (expectedVersion != null && expectedVersion != before.version()) {
            throw ApiException.staleVersion("La règle de promotion a changé. Rechargez avant de publier.", before.version(), expectedVersion);
        }
        if (before.rules().isEmpty()) throw ApiException.blockers("PROMOTION_RULE_SET_EMPTY", "Ajoutez au moins une règle avant publication.", List.of("RULE_SET_EMPTY"));
        jdbc.update("UPDATE promotion_rule_set SET status='ARCHIVED' WHERE school_id=? AND academic_session_id=? AND status='PUBLISHED' AND id<>?",
                TenantContext.get(), before.academicSessionId(), id);
        int changed = jdbc.update("""
            UPDATE promotion_rule_set SET status='PUBLISHED',published_at=now(),published_by=?,version=version+1
             WHERE id=? AND school_id=? AND status='DRAFT' AND (CAST(? AS bigint) IS NULL OR version=CAST(? AS bigint))
            """, currentUser(), id, TenantContext.get(), expectedVersion, expectedVersion);
        if (changed == 0) throw ApiException.staleVersion("La règle de promotion a changé. Rechargez avant de publier.", ruleSetVersion(id), expectedVersion == null ? -1 : expectedVersion);
        PromotionRuleSetView result = ruleSetById(id);
        audit.record("PROMOTION_RULE_SET_PUBLISHED", "PromotionRuleSet", id.toString(), before, result, null);
        return result;
    }

    @Transactional
    public PromotionBatchView preview(PromotionPreviewRequest in) {
        UUID school = TenantContext.get();
        SessionInfo sourceSession = session(in.sourceSessionId());
        SessionInfo targetSession = session(in.targetSessionId());
        if (!targetSession.start.isAfter(sourceSession.end)) {
            throw ApiException.badRequest("La session cible doit suivre la session source");
        }
        Authority authority = resolveAuthority(in);
        if (in.idempotencyKey() != null && !in.idempotencyKey().isBlank()) {
            List<UUID> existing = jdbc.query("SELECT id FROM promotion_batch WHERE school_id=? AND idempotency_key=?",
                    (rs, n) -> rs.getObject(1, UUID.class), school, in.idempotencyKey().trim());
            if (!existing.isEmpty()) return batch(existing.getFirst());
        }
        UUID batchId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO promotion_batch(id, school_id, source_session_id, target_session_id, name, idempotency_key, created_by,
                                        graph_version_id,rule_set_id,preview_fingerprint)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """, batchId, school, in.sourceSessionId(), in.targetSessionId(), in.name().trim(), clean(in.idempotencyKey()), currentUser(),
                authority.graphId, authority.ruleSetId, clean(in.previewFingerprint()));

        String classFilter = in.sourceClassIds() == null || in.sourceClassIds().isEmpty()
                ? "" : " AND e.school_class_id IN (" + String.join(",", Collections.nCopies(in.sourceClassIds().size(), "?")) + ")";
        List<Object> args = new ArrayList<>(List.of(school, in.sourceSessionId()));
        if (in.sourceClassIds() != null) args.addAll(in.sourceClassIds());
        List<EnrollmentInfo> roster = jdbc.query("""
            SELECT e.id enrollment_id, e.student_id, e.school_class_id, e.class_name_snapshot,
                   e.level_snapshot, e.subsystem_snapshot, st.matricule, st.first_name, st.last_name
              FROM student_enrollment e JOIN student st ON st.id=e.student_id
             WHERE e.school_id=? AND e.academic_session_id=? AND e.status='ACTIVE'
            """ + classFilter + " ORDER BY e.class_name_snapshot, st.last_name, st.first_name",
            this::enrollment, args.toArray());
        if (roster.isEmpty()) throw ApiException.conflict("Aucun élève actif dans les classes sélectionnées pour cette session");

        for (EnrollmentInfo e : roster) createDecision(batchId, sourceSession, in.targetSessionId(), e, authority);
        PromotionBatchView result = batch(batchId);
        audit.record("PROMOTION_BATCH_PREVIEWED", "PromotionBatch", batchId.toString(), null, result, null);
        return result;
    }

    @Transactional
    public PromotionBatchView createReviewedBatch(PromotionPreviewRequest in) {
        if (in.idempotencyKey() == null || in.idempotencyKey().isBlank()) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "Une clé d'idempotence stable est obligatoire pour enregistrer un lot de révision.",
                    "idempotencyKey", "Provide a stable idempotency key.");
        }
        if (in.previewFingerprint() == null || in.previewFingerprint().isBlank()) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "PREVIEW_FINGERPRINT_REQUIRED",
                    "Enregistrez d'abord l'aperçu en lecture seule avant de créer le lot de révision.",
                    "previewFingerprint", "Read-only preview fingerprint is required.");
        }
        PromotionPreviewView preview = previewReadOnly(in);
        if (!preview.fingerprint().equals(in.previewFingerprint().trim())) {
            throw ApiException.staleVersion("L'aperçu de promotion est obsolète. Recalculez-le avant de l'enregistrer.", 0, 0);
        }
        return preview(new PromotionPreviewRequest(in.sourceSessionId(), in.targetSessionId(), in.name(),
                in.sourceClassIds(), in.idempotencyKey(), preview.fingerprint(),
                preview.graphVersionId(), preview.ruleSetId()));
    }

    private UUID ensureDraftGraphVersion(UUID sourceSessionId, UUID targetSessionId) {
        UUID id = jdbc.query("""
            SELECT id FROM progression_graph_version
             WHERE school_id=? AND source_session_id=? AND target_session_id=? AND status='DRAFT'
             ORDER BY version_no DESC LIMIT 1
            """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, TenantContext.get(), sourceSessionId, targetSessionId);
        if (id != null) return id;
        UUID prior = latestGraphId(sourceSessionId, targetSessionId);
        return createDraftGraph(sourceSessionId, targetSessionId, prior);
    }

    private UUID createDraftGraph(UUID sourceSessionId, UUID targetSessionId, UUID copiedFromId) {
        Integer next = jdbc.queryForObject("SELECT coalesce(max(version_no),0)+1 FROM progression_graph_version WHERE school_id=? AND source_session_id=? AND target_session_id=?", Integer.class, TenantContext.get(), sourceSessionId, targetSessionId);
        UUID created = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO progression_graph_version(id,school_id,source_session_id,target_session_id,version_no,status,copied_from_id)
            VALUES (?,?,?,?,?,'DRAFT',?)
            """, created, TenantContext.get(), sourceSessionId, targetSessionId, next == null ? 1 : next, copiedFromId);
        if (copiedFromId != null) {
            jdbc.update("""
                INSERT INTO class_progression_path
                    (school_id,source_session_id,source_class_id,target_session_id,target_class_id,
                     terminal,active,version,graph_version_id,edge_type,display_order,allow_skip,skip_reason)
                SELECT school_id,?,source_class_id,?,target_class_id,
                       terminal,active,0,?,edge_type,display_order,allow_skip,skip_reason
                  FROM class_progression_path
                 WHERE school_id=? AND graph_version_id=? AND active
                """, sourceSessionId, targetSessionId, created, TenantContext.get(), copiedFromId);
        }
        return created;
    }

    private UUID ensureDraftRuleSet(UUID academicSessionId) {
        UUID existing = jdbc.query("""
            SELECT id FROM promotion_rule_set
             WHERE school_id=? AND academic_session_id=? AND status='DRAFT'
             ORDER BY version_no DESC LIMIT 1
            """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), academicSessionId);
        if (existing != null) return existing;
        UUID prior = jdbc.query("""
            SELECT id FROM promotion_rule_set
             WHERE school_id=? AND academic_session_id=? AND status='PUBLISHED'
             ORDER BY version_no DESC LIMIT 1
            """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), academicSessionId);
        Integer next = jdbc.queryForObject("SELECT coalesce(max(version_no),0)+1 FROM promotion_rule_set WHERE school_id=? AND academic_session_id=?",
                Integer.class, TenantContext.get(), academicSessionId);
        UUID created = UUID.randomUUID();
        String conditions = prior == null ? "{}" : jdbc.queryForObject("SELECT conditions::text FROM promotion_rule_set WHERE id=?",
                String.class, prior);
        jdbc.update("""
            INSERT INTO promotion_rule_set(id,school_id,academic_session_id,version_no,status,conditions)
            VALUES (?,?,?,?, 'DRAFT',?::jsonb)
            """, created, TenantContext.get(), academicSessionId, next == null ? 1 : next, conditions == null ? "{}" : conditions);
        if (prior != null) {
            List<RuleSeed> seeds = jdbc.query("""
                SELECT subsystem,level,promote_min,review_min,require_final_average
                  FROM promotion_rule WHERE school_id=? AND rule_set_id=? AND active
                """, (rs,n) -> new RuleSeed(rs.getString("subsystem"), rs.getString("level"),
                        rs.getBigDecimal("promote_min"), rs.getBigDecimal("review_min"), rs.getBoolean("require_final_average")),
                    TenantContext.get(), prior);
            for (RuleSeed seed : seeds) {
                jdbc.update("""
                    INSERT INTO promotion_rule(id,school_id,academic_session_id,subsystem,level,promote_min,review_min,require_final_average,rule_set_id)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), TenantContext.get(), academicSessionId, seed.subsystem,
                        seed.level, seed.promoteMin, seed.reviewMin, seed.requireAverage, created);
            }
        }
        return created;
    }

    private long ruleVersion(UUID id) {
        Long value = jdbc.queryForObject("SELECT version FROM promotion_rule WHERE id=? AND school_id=?", Long.class, id, TenantContext.get());
        return value == null ? 0 : value;
    }

    private long ruleSetVersion(UUID id) {
        Long value = jdbc.queryForObject("SELECT version FROM promotion_rule_set WHERE id=? AND school_id=?", Long.class, id, TenantContext.get());
        return value == null ? 0 : value;
    }

    private Authority resolveAuthority(PromotionPreviewRequest in) {
        UUID graphId = in.graphVersionId() == null
                ? publishedGraphId(in.sourceSessionId(), in.targetSessionId()) : in.graphVersionId();
        Map<String, Object> graph = graphId == null ? null : jdbc.query("""
            SELECT id,version_no,version,status,source_session_id,target_session_id
              FROM progression_graph_version WHERE id=? AND school_id=?
            """, rs -> rs.next() ? Map.of(
                    "id", rs.getObject("id", UUID.class), "versionNo", rs.getInt("version_no"),
                    "version", rs.getLong("version"), "status", rs.getString("status"),
                    "source", rs.getObject("source_session_id", UUID.class), "target", rs.getObject("target_session_id", UUID.class)) : null,
            graphId, TenantContext.get());
        if (graph == null || !"PUBLISHED".equals(graph.get("status"))
                || !Objects.equals(in.sourceSessionId(), graph.get("source"))
                || !Objects.equals(in.targetSessionId(), graph.get("target"))) {
            throw ApiException.blockers("PUBLISHED_GRAPH_REQUIRED", "Publiez une version du graphe source/cible avant de prévisualiser.", List.of("PUBLISHED_GRAPH_REQUIRED"));
        }
        UUID ruleSetId = in.ruleSetId() == null ? publishedRuleSetId(in.sourceSessionId()) : in.ruleSetId();
        Map<String, Object> ruleSet = ruleSetId == null ? null : jdbc.query("""
            SELECT id,version_no,version,status,academic_session_id FROM promotion_rule_set
             WHERE id=? AND school_id=?
            """, rs -> rs.next() ? Map.of(
                    "id", rs.getObject("id", UUID.class), "versionNo", rs.getInt("version_no"),
                    "version", rs.getLong("version"), "status", rs.getString("status"),
                    "session", rs.getObject("academic_session_id", UUID.class)) : null,
            ruleSetId, TenantContext.get());
        if (ruleSet == null || !"PUBLISHED".equals(ruleSet.get("status"))
                || !Objects.equals(in.sourceSessionId(), ruleSet.get("session"))) {
            throw ApiException.blockers("PUBLISHED_RULE_SET_REQUIRED", "Publiez une version des règles avant de prévisualiser.", List.of("PUBLISHED_RULE_SET_REQUIRED"));
        }
        return new Authority(graphId, (Integer) graph.get("versionNo"), (Long) graph.get("version"),
                ruleSetId, (Integer) ruleSet.get("versionNo"), (Long) ruleSet.get("version"));
    }

    private UUID publishedRuleSetId(UUID sessionId) {
        return jdbc.query("""
            SELECT id FROM promotion_rule_set
             WHERE school_id=? AND academic_session_id=? AND status='PUBLISHED'
             ORDER BY version_no DESC LIMIT 1
            """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), sessionId);
    }

    private PromotionRuleSetView ruleSetById(UUID id) {
        PromotionRuleSetView value = jdbc.query("SELECT * FROM promotion_rule_set WHERE id=? AND school_id=?",
                rs -> rs.next() ? ruleSet(rs) : null, id, TenantContext.get());
        if (value == null) throw ApiException.notFound("Version des règles de promotion");
        List<PromotionRuleView> rules = jdbc.query("""
            SELECT r.*,rs.id rule_set_id,rs.version_no rule_set_version,rs.status rule_set_status
              FROM promotion_rule r JOIN promotion_rule_set rs ON rs.id=r.rule_set_id
             WHERE r.school_id=? AND r.rule_set_id=? AND r.active
             ORDER BY r.subsystem NULLS FIRST,r.level NULLS FIRST
            """, this::rule, TenantContext.get(), id);
        return new PromotionRuleSetView(value.id(), value.academicSessionId(), value.versionNo(), value.status(),
                value.conditions(), value.publishedAt(), value.version(), rules);
    }

    private long currentPathVersion(UUID id) {
        Long value = jdbc.queryForObject("SELECT version FROM class_progression_path WHERE id=? AND school_id=?", Long.class, id, TenantContext.get());
        return value == null ? 0 : value;
    }

    private UUID latestGraphId(UUID sourceSessionId, UUID targetSessionId) {
        return jdbc.query("""
            SELECT id FROM progression_graph_version
             WHERE school_id=? AND source_session_id=? AND target_session_id=?
               AND status IN ('DRAFT','PUBLISHED')
             ORDER BY CASE WHEN status='DRAFT' THEN 0 ELSE 1 END, version_no DESC
             LIMIT 1
            """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), sourceSessionId, targetSessionId);
    }

    private UUID publishedGraphId(UUID sourceSessionId, UUID targetSessionId) {
        UUID id = jdbc.query("""
            SELECT id FROM progression_graph_version
             WHERE school_id=? AND source_session_id=? AND target_session_id=? AND status='PUBLISHED'
             ORDER BY version_no DESC LIMIT 1
            """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), sourceSessionId, targetSessionId);
        return id;
    }

    private long graphVersion(UUID id) {
        Long value = jdbc.queryForObject("SELECT version FROM progression_graph_version WHERE id=? AND school_id=?",
                Long.class, id, TenantContext.get());
        return value == null ? 0 : value;
    }

    private ProgressionGraphView graphById(UUID id) {
        ProgressionGraphView value = jdbc.query("""
            SELECT g.*,ss.label source_label,ts.label target_label,
                   count(p.id) FILTER (WHERE p.active) edge_count
              FROM progression_graph_version g
              JOIN academic_session ss ON ss.id=g.source_session_id
              JOIN academic_session ts ON ts.id=g.target_session_id
              LEFT JOIN class_progression_path p ON p.graph_version_id=g.id
             WHERE g.id=? AND g.school_id=?
             GROUP BY g.id,ss.label,ts.label
            """, rs -> rs.next() ? graph(rs, 0) : null, id, TenantContext.get());
        if (value == null) throw ApiException.notFound("Version de graphe");
        return new ProgressionGraphView(value.id(), value.sourceSessionId(), value.sourceSessionLabel(),
                value.targetSessionId(), value.targetSessionLabel(), value.versionNo(), value.status(),
                value.copiedFromId(), value.publishedAt(), value.version(), value.edgeCount(), validateGraph(id));
    }

    private List<String> validateGraph(UUID id) {
        ProgressionGraphView graph = jdbc.query("""
            SELECT g.*,ss.label source_label,ts.label target_label,
                   count(p.id) FILTER (WHERE p.active) edge_count
              FROM progression_graph_version g
              JOIN academic_session ss ON ss.id=g.source_session_id
              JOIN academic_session ts ON ts.id=g.target_session_id
              LEFT JOIN class_progression_path p ON p.graph_version_id=g.id
             WHERE g.id=? AND g.school_id=?
             GROUP BY g.id,ss.label,ts.label
            """, rs -> rs.next() ? graph(rs, 0) : null, id, TenantContext.get());
        if (graph == null) return List.of("GRAPH_NOT_FOUND");
        List<GraphEdge> edges = jdbc.query("""
            SELECT p.*,sc.name source_name,sc.subsystem source_subsystem,sc.progression_rank source_rank,
                   tc.name target_name,tc.subsystem target_subsystem,tc.progression_rank target_rank
              FROM class_progression_path p
              JOIN school_class sc ON sc.id=p.source_class_id
              LEFT JOIN school_class tc ON tc.id=p.target_class_id
             WHERE p.school_id=? AND p.graph_version_id=? AND p.active
            """, this::graphEdge, TenantContext.get(), id);
        List<String> blockers = new ArrayList<>();
        Set<UUID> allClasses = new HashSet<>(jdbc.query("SELECT id FROM school_class WHERE school_id=?",
                (rs,n) -> rs.getObject(1, UUID.class), TenantContext.get()));
        Map<UUID, List<GraphEdge>> outgoing = new HashMap<>();
        for (GraphEdge edge : edges) {
            outgoing.computeIfAbsent(edge.sourceClassId, ignored -> new ArrayList<>()).add(edge);
            if (edge.terminal && edge.targetClassId != null) blockers.add("TERMINAL_HAS_TARGET:" + edge.sourceName);
            if (!edge.terminal && edge.targetClassId == null) blockers.add("TARGET_REQUIRED:" + edge.sourceName);
            if (edge.targetClassId != null && !allClasses.contains(edge.targetClassId)) blockers.add("TARGET_CLASS_MISSING:" + edge.sourceName);
            if (edge.targetClassId != null && edge.sourceClassId.equals(edge.targetClassId)) blockers.add("SELF_LOOP:" + edge.sourceName);
            if (edge.targetClassId != null && edge.targetSubsystem != null && !Objects.equals(edge.sourceSubsystem, edge.targetSubsystem)) {
                blockers.add("SUBSYSTEM_MISMATCH:" + edge.sourceName);
            }
            if (!Set.of("DEFAULT", "ALTERNATIVE", "TERMINAL").contains(edge.edgeType)) blockers.add("EDGE_TYPE_INVALID:" + edge.sourceName);
            if (edge.allowSkip && (edge.skipReason == null || edge.skipReason.isBlank())) blockers.add("SKIP_REASON_REQUIRED:" + edge.sourceName);
            if (edge.targetClassId != null && edge.sourceRank != null && edge.targetRank != null) {
                int delta = edge.targetRank - edge.sourceRank;
                if (delta <= 0) blockers.add("BACKWARD_EDGE:" + edge.sourceName);
                else if (delta > 1 && !edge.allowSkip) blockers.add("EXPLICIT_SKIP_REQUIRED:" + edge.sourceName);
            }
        }
        for (UUID classId : allClasses) {
            List<GraphEdge> classEdges = outgoing.getOrDefault(classId, List.of());
            if (classEdges.isEmpty()) blockers.add("UNMAPPED_SOURCE_CLASS:" + classId);
            long terminalCount = classEdges.stream().filter(e -> e.terminal).count();
            if (terminalCount > 0 && classEdges.size() > 1) blockers.add("TERMINAL_OUTGOING_EDGE:" + classId);
            long defaults = classEdges.stream().filter(e -> "DEFAULT".equals(e.edgeType)).count();
            if (defaults > 1) blockers.add("MULTIPLE_DEFAULT_EDGES:" + classId);
            Set<UUID> targets = new HashSet<>();
            for (GraphEdge edge : classEdges) if (edge.targetClassId != null && !targets.add(edge.targetClassId)) {
                blockers.add("DUPLICATE_TARGET_EDGE:" + classId);
            }
        }
        if (hasCycle(outgoing, allClasses)) blockers.add("GRAPH_CYCLE");
        return blockers.stream().distinct().sorted().toList();
    }

    private boolean hasCycle(Map<UUID, List<GraphEdge>> outgoing, Set<UUID> nodes) {
        Set<UUID> visiting = new HashSet<>();
        Set<UUID> visited = new HashSet<>();
        for (UUID node : nodes) if (cycle(node, outgoing, visiting, visited)) return true;
        return false;
    }

    private boolean cycle(UUID node, Map<UUID, List<GraphEdge>> outgoing,
                          Set<UUID> visiting, Set<UUID> visited) {
        if (visiting.contains(node)) return true;
        if (!visited.add(node)) return false;
        visiting.add(node);
        for (GraphEdge edge : outgoing.getOrDefault(node, List.of())) {
            if (edge.targetClassId != null && cycle(edge.targetClassId, outgoing, visiting, visited)) return true;
        }
        visiting.remove(node);
        return false;
    }

    /**
     * Read-only promotion preview.  This endpoint intentionally does not
     * allocate a promotion_batch or promotion_decision row; the returned
     * fingerprint is evidence for a later review/commit operation.
     */
    @Transactional(readOnly = true)
    public PromotionPreviewView previewReadOnly(PromotionPreviewRequest in) {
        UUID school = TenantContext.get();
        SessionInfo sourceSession = session(in.sourceSessionId());
        SessionInfo targetSession = session(in.targetSessionId());
        if (!targetSession.start.isAfter(sourceSession.end)) {
            throw ApiException.badRequest("La session cible doit suivre la session source");
        }
        Authority authority = resolveAuthority(in);
        List<EnrollmentInfo> roster = roster(in.sourceSessionId(), in.sourceClassIds());
        if (roster.isEmpty()) throw ApiException.blockers("PROMOTION_ROSTER_EMPTY",
                "Aucun élève actif dans les classes sélectionnées pour cette session", List.of("ROSTER_EMPTY"));
        List<PromotionCandidateView> candidates = roster.stream().map(e -> {
            DecisionDraft draft = decisionDraft(sourceSession, in.targetSessionId(), e, authority);
            String mappedName = draft.mappedTargetClassId == null ? null : schoolClass(draft.mappedTargetClassId).name;
            String targetName = draft.targetClassId == null ? null : schoolClass(draft.targetClassId).name;
            return new PromotionCandidateView(UUID.randomUUID(), e.studentId, e.matricule,
                    e.lastName + " " + e.firstName, e.enrollmentId, e.classId, e.className,
                    draft.mappedTargetClassId, mappedName, draft.targetClassId, targetName,
                    draft.average, draft.recommendation, draft.finalDecision, null,
                    draft.explanation, 0, draft.annualId, draft.annualAverage, null,
                    draft.annualDecision, draft.councilApproved, draft.allowedTargets, draft.blockers);
        }).toList();
        String fingerprint = fingerprint(in, candidates, authority);
        return new PromotionPreviewView(UUID.randomUUID().toString(), in.name().trim(), sourceSession.id,
                sourceSession.label, targetSession.id, targetSession.label, fingerprint, candidates.size(), candidates,
                authority.graphId, authority.graphVersionNo, authority.ruleSetId, authority.ruleSetVersion);
    }

    private List<EnrollmentInfo> roster(UUID sourceSessionId, List<UUID> sourceClassIds) {
        String classFilter = sourceClassIds == null || sourceClassIds.isEmpty()
                ? "" : " AND e.school_class_id IN (" + String.join(",", Collections.nCopies(sourceClassIds.size(), "?")) + ")";
        List<Object> args = new ArrayList<>(List.of(TenantContext.get(), sourceSessionId));
        if (sourceClassIds != null) args.addAll(sourceClassIds);
        return jdbc.query("""
            SELECT e.id enrollment_id, e.student_id, e.school_class_id, e.class_name_snapshot,
                   e.level_snapshot, e.subsystem_snapshot, st.matricule, st.first_name, st.last_name
              FROM student_enrollment e JOIN student st ON st.id=e.student_id
             WHERE e.school_id=? AND e.academic_session_id=? AND e.status='ACTIVE'
            """ + classFilter + " ORDER BY e.class_name_snapshot, st.last_name, st.first_name",
                this::enrollment, args.toArray());
    }

    @Transactional(readOnly = true)
    public PromotionBatchView batch(UUID id) {
        UUID school = TenantContext.get();
        BatchInfo b = jdbc.query("""
            SELECT b.*, ss.label source_label, ts.label target_label,
                   gv.version_no graph_version_no, rs.version_no rule_set_version
              FROM promotion_batch b JOIN academic_session ss ON ss.id=b.source_session_id
              JOIN academic_session ts ON ts.id=b.target_session_id
              LEFT JOIN progression_graph_version gv ON gv.id=b.graph_version_id
              LEFT JOIN promotion_rule_set rs ON rs.id=b.rule_set_id
             WHERE b.id=? AND b.school_id=?
            """, rs -> rs.next() ? batchInfo(rs) : null, id, school);
        if (b == null) throw ApiException.notFound("Lot de promotion");
        List<PromotionCandidateView> candidates = jdbc.query("""
            SELECT d.*, d.evidence->>'explanation' explanation,
                   d.evidence->>'annualBulletinId' annual_bulletin_id,
                   d.evidence->>'annualDecision' annual_decision,
                   d.evidence->>'councilApproved' council_approved,
                   d.evidence->>'blockers' evidence_blockers,
                   st.matricule, st.first_name, st.last_name,
                   sc.name source_name, mc.name mapped_name, tc.name target_name
              FROM promotion_decision d JOIN student st ON st.id=d.student_id
              JOIN school_class sc ON sc.id=d.source_class_id
              LEFT JOIN school_class mc ON mc.id=d.mapped_target_class_id
              LEFT JOIN school_class tc ON tc.id=d.target_class_id
             WHERE d.school_id=? AND d.batch_id=? ORDER BY sc.name, st.last_name, st.first_name
            """, this::candidate, school, id);
        int promote=0, repeat=0, graduate=0, review=0;
        for (var c : candidates) switch (c.finalDecision()) {
            case "PROMOTE" -> promote++; case "REPEAT", "HOLD" -> repeat++;
            case "GRADUATE" -> graduate++; default -> review++;
        }
        return new PromotionBatchView(b.id, b.name, b.sourceSessionId, b.sourceLabel,
                b.targetSessionId, b.targetLabel, b.status, candidates.size(), promote, repeat,
                graduate, review, b.version, b.createdAt, b.committedAt, candidates,
                b.graphVersionId, b.graphVersionNo == null ? 0 : b.graphVersionNo,
                b.ruleSetId, b.ruleSetVersion == null ? 0 : b.ruleSetVersion, b.previewFingerprint);
    }

    @Transactional(readOnly = true)
    public PromotionCommitPreviewView commitPreview(UUID id) {
        BatchInfo batch = batchInfo(id);
        List<DecisionInfo> decisions = jdbc.query("SELECT * FROM promotion_decision WHERE school_id=? AND batch_id=? ORDER BY student_id",
                this::mapDecisionInfo, TenantContext.get(), id);
        int promote = 0, repeat = 0, graduate = 0, review = 0;
        for (DecisionInfo decision : decisions) {
            switch (decision.finalDecision()) {
                case "PROMOTE" -> promote++;
                case "REPEAT", "HOLD" -> repeat++;
                case "GRADUATE" -> graduate++;
                default -> review++;
            }
        }
        List<String> blockers = new ArrayList<>();
        if (!"DRAFT".equals(batch.status())) blockers.add("BATCH_NOT_DRAFT");
        SessionInfo source = session(batch.sourceSessionId);
        SessionInfo target = session(batch.targetSessionId);
        if (!"OPEN".equals(source.status)) blockers.add("SOURCE_SESSION_NOT_OPEN");
        if (Set.of("CLOSED", "ARCHIVED").contains(target.status)) blockers.add("TARGET_SESSION_NOT_AVAILABLE");
        for (DecisionInfo decision : decisions) {
            if ("REVIEW".equals(decision.finalDecision())) blockers.add("DECISION_REVIEW_REQUIRED:" + decision.studentId);
            if (!"GRADUATE".equals(decision.finalDecision()) && decision.targetClassId == null) {
                blockers.add("TARGET_CLASS_REQUIRED:" + decision.studentId);
            }
            Integer active = jdbc.queryForObject("SELECT count(*) FROM student_enrollment WHERE id=? AND school_id=? AND status='ACTIVE'",
                    Integer.class, decision.sourceEnrollmentId, TenantContext.get());
            if (active == null || active == 0) blockers.add("SOURCE_ENROLLMENT_NOT_ACTIVE:" + decision.studentId);
            Integer duplicate = jdbc.queryForObject("SELECT count(*) FROM student_enrollment WHERE school_id=? AND student_id=? AND academic_session_id=? AND status IN ('ACTIVE','PLANNED')",
                    Integer.class, TenantContext.get(), decision.studentId, batch.targetSessionId);
            if (duplicate != null && duplicate > 0) blockers.add("TARGET_ENROLLMENT_EXISTS:" + decision.studentId);
            if (decision.targetClassId != null && !"GRADUATE".equals(decision.finalDecision())) {
                Integer allowed = jdbc.queryForObject("""
                    SELECT count(*) FROM class_progression_path
                     WHERE school_id=? AND source_session_id=? AND target_session_id=?
                       AND source_class_id=? AND target_class_id=? AND active AND NOT terminal
                       AND (CAST(? AS uuid) IS NULL OR graph_version_id=CAST(? AS uuid))
                    """, Integer.class, TenantContext.get(), batch.sourceSessionId, batch.targetSessionId,
                    decision.sourceClassId, decision.targetClassId, batch.graphVersionId, batch.graphVersionId);
                boolean repeatTarget = ("REPEAT".equals(decision.finalDecision()) || "HOLD".equals(decision.finalDecision()))
                        && decision.sourceClassId.equals(decision.targetClassId);
                if (!repeatTarget && (allowed == null || allowed == 0)) blockers.add("TARGET_CLASS_NOT_ALLOWED:" + decision.studentId);
            }
            String annualId = jdbc.queryForObject("SELECT evidence->>'annualBulletinId' FROM promotion_decision WHERE id=? AND school_id=?",
                    String.class, decision.id, TenantContext.get());
            Integer published = 0;
            try {
                UUID evidenceId = annualId == null || annualId.isBlank() ? null : UUID.fromString(annualId);
                if (evidenceId != null) published = jdbc.queryForObject("SELECT count(*) FROM bulletin_version WHERE id=? AND school_id=? AND state='PUBLISHED'",
                        Integer.class, evidenceId, TenantContext.get());
            } catch (IllegalArgumentException ignored) {
                published = 0;
            }
            if (published == null || published == 0) blockers.add("PUBLISHED_ANNUAL_REQUIRED:" + decision.studentId);
        }
        return new PromotionCommitPreviewView(batch.id, batch.status, decisions.size(), promote, repeat, graduate,
                review, blockers.stream().distinct().toList(), batch.graphVersionId,
                batch.graphVersionNo == null ? 0 : batch.graphVersionNo, batch.ruleSetId,
                batch.ruleSetVersion == null ? 0 : batch.ruleSetVersion);
    }

    @Transactional(readOnly = true)
    public List<PromotionBatchListItem> batches(UUID sourceSessionId, UUID targetSessionId, String status) {
        if (sourceSessionId != null) assertSession(sourceSessionId);
        if (targetSessionId != null) assertSession(targetSessionId);
        return jdbc.query("""
            SELECT b.*,ss.label source_label,ts.label target_label,
                   count(d.id) candidate_count,
                   count(d.id) FILTER (WHERE d.final_decision='REVIEW') blocked_count
              FROM promotion_batch b
              JOIN academic_session ss ON ss.id=b.source_session_id
              JOIN academic_session ts ON ts.id=b.target_session_id
              LEFT JOIN promotion_decision d ON d.batch_id=b.id
             WHERE b.school_id=?
               AND (CAST(? AS uuid) IS NULL OR b.source_session_id=CAST(? AS uuid))
               AND (CAST(? AS uuid) IS NULL OR b.target_session_id=CAST(? AS uuid))
               AND (CAST(? AS varchar) IS NULL OR b.status=CAST(? AS varchar))
             GROUP BY b.id,ss.label,ts.label
             ORDER BY b.created_at DESC
            """, (rs,n) -> new PromotionBatchListItem(rs.getObject("id",UUID.class),rs.getString("name"),
                    rs.getObject("source_session_id",UUID.class),rs.getString("source_label"),
                    rs.getObject("target_session_id",UUID.class),rs.getString("target_label"),rs.getString("status"),
                    rs.getInt("candidate_count"),rs.getInt("blocked_count"),rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("committed_at")==null?null:rs.getTimestamp("committed_at").toInstant(),rs.getLong("version")),
                TenantContext.get(), sourceSessionId, sourceSessionId, targetSessionId, targetSessionId, status, status);
    }

    @Transactional
    public void cancelBatch(UUID id, PromotionCancelRequest in) {
        BatchInfo before = batchInfo(id);
        if (!"DRAFT".equals(before.status())) throw ApiException.conflict("Seul un lot DRAFT peut être annulé avant validation.");
        int changed = jdbc.update("""
            UPDATE promotion_batch SET status='CANCELLED',cancelled_by=?,cancelled_at=now(),cancel_reason=?,version=version+1
             WHERE id=? AND school_id=? AND status='DRAFT'
            """, currentUser(), in.reason().trim(), id, TenantContext.get());
        if (changed == 0) throw ApiException.conflict("Le lot a déjà été modifié.");
        audit.record("PROMOTION_BATCH_CANCELLED", "PromotionBatch", id.toString(), before, batchInfo(id), in.reason());
    }

    @Transactional(readOnly = true)
    public List<PromotionDecisionHistoryView> decisionHistory(UUID decisionId) {
        decisionInfo(decisionId);
        return jdbc.query("""
            SELECT * FROM promotion_decision_history
             WHERE school_id=? AND decision_id=? ORDER BY created_at
            """, (rs,n) -> new PromotionDecisionHistoryView(rs.getObject("id",UUID.class),
                    rs.getObject("decision_id",UUID.class),rs.getString("from_decision"),rs.getString("to_decision"),
                    rs.getObject("target_class_id",UUID.class),rs.getString("reason"),
                    rs.getObject("actor_user_id",UUID.class),rs.getTimestamp("created_at").toInstant()),
                TenantContext.get(), decisionId);
    }

    @Transactional
    public PromotionCandidateView override(UUID id, PromotionOverrideRequest in) {
        String decision = in.finalDecision().trim().toUpperCase(Locale.ROOT);
        if (!DECISIONS.contains(decision) || "REVIEW".equals(decision)) {
            throw ApiException.badRequest("Choisissez PROMOTE, REPEAT, HOLD ou GRADUATE");
        }
        DecisionInfo current = decisionInfo(id);
        ensureDraft(current.batchId);
        if (in.version() != null && in.version() != current.version) throw ApiException.conflict("Cette décision a été modifiée par un autre utilisateur");
        UUID target = "GRADUATE".equals(decision) ? null : in.targetClassId();
        if (target == null && !"GRADUATE".equals(decision)) {
            target = ("REPEAT".equals(decision) || "HOLD".equals(decision)) ? current.sourceClassId : current.mappedTargetClassId;
        }
        if (target != null) {
            ClassInfo targetClass = schoolClass(target);
            ClassInfo sourceClass = schoolClass(current.sourceClassId);
            BatchInfo batch = batchInfo(current.batchId);
            boolean configuredTarget = jdbc.queryForObject("""
                    SELECT count(*) FROM class_progression_path
                     WHERE school_id=? AND source_session_id=? AND target_session_id=?
                       AND source_class_id=? AND target_class_id=? AND active AND NOT terminal
                       AND (CAST(? AS uuid) IS NULL OR graph_version_id=CAST(? AS uuid))
                    """, Integer.class, TenantContext.get(), batch.sourceSessionId, batch.targetSessionId,
                    current.sourceClassId, target, batch.graphVersionId, batch.graphVersionId) > 0;
            if (!Objects.equals(target, current.sourceClassId) && !configuredTarget) {
                throw ApiException.coded(org.springframework.http.HttpStatus.BAD_REQUEST, "TARGET_CLASS_NOT_ALLOWED",
                        "La classe cible doit être l'un des parcours configurés ou la classe source pour une répétition.");
            }
            if (!Objects.equals(targetClass.subsystem, sourceClass.subsystem))
                throw ApiException.badRequest("La classe cible doit appartenir au même sous-système");
        }
        int changed = jdbc.update("""
            UPDATE promotion_decision SET final_decision=?, target_class_id=?, override_reason=?,
                   decided_by=?, decided_at=now(), version=version+1
             WHERE id=? AND school_id=? AND version=?
            """, decision, target, in.reason().trim(), currentUser(), id, TenantContext.get(), current.version);
        if (changed == 0) throw ApiException.conflict("Cette décision a été modifiée par un autre utilisateur");
        jdbc.update("""
            INSERT INTO promotion_decision_history
                (school_id,decision_id,from_decision,to_decision,target_class_id,reason,actor_user_id)
            VALUES (?,?,?,?,?,?,?)
            """, TenantContext.get(), id, current.finalDecision, decision, target, in.reason().trim(), currentUser());
        PromotionCandidateView result = candidateById(id);
        audit.record("PROMOTION_DECISION_OVERRIDDEN", "PromotionDecision", id.toString(), current, result, in.reason());
        return result;
    }

    @Transactional
    public PromotionBatchView commit(UUID id, PromotionCommitRequest in) {
        BatchInfo batch = batchInfo(id);
        if (!"DRAFT".equals(batch.status)) {
            if ("COMMITTED".equals(batch.status)) return batch(id);
            throw ApiException.conflict("Ce lot ne peut plus être validé");
        }
        if (in.version() != null && in.version() != batch.version) throw ApiException.conflict("Le lot a été modifié par un autre utilisateur");
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class,
                TenantContext.get() + ":promotion-batch:" + id);
        batch = batchInfo(id);
        if (!"DRAFT".equals(batch.status)) {
            if ("COMMITTED".equals(batch.status)) return batch(id);
            throw ApiException.conflict("Ce lot ne peut plus être validé");
        }
        List<DecisionInfo> decisions = jdbc.query("SELECT * FROM promotion_decision WHERE school_id=? AND batch_id=? ORDER BY student_id",
                this::mapDecisionInfo, TenantContext.get(), id);
        List<String> blockers = new ArrayList<>();
        for (DecisionInfo d : decisions) {
            if ("REVIEW".equals(d.finalDecision)) blockers.add(studentName(d.studentId) + " : décision à réviser");
            if (!"GRADUATE".equals(d.finalDecision) && d.targetClassId == null) blockers.add(studentName(d.studentId) + " : classe cible manquante");
            Integer existing = jdbc.queryForObject("SELECT count(*) FROM student_enrollment WHERE school_id=? AND student_id=? AND academic_session_id=? AND status IN ('ACTIVE','PLANNED')",
                    Integer.class, TenantContext.get(), d.studentId, batch.targetSessionId);
            if (existing != null && existing > 0) blockers.add(studentName(d.studentId) + " : déjà inscrit dans la session cible");
            Integer sourceActive = jdbc.queryForObject("SELECT count(*) FROM student_enrollment WHERE id=? AND school_id=? AND status='ACTIVE'",
                    Integer.class, d.sourceEnrollmentId, TenantContext.get());
            if (sourceActive == null || sourceActive == 0) blockers.add(studentName(d.studentId) + " : inscription source non active");
            String annualId = jdbc.queryForObject("SELECT evidence->>'annualBulletinId' FROM promotion_decision WHERE id=? AND school_id=?",
                    String.class, d.id, TenantContext.get());
            try {
                UUID evidenceId = annualId == null || annualId.isBlank() ? null : UUID.fromString(annualId);
                Integer published = evidenceId == null ? 0 : jdbc.queryForObject("SELECT count(*) FROM bulletin_version WHERE id=? AND school_id=? AND state='PUBLISHED'",
                        Integer.class, evidenceId, TenantContext.get());
                if (published == null || published == 0) blockers.add(studentName(d.studentId) + " : preuve annuelle publiée absente ou obsolète");
            } catch (IllegalArgumentException ex) {
                blockers.add(studentName(d.studentId) + " : preuve annuelle publiée invalide");
            }
        }
        if (!blockers.isEmpty()) throw ApiException.conflict("Validation impossible — " + String.join(" ; ", blockers));

        SessionInfo sourceSession = session(batch.sourceSessionId);
        SessionInfo targetSession = session(batch.targetSessionId);
        if (!"OPEN".equals(sourceSession.status)) {
            throw ApiException.blockers("SOURCE_SESSION_NOT_OPEN", "La session source doit rester OPEN pendant la préparation du transfert.", List.of("SOURCE_SESSION_NOT_OPEN"));
        }
        if (Set.of("CLOSED", "ARCHIVED").contains(targetSession.status)) {
            throw ApiException.blockers("TARGET_SESSION_NOT_AVAILABLE", "La session cible n'accepte pas de nouvelle inscription planifiée.", List.of("TARGET_SESSION_NOT_AVAILABLE"));
        }
        for (DecisionInfo d : decisions) commitDecision(d, batch, sourceSession, targetSession);
        int changed = jdbc.update("""
            UPDATE promotion_batch SET status='COMMITTED', committed_by=?, committed_at=now(),
                   commit_reason=?, version=version+1 WHERE id=? AND school_id=? AND version=?
            """, currentUser(), in.reason().trim(), id, TenantContext.get(), batch.version);
        if (changed == 0) throw ApiException.conflict("Le lot a été modifié pendant la validation");
        createPromotionRegister(id);
        PromotionBatchView result = batch(id);
        audit.record("PROMOTION_BATCH_COMMITTED", "PromotionBatch", id.toString(), batch, result, in.reason());
        return result;
    }

    @Transactional(readOnly = true)
    public PromotionRegisterView register(UUID batchId) {
        batchInfo(batchId);
        PromotionRegisterView value = jdbc.query("SELECT id,batch_id,sha256,created_at,manifest::text FROM promotion_register WHERE school_id=? AND batch_id=?",
                rs -> rs.next() ? new PromotionRegisterView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getString(5)) : null,
                TenantContext.get(), batchId);
        if (value == null) throw ApiException.notFound("Registre de promotion");
        return value;
    }

    private PromotionRegisterView createPromotionRegister(UUID batchId) {
        String manifest = jdbc.query("""
                SELECT json_build_object(
                    'batchId',?, 'decisions', coalesce(json_agg(json_build_object(
                        'decisionId',d.id, 'studentId',d.student_id, 'finalDecision',d.final_decision,
                        'targetClassId',d.target_class_id, 'enrollmentId',d.committed_enrollment_id,
                        'evidence',d.evidence,
                        'annualSnapshot', coalesce((SELECT json_build_object(
                            'id',v.id,'version',v.version,'snapshotHash',v.snapshot_hash,
                            'average',v.average,'state',v.state
                        ) FROM bulletin_version v
                         WHERE v.id::text=NULLIF(d.evidence->>'annualBulletinId','')
                           AND v.school_id=d.school_id), '{}'::json),
                        'documents', coalesce((SELECT json_agg(json_build_object(
                            'id',g.id,'documentNumber',g.document_number,'sha256',g.sha256,
                            'status',g.status,'locale',g.locale
                        ) ORDER BY g.locale,g.generated_at)
                          FROM generated_document g
                         WHERE g.school_id=d.school_id AND g.aggregate_type='BulletinVersion'
                           AND g.aggregate_id=d.evidence->>'annualBulletinId'
                           AND g.status<>'REVOKED'), '[]'::json)
                    ) ORDER BY d.student_id),'[]'::json))::text
                  FROM promotion_decision d
                 WHERE d.school_id=? AND d.batch_id=?
                """, rs -> rs.next() ? rs.getString(1) : null, batchId, TenantContext.get(), batchId);
        if (manifest == null) throw ApiException.conflict("Impossible de créer le registre de promotion");
        String hash = sha256Text(manifest);
        jdbc.update("""
                INSERT INTO promotion_register(id,school_id,batch_id,manifest,sha256,created_by)
                VALUES (gen_random_uuid(),?,?,?::jsonb,?,?)
                ON CONFLICT (school_id,batch_id) DO UPDATE SET manifest=excluded.manifest,sha256=excluded.sha256,created_by=excluded.created_by
                """, TenantContext.get(), batchId, manifest, hash, currentUser());
        return register(batchId);
    }

    @Transactional
    public PromotionActivationView activatePlanned(UUID enrollmentId, PromotionActivationRequest in) {
        Map<String, Object> row = jdbc.queryForMap("""
            SELECT e.id,e.student_id,e.previous_enrollment_id,e.school_class_id,e.status,e.planned_on,c.name
              FROM student_enrollment e LEFT JOIN school_class c ON c.id=e.school_class_id
             WHERE e.id=? AND e.school_id=?
             FOR UPDATE OF e
            """, enrollmentId, TenantContext.get());
        String status = (String) row.get("status");
        if ("ACTIVE".equals(status)) {
            return new PromotionActivationView(enrollmentId, (UUID) row.get("previous_enrollment_id"),
                    (UUID) row.get("student_id"), status, (String) row.get("name"));
        }
        if (!"PLANNED".equals(status)) throw ApiException.conflict("Cette inscription planifiée ne peut plus être activée");
        UUID studentId = (UUID) row.get("student_id");
        UUID sourceEnrollmentId = (UUID) row.get("previous_enrollment_id");
        java.sql.Date plannedOn = (java.sql.Date) row.get("planned_on");
        LocalDate activationDate = plannedOn == null ? LocalDate.now() : plannedOn.toLocalDate();
        jdbc.update("UPDATE student_enrollment SET status='ACTIVE', enrolled_on=COALESCE(enrolled_on,?), activation_reason=?, version=version+1 WHERE id=? AND status='PLANNED'",
                activationDate, in.reason().trim(), enrollmentId);
        int sourceChanged = 0;
        if (sourceEnrollmentId != null) {
            sourceChanged = jdbc.update("""
                UPDATE student_enrollment SET status='COMPLETED', exited_on=?, reason=?, version=version+1
                 WHERE id=? AND school_id=? AND status='ACTIVE'
                """, activationDate.minusDays(1), in.reason().trim(), sourceEnrollmentId, TenantContext.get());
        }
        UUID targetClassId = (UUID) row.get("school_class_id");
        if (targetClassId != null) {
            ClassInfo target = schoolClass(targetClassId);
            jdbc.update("UPDATE student SET class_id=?,class_name=?,level=?,subsystem=? WHERE id=? AND school_id=?",
                    target.id, target.name, target.level, target.subsystem, studentId, TenantContext.get());
        }
        jdbc.update("""
            INSERT INTO promotion_transition_event
                (school_id,student_id,source_enrollment_id,target_enrollment_id,action,reason,actor_user_id)
            VALUES (?,?,?,?, 'ACTIVATED',?,?)
            """, TenantContext.get(), studentId, sourceEnrollmentId, enrollmentId, in.reason().trim(), currentUser());
        if (sourceChanged > 0) {
            jdbc.update("""
                INSERT INTO promotion_transition_event
                    (school_id,student_id,source_enrollment_id,target_enrollment_id,action,reason,actor_user_id)
                VALUES (?,?,?,?, 'SOURCE_COMPLETED',?,?)
                """, TenantContext.get(), studentId, sourceEnrollmentId, enrollmentId, in.reason().trim(), currentUser());
        }
        jdbc.update("""
            INSERT INTO journey_event
                (school_id,student_id,event_type,payload,visibility)
            VALUES (?,?,?,?::jsonb,'PARENT')
            """, TenantContext.get(), studentId, "PROMOTION_ACTIVATED",
                "{\"targetEnrollmentId\":\"" + enrollmentId + "\",\"class\":\"" + jsonEscape((String) row.get("name")) + "\"}");
        return new PromotionActivationView(enrollmentId, sourceEnrollmentId, studentId, "ACTIVE", (String) row.get("name"));
    }

    private String fingerprint(PromotionPreviewRequest in, List<PromotionCandidateView> candidates, Authority authority) {
        String payload = in.sourceSessionId() + "|" + in.targetSessionId() + "|" + in.name().trim() + "|"
                + authority.graphId + ":" + authority.graphVersionNo + "|" + authority.ruleSetId + ":" + authority.ruleSetVersion + "|"
                + candidates.stream().map(c -> c.studentId() + ":" + c.recommendation() + ":"
                        + c.targetClassId() + ":" + c.finalAverage()).sorted().reduce("", (a, b) -> a + b);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot fingerprint promotion preview", ex);
        }
    }
    private String sha256Text(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot hash promotion register", ex);
        }
    }

    private DecisionDraft decisionDraft(SessionInfo sourceSession, UUID targetSessionId, EnrollmentInfo e, Authority authority) {
        PathInfo path = jdbc.query("""
            SELECT * FROM class_progression_path WHERE school_id=? AND source_session_id=?
             AND target_session_id=? AND source_class_id=? AND graph_version_id=? AND active
            """, rs -> rs.next() ? pathInfo(rs) : null, TenantContext.get(), sourceSession.id, targetSessionId, e.classId, authority.graphId);
        RuleInfo rule = resolveRule(authority.ruleSetId, e.subsystem, e.level);
        AnnualEvidence annual = jdbc.query("""
            SELECT v.id, v.average, v.snapshot_json->'conduct'->>'decisionCode' AS decision_code,
                   (v.snapshot_json->'conduct'->>'status')='APPROVED' AS council_approved
              FROM bulletin_version v
              JOIN academic_reporting_period p ON p.id=v.reporting_period_id
             WHERE v.school_id=? AND v.student_id=? AND v.academic_session_id=?
               AND p.period_type='ANNUAL_RESULT' AND v.state='PUBLISHED'
             ORDER BY v.published_at DESC NULLS LAST, v.created_at DESC LIMIT 1
            """, rs -> rs.next() ? new AnnualEvidence(rs.getObject("id", UUID.class), rs.getBigDecimal("average"), rs.getString("decision_code"), rs.getBoolean("council_approved")) : null,
                 TenantContext.get(), e.studentId, sourceSession.id);
        List<PromotionTargetOption> allowedTargets = jdbc.query("""
            SELECT p.target_class_id,tc.name,p.edge_type,p.terminal,p.allow_skip
              FROM class_progression_path p
              LEFT JOIN school_class tc ON tc.id=p.target_class_id
             WHERE p.school_id=? AND p.graph_version_id=? AND p.source_class_id=? AND p.active
             ORDER BY p.display_order,p.edge_type,tc.name
            """, (rs,n) -> new PromotionTargetOption(rs.getObject("target_class_id",UUID.class),
                    rs.getString("name"),rs.getString("edge_type"),rs.getBoolean("terminal"),rs.getBoolean("allow_skip")),
                TenantContext.get(), authority.graphId, e.classId);
        BigDecimal average = annual == null ? null : annual.average;
        String averageSource = annual == null ? "MISSING" : "PUBLISHED_ANNUAL_BULLETIN";
        String recommendation;
        UUID mapped = path == null ? null : path.targetClassId;
        String explanation;
        List<String> blockers = new ArrayList<>();
        if (path == null) {
            blockers.add("PROGRESSION_PATH_MISSING");
            recommendation = "REVIEW"; explanation = "Aucun parcours de progression configuré pour cette classe.";
        } else if (annual == null) {
            blockers.add("PUBLISHED_ANNUAL_REQUIRED");
            recommendation = "REVIEW"; explanation = "Le bulletin annuel publié est obligatoire avant toute recommandation.";
        } else if (!annual.councilApproved) {
            blockers.add("COUNCIL_APPROVAL_REQUIRED");
            recommendation = "REVIEW"; explanation = "La preuve approuvée du conseil de classe est obligatoire avant toute recommandation.";
        } else if (path.terminal) {
            recommendation = "GRADUATE"; explanation = "Classe terminale et bulletin annuel approuvé : sortie/diplôme recommandé.";
        } else if (annual != null && annual.decisionCode != null && DECISIONS.contains(annual.decisionCode.toUpperCase(Locale.ROOT))) {
            recommendation = annual.decisionCode.toUpperCase(Locale.ROOT);
            explanation = "Décision annuelle publiée : " + recommendation + " (bulletin annuel " + annual.id + ").";
        } else if (rule == null) {
            blockers.add("PROMOTION_RULE_NOT_APPLICABLE");
            recommendation = "REVIEW"; explanation = "Aucune règle de promotion publiée pour cette session.";
        } else if (average == null && rule.requireAverage) {
            blockers.add("ANNUAL_AVERAGE_MISSING");
            recommendation = "REVIEW"; explanation = "Moyenne finale indisponible : décision manuelle requise.";
        } else if (average != null && average.compareTo(rule.promoteMin) >= 0) {
            recommendation = "PROMOTE"; explanation = "Moyenne " + average + "/20 ≥ seuil de promotion " + rule.promoteMin + ".";
        } else if (average != null && average.compareTo(rule.reviewMin) < 0) {
            recommendation = "REPEAT"; explanation = "Moyenne " + average + "/20 < seuil de révision " + rule.reviewMin + ".";
        } else {
            recommendation = "REVIEW"; explanation = "Résultat dans la zone de révision du conseil de classe.";
        }
        UUID target = switch (recommendation) {
            case "PROMOTE" -> mapped; case "REPEAT", "HOLD" -> e.classId; default -> null;
        };
        String annualId = annual == null ? "" : ",\"annualBulletinId\":\"" + annual.id + "\"";
        String annualDecision = annual == null || annual.decisionCode == null ? "" : ",\"annualDecision\":\"" + jsonEscape(annual.decisionCode) + "\"";
        String council = annual != null && annual.councilApproved ? ",\"councilApproved\":true" : ",\"councilApproved\":false";
        String ruleEvidence = rule == null ? "" : ",\"promoteMin\":" + rule.promoteMin + ",\"reviewMin\":" + rule.reviewMin;
        String evidence = "{\"averageSource\":\"" + averageSource + "\"" + annualId + annualDecision + council + ruleEvidence
                + ",\"graphVersionId\":\"" + authority.graphId + "\",\"graphVersionNo\":" + authority.graphVersionNo
                + ",\"ruleSetId\":\"" + authority.ruleSetId + "\",\"ruleSetVersion\":" + authority.ruleSetVersion
                + ",\"blockers\":\"" + jsonEscape(String.join(",", blockers)) + "\""
                + ",\"explanation\":\"" + jsonEscape(explanation) + "\"}";
        return new DecisionDraft(mapped, target, average, recommendation, recommendation, explanation, evidence,
                annual == null ? null : annual.id, average, annual == null ? null : annual.decisionCode,
                annual != null && annual.councilApproved, allowedTargets, blockers);
    }

    private void createDecision(UUID batchId, SessionInfo sourceSession, UUID targetSessionId, EnrollmentInfo e, Authority authority) {
        DecisionDraft draft = decisionDraft(sourceSession, targetSessionId, e, authority);
        jdbc.update("""
            INSERT INTO promotion_decision
                (school_id,batch_id,student_id,source_enrollment_id,source_class_id,mapped_target_class_id,
                 target_class_id,final_average,recommendation,final_decision,evidence)
            VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb)
                """, TenantContext.get(), batchId, e.studentId, e.enrollmentId, e.classId, draft.mappedTargetClassId,
                draft.targetClassId, draft.average, draft.recommendation, draft.finalDecision, draft.evidence);
    }

    private void commitDecision(DecisionInfo d, BatchInfo batch, SessionInfo sourceSession, SessionInfo targetSession) {
        UUID newEnrollment = null;
        String result;
        if ("GRADUATE".equals(d.finalDecision)) result = "graduated";
        else {
            result = "PROMOTE".equals(d.finalDecision) ? "promoted" : "repeated";
            ClassInfo target = schoolClass(d.targetClassId);
            newEnrollment = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO student_enrollment
                    (id,school_id,student_id,academic_session_id,school_class_id,class_name_snapshot,
                     level_snapshot,subsystem_snapshot,status,enrolled_on,planned_on,source,reason,previous_enrollment_id)
                VALUES (?,?,?,?,?,?,?,?, 'PLANNED', ?, ?, 'PROMOTION', ?, ?)
                """, newEnrollment, TenantContext.get(), d.studentId, batch.targetSessionId, target.id,
                    target.name, target.level, target.subsystem, targetSession.start, targetSession.start,
                    d.overrideReason == null ? "Promotion de fin d'année" : d.overrideReason, d.sourceEnrollmentId);
        }
        jdbc.update("UPDATE promotion_decision SET committed_enrollment_id=?, version=version+1 WHERE id=?", newEnrollment, d.id);
        if (newEnrollment != null) {
            jdbc.update("UPDATE student_enrollment SET promotion_decision_id=? WHERE id=?", d.id, newEnrollment);
            jdbc.update("""
                INSERT INTO promotion_transition_event
                    (school_id,student_id,source_enrollment_id,target_enrollment_id,promotion_batch_id,action,reason,actor_user_id)
                VALUES (?,?,?,?,?,'PLANNED',?,?)
                """, TenantContext.get(), d.studentId, d.sourceEnrollmentId, newEnrollment, batch.id,
                    d.overrideReason == null ? "Promotion planifiée" : d.overrideReason, currentUser());
            jdbc.update("""
                INSERT INTO journey_event
                    (school_id,student_id,event_type,academic_session_id,payload,visibility)
                VALUES (?,?,?,?,?::jsonb,'INTERNAL')
                """, TenantContext.get(), d.studentId, "PROMOTION_PLANNED", targetSession.id,
                    "{\"targetEnrollmentId\":\"" + newEnrollment + "\",\"decision\":\"" + d.finalDecision + "\"}");
        }
        String sourceClassName = schoolClass(d.sourceClassId).name;
        String targetName = d.targetClassId == null ? null : schoolClass(d.targetClassId).name;
        jdbc.update("""
            INSERT INTO journey_entry
                (school_id,student_id,academic_year,class_name,level,subsystem,result,general_average,
                 decision,note,recorded_by,source_session_id,target_session_id,promotion_batch_id,
                 recommendation,final_decision,target_class_name,override_reason,decision_by,decision_at)
            SELECT ?,?,?,?,sc.level,sc.subsystem,?,?,?, ?,?,?,?,?,?,?,?,?,?,now()
              FROM school_class sc WHERE sc.id=?
            ON CONFLICT (school_id,student_id,academic_year) DO NOTHING
            """, TenantContext.get(), d.studentId, sourceSession.code, sourceClassName, result, d.finalAverage,
                d.finalDecision + (targetName == null ? "" : " → " + targetName),
                d.overrideReason, currentUser(), sourceSession.id, targetSession.id, batch.id,
                d.recommendation, d.finalDecision, targetName, d.overrideReason, currentUser(), d.sourceClassId);
    }

    private RuleInfo resolveRule(UUID ruleSetId, String subsystem, String level) {
        RuleInfo found = jdbc.query("""
            SELECT * FROM promotion_rule WHERE school_id=? AND rule_set_id=? AND active
             AND (subsystem IS NULL OR subsystem=?) AND (level IS NULL OR level=?)
             ORDER BY (subsystem IS NOT NULL)::int + (level IS NOT NULL)::int DESC LIMIT 1
            """, rs -> rs.next() ? ruleInfo(rs) : null, TenantContext.get(), ruleSetId, subsystem, level);
        return found;
    }

    private ProgressionPathView pathById(UUID id) {
        ProgressionPathView value = jdbc.query("""
            SELECT p.*, sc.name source_name, tc.name target_name,
                   g.version_no graph_version_no,g.status graph_status
              FROM class_progression_path p
            JOIN school_class sc ON sc.id=p.source_class_id LEFT JOIN school_class tc ON tc.id=p.target_class_id
            JOIN progression_graph_version g ON g.id=p.graph_version_id
            WHERE p.id=? AND p.school_id=?
            """, rs -> rs.next() ? path(rs, 0) : null, id, TenantContext.get());
        if (value == null) throw ApiException.notFound("Parcours de progression");
        return value;
    }
    private List<ProgressionPathView> pathsForGraph(UUID graphId) {
        return jdbc.query("""
            SELECT p.*, sc.name source_name, tc.name target_name,
                   g.version_no graph_version_no,g.status graph_status
              FROM class_progression_path p
              JOIN progression_graph_version g ON g.id=p.graph_version_id
              JOIN school_class sc ON sc.id=p.source_class_id
              LEFT JOIN school_class tc ON tc.id=p.target_class_id
             WHERE p.school_id=? AND p.graph_version_id=? AND p.active
             ORDER BY p.display_order,p.edge_type,sc.name,tc.name
            """, this::path, TenantContext.get(), graphId);
    }
    private PromotionCandidateView candidateById(UUID id) {
        PromotionCandidateView value = jdbc.query("""
            SELECT d.*,d.evidence->>'explanation' explanation,
                   d.evidence->>'annualBulletinId' annual_bulletin_id,
                   d.evidence->>'annualDecision' annual_decision,
                   d.evidence->>'councilApproved' council_approved,
                   d.evidence->>'blockers' evidence_blockers,
                   st.matricule,st.first_name,st.last_name,sc.name source_name,
                   mc.name mapped_name,tc.name target_name FROM promotion_decision d
            JOIN student st ON st.id=d.student_id JOIN school_class sc ON sc.id=d.source_class_id
            LEFT JOIN school_class mc ON mc.id=d.mapped_target_class_id LEFT JOIN school_class tc ON tc.id=d.target_class_id
            WHERE d.id=? AND d.school_id=?
            """, rs -> rs.next() ? candidate(rs, 0) : null, id, TenantContext.get());
        if (value == null) throw ApiException.notFound("Décision de promotion"); return value;
    }
    private DecisionInfo decisionInfo(UUID id) {
        DecisionInfo value = jdbc.query("SELECT * FROM promotion_decision WHERE id=? AND school_id=?",
                rs -> rs.next() ? mapDecisionInfo(rs, 0) : null, id, TenantContext.get());
        if (value == null) throw ApiException.notFound("Décision de promotion"); return value;
    }
    private void ensureDraft(UUID batchId) { if (!"DRAFT".equals(batchInfo(batchId).status)) throw ApiException.conflict("Le lot est déjà validé et ne peut plus être modifié"); }
    private BatchInfo batchInfo(UUID id) {
        BatchInfo b = jdbc.query("""
            SELECT b.*,ss.label source_label,ts.label target_label,
                   gv.version_no graph_version_no,rs.version_no rule_set_version FROM promotion_batch b
            JOIN academic_session ss ON ss.id=b.source_session_id JOIN academic_session ts ON ts.id=b.target_session_id
            LEFT JOIN progression_graph_version gv ON gv.id=b.graph_version_id
            LEFT JOIN promotion_rule_set rs ON rs.id=b.rule_set_id
            WHERE b.id=? AND b.school_id=?
            """, rs -> rs.next() ? batchInfo(rs) : null, id, TenantContext.get());
        if (b == null) throw ApiException.notFound("Lot de promotion"); return b;
    }
    private SessionInfo session(UUID id) {
        SessionInfo s = jdbc.query("SELECT id,code,label,start_date,end_date,status FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? new SessionInfo(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("label"),rs.getObject("start_date",LocalDate.class),rs.getObject("end_date",LocalDate.class),rs.getString("status")) : null,
                id, TenantContext.get());
        if (s == null) throw ApiException.notFound("Session académique"); return s;
    }
    private void assertSession(UUID id) { session(id); }
    private ClassInfo schoolClass(UUID id) {
        ClassInfo c = jdbc.query("SELECT id,name,level,subsystem,progression_rank FROM school_class WHERE id=? AND school_id=?",
                rs -> rs.next() ? new ClassInfo(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("level"),rs.getString("subsystem"),rs.getObject("progression_rank",Integer.class)) : null,
                id, TenantContext.get());
        if (c == null) throw ApiException.notFound("Classe"); return c;
    }
    private String studentName(UUID id) { return jdbc.queryForObject("SELECT last_name||' '||first_name FROM student WHERE id=? AND school_id=?", String.class, id, TenantContext.get()); }
    private UUID currentUser() { var a=SecurityContextHolder.getContext().getAuthentication(); return a!=null&&a.getPrincipal() instanceof AppUserPrincipal p?p.userId():null; }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String jsonEscape(String value) { return value.replace("\\","\\\\").replace("\"","\\\""); }

    private ProgressionPathView path(ResultSet rs, int n) throws SQLException { return new ProgressionPathView(rs.getObject("id",UUID.class),rs.getObject("source_session_id",UUID.class),rs.getObject("source_class_id",UUID.class),rs.getString("source_name"),rs.getObject("target_session_id",UUID.class),rs.getObject("target_class_id",UUID.class),rs.getString("target_name"),rs.getBoolean("terminal"),rs.getBoolean("active"),rs.getLong("version"),rs.getObject("graph_version_id",UUID.class),rs.getInt("graph_version_no"),rs.getString("graph_status"),rs.getString("edge_type"),rs.getInt("display_order"),rs.getBoolean("allow_skip"),rs.getString("skip_reason")); }
    private PromotionRuleView rule(ResultSet rs, int n) throws SQLException { return new PromotionRuleView(rs.getObject("id",UUID.class),rs.getObject("academic_session_id",UUID.class),rs.getString("subsystem"),rs.getString("level"),rs.getBigDecimal("promote_min"),rs.getBigDecimal("review_min"),rs.getBoolean("require_final_average"),rs.getBoolean("active"),rs.getLong("version"),rs.getObject("rule_set_id",UUID.class),rs.getInt("rule_set_version"),rs.getString("rule_set_status")); }
    private EnrollmentInfo enrollment(ResultSet rs, int n) throws SQLException { return new EnrollmentInfo(rs.getObject("enrollment_id",UUID.class),rs.getObject("student_id",UUID.class),rs.getObject("school_class_id",UUID.class),rs.getString("class_name_snapshot"),rs.getString("level_snapshot"),rs.getString("subsystem_snapshot"),rs.getString("matricule"),rs.getString("first_name"),rs.getString("last_name")); }
    private PromotionCandidateView candidate(ResultSet rs, int n) throws SQLException { return new PromotionCandidateView(rs.getObject("id",UUID.class),rs.getObject("student_id",UUID.class),rs.getString("matricule"),rs.getString("last_name")+" "+rs.getString("first_name"),rs.getObject("source_enrollment_id",UUID.class),rs.getObject("source_class_id",UUID.class),rs.getString("source_name"),rs.getObject("mapped_target_class_id",UUID.class),rs.getString("mapped_name"),rs.getObject("target_class_id",UUID.class),rs.getString("target_name"),rs.getBigDecimal("final_average"),rs.getString("recommendation"),rs.getString("final_decision"),rs.getString("override_reason"),rs.getString("explanation"),rs.getLong("version"),safeUuid(rs.getString("annual_bulletin_id")),rs.getBigDecimal("final_average"),null,rs.getString("annual_decision"),Boolean.parseBoolean(rs.getString("council_approved")),List.of(),splitBlockers(rs.getString("evidence_blockers"))); }
    private UUID safeUuid(String value) { try { return value == null || value.isBlank() ? null : UUID.fromString(value); } catch (IllegalArgumentException ex) { return null; } }
    private List<String> splitBlockers(String value) { return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(",")).filter(v -> !v.isBlank()).toList(); }
    private DecisionInfo mapDecisionInfo(ResultSet rs, int n) throws SQLException { return new DecisionInfo(rs.getObject("id",UUID.class),rs.getObject("batch_id",UUID.class),rs.getObject("student_id",UUID.class),rs.getObject("source_enrollment_id",UUID.class),rs.getObject("source_class_id",UUID.class),rs.getObject("mapped_target_class_id",UUID.class),rs.getObject("target_class_id",UUID.class),rs.getBigDecimal("final_average"),rs.getString("recommendation"),rs.getString("final_decision"),rs.getString("override_reason"),rs.getLong("version")); }
    private PathInfo pathInfo(ResultSet rs) throws SQLException { return new PathInfo(rs.getObject("target_class_id",UUID.class),rs.getBoolean("terminal")); }
    private RuleInfo ruleInfo(ResultSet rs) throws SQLException { return new RuleInfo(rs.getBigDecimal("promote_min"),rs.getBigDecimal("review_min"),rs.getBoolean("require_final_average")); }
    private ProgressionGraphView graph(ResultSet rs, int n) throws SQLException { return new ProgressionGraphView(rs.getObject("id",UUID.class),rs.getObject("source_session_id",UUID.class),rs.getString("source_label"),rs.getObject("target_session_id",UUID.class),rs.getString("target_label"),rs.getInt("version_no"),rs.getString("status"),rs.getObject("copied_from_id",UUID.class),rs.getTimestamp("published_at")==null?null:rs.getTimestamp("published_at").toInstant(),rs.getLong("version"),rs.getInt("edge_count"),List.of()); }
    private GraphEdge graphEdge(ResultSet rs, int n) throws SQLException { return new GraphEdge(rs.getObject("source_class_id",UUID.class),rs.getString("source_name"),rs.getString("source_subsystem"),rs.getObject("source_rank",Integer.class),rs.getObject("target_class_id",UUID.class),rs.getString("target_name"),rs.getString("target_subsystem"),rs.getObject("target_rank",Integer.class),rs.getBoolean("terminal"),rs.getString("edge_type"),rs.getInt("display_order"),rs.getBoolean("allow_skip"),rs.getString("skip_reason")); }
    private PromotionRuleSetView ruleSet(ResultSet rs) throws SQLException { return new PromotionRuleSetView(rs.getObject("id",UUID.class),rs.getObject("academic_session_id",UUID.class),rs.getInt("version_no"),rs.getString("status"),rs.getString("conditions"),rs.getTimestamp("published_at")==null?null:rs.getTimestamp("published_at").toInstant(),rs.getLong("version"),List.of()); }
    private BatchInfo batchInfo(ResultSet rs) throws SQLException { return new BatchInfo(rs.getObject("id",UUID.class),rs.getString("name"),rs.getObject("source_session_id",UUID.class),rs.getString("source_label"),rs.getObject("target_session_id",UUID.class),rs.getString("target_label"),rs.getString("status"),rs.getLong("version"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("committed_at")==null?null:rs.getTimestamp("committed_at").toInstant(),rs.getObject("graph_version_id",UUID.class),rs.getObject("graph_version_no",Integer.class),rs.getObject("rule_set_id",UUID.class),rs.getObject("rule_set_version",Integer.class),rs.getString("preview_fingerprint")); }
    private record SessionInfo(UUID id,String code,String label,LocalDate start,LocalDate end,String status) {}
    private record ClassInfo(UUID id,String name,String level,String subsystem,Integer progressionRank) {}
    private record EnrollmentInfo(UUID enrollmentId,UUID studentId,UUID classId,String className,String level,String subsystem,String matricule,String firstName,String lastName) {}
    private record PathInfo(UUID targetClassId,boolean terminal) {}
    private record RuleInfo(BigDecimal promoteMin,BigDecimal reviewMin,boolean requireAverage) {}
    private record RuleSeed(String subsystem,String level,BigDecimal promoteMin,BigDecimal reviewMin,boolean requireAverage) {}
    private record Authority(UUID graphId,int graphVersionNo,long graphRowVersion,UUID ruleSetId,int ruleSetVersion,long ruleSetRowVersion) {}
    private record GraphEdge(UUID sourceClassId,String sourceName,String sourceSubsystem,Integer sourceRank,UUID targetClassId,String targetName,String targetSubsystem,Integer targetRank,boolean terminal,String edgeType,int displayOrder,boolean allowSkip,String skipReason) {}
    private record AnnualEvidence(UUID id, BigDecimal average, String decisionCode, boolean councilApproved) {}
    private record DecisionDraft(UUID mappedTargetClassId, UUID targetClassId, BigDecimal average,
                                 String recommendation, String finalDecision, String explanation, String evidence,
                                 UUID annualId, BigDecimal annualAverage, String annualDecision,
                                 boolean councilApproved, List<PromotionTargetOption> allowedTargets,
                                 List<String> blockers) {}
    private record BatchInfo(UUID id,String name,UUID sourceSessionId,String sourceLabel,UUID targetSessionId,String targetLabel,String status,long version,Instant createdAt,Instant committedAt,
                             UUID graphVersionId,Integer graphVersionNo,UUID ruleSetId,Integer ruleSetVersion,String previewFingerprint) {}
    private record DecisionInfo(UUID id,UUID batchId,UUID studentId,UUID sourceEnrollmentId,UUID sourceClassId,UUID mappedTargetClassId,UUID targetClassId,BigDecimal finalAverage,String recommendation,String finalDecision,String overrideReason,long version) {}
}
