package com.bbc.sms.journey;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
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
            SELECT p.*, sc.name source_name, tc.name target_name
              FROM class_progression_path p
              JOIN school_class sc ON sc.id=p.source_class_id
              LEFT JOIN school_class tc ON tc.id=p.target_class_id
             WHERE p.school_id=? AND p.source_session_id=? AND p.target_session_id=? AND p.active
             ORDER BY sc.level, sc.subsystem, sc.name
            """, this::path, TenantContext.get(), sourceSessionId, targetSessionId);
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
        UUID id = jdbc.query("""
            INSERT INTO class_progression_path
                (school_id, source_session_id, source_class_id, target_session_id, target_class_id, terminal)
            VALUES (?,?,?,?,?,?)
            ON CONFLICT (school_id, source_session_id, source_class_id, target_session_id)
            DO UPDATE SET target_class_id=excluded.target_class_id, terminal=excluded.terminal,
                          active=true, version=class_progression_path.version+1, updated_at=now()
            RETURNING id
            """, rs -> { rs.next(); return rs.getObject(1, UUID.class); }, school,
            in.sourceSessionId(), in.sourceClassId(), in.targetSessionId(), in.targetClassId(), in.terminal());
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
            SELECT * FROM promotion_rule WHERE school_id=? AND academic_session_id=? AND active
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
        UUID id = jdbc.query("""
            INSERT INTO promotion_rule
                (school_id, academic_session_id, subsystem, level, promote_min, review_min, require_final_average)
            VALUES (?,?,?,?,?,?,?)
            ON CONFLICT (school_id, academic_session_id, subsystem, level)
            DO UPDATE SET promote_min=excluded.promote_min, review_min=excluded.review_min,
                          require_final_average=excluded.require_final_average, active=true,
                          version=promotion_rule.version+1, updated_at=now()
            RETURNING id
            """, rs -> { rs.next(); return rs.getObject(1, UUID.class); }, school, in.academicSessionId(),
            subsystem, level, in.promoteMin(), in.reviewMin(), in.requireFinalAverage());
        PromotionRuleView saved = jdbc.queryForObject("SELECT * FROM promotion_rule WHERE id=? AND school_id=?", this::rule, id, school);
        audit.record("PROMOTION_RULE_SAVED", "PromotionRule", id.toString(), null, saved, null);
        return saved;
    }

    @Transactional
    public PromotionBatchView preview(PromotionPreviewRequest in) {
        UUID school = TenantContext.get();
        SessionInfo sourceSession = session(in.sourceSessionId());
        SessionInfo targetSession = session(in.targetSessionId());
        if (!targetSession.start.isAfter(sourceSession.end)) {
            throw ApiException.badRequest("La session cible doit suivre la session source");
        }
        if (in.idempotencyKey() != null && !in.idempotencyKey().isBlank()) {
            List<UUID> existing = jdbc.query("SELECT id FROM promotion_batch WHERE school_id=? AND idempotency_key=?",
                    (rs, n) -> rs.getObject(1, UUID.class), school, in.idempotencyKey().trim());
            if (!existing.isEmpty()) return batch(existing.getFirst());
        }
        UUID batchId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO promotion_batch(id, school_id, source_session_id, target_session_id, name, idempotency_key, created_by)
            VALUES (?,?,?,?,?,?,?)
            """, batchId, school, in.sourceSessionId(), in.targetSessionId(), in.name().trim(), clean(in.idempotencyKey()), currentUser());

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

        for (EnrollmentInfo e : roster) createDecision(batchId, sourceSession, in.targetSessionId(), e);
        PromotionBatchView result = batch(batchId);
        audit.record("PROMOTION_BATCH_PREVIEWED", "PromotionBatch", batchId.toString(), null, result, null);
        return result;
    }

    @Transactional(readOnly = true)
    public PromotionBatchView batch(UUID id) {
        UUID school = TenantContext.get();
        BatchInfo b = jdbc.query("""
            SELECT b.*, ss.label source_label, ts.label target_label
              FROM promotion_batch b JOIN academic_session ss ON ss.id=b.source_session_id
              JOIN academic_session ts ON ts.id=b.target_session_id
             WHERE b.id=? AND b.school_id=?
            """, rs -> rs.next() ? batchInfo(rs) : null, id, school);
        if (b == null) throw ApiException.notFound("Lot de promotion");
        List<PromotionCandidateView> candidates = jdbc.query("""
            SELECT d.*, d.evidence->>'explanation' explanation, st.matricule, st.first_name, st.last_name,
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
                graduate, review, b.version, b.createdAt, b.committedAt, candidates);
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
        if (target != null) schoolClass(target);
        int changed = jdbc.update("""
            UPDATE promotion_decision SET final_decision=?, target_class_id=?, override_reason=?,
                   decided_by=?, decided_at=now(), version=version+1
             WHERE id=? AND school_id=? AND version=?
            """, decision, target, in.reason().trim(), currentUser(), id, TenantContext.get(), current.version);
        if (changed == 0) throw ApiException.conflict("Cette décision a été modifiée par un autre utilisateur");
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
        List<DecisionInfo> decisions = jdbc.query("SELECT * FROM promotion_decision WHERE school_id=? AND batch_id=? ORDER BY student_id",
                this::mapDecisionInfo, TenantContext.get(), id);
        List<String> blockers = new ArrayList<>();
        for (DecisionInfo d : decisions) {
            if ("REVIEW".equals(d.finalDecision)) blockers.add(studentName(d.studentId) + " : décision à réviser");
            if (!"GRADUATE".equals(d.finalDecision) && d.targetClassId == null) blockers.add(studentName(d.studentId) + " : classe cible manquante");
            Integer existing = jdbc.queryForObject("SELECT count(*) FROM student_enrollment WHERE school_id=? AND student_id=? AND academic_session_id=? AND status='ACTIVE'",
                    Integer.class, TenantContext.get(), d.studentId, batch.targetSessionId);
            if (existing != null && existing > 0) blockers.add(studentName(d.studentId) + " : déjà inscrit dans la session cible");
        }
        if (!blockers.isEmpty()) throw ApiException.conflict("Validation impossible — " + String.join(" ; ", blockers));

        SessionInfo sourceSession = session(batch.sourceSessionId);
        SessionInfo targetSession = session(batch.targetSessionId);
        for (DecisionInfo d : decisions) commitDecision(d, batch, sourceSession, targetSession);
        int changed = jdbc.update("""
            UPDATE promotion_batch SET status='COMMITTED', committed_by=?, committed_at=now(),
                   commit_reason=?, version=version+1 WHERE id=? AND school_id=? AND version=?
            """, currentUser(), in.reason().trim(), id, TenantContext.get(), batch.version);
        if (changed == 0) throw ApiException.conflict("Le lot a été modifié pendant la validation");
        PromotionBatchView result = batch(id);
        audit.record("PROMOTION_BATCH_COMMITTED", "PromotionBatch", id.toString(), batch, result, in.reason());
        return result;
    }

    private void createDecision(UUID batchId, SessionInfo sourceSession, UUID targetSessionId, EnrollmentInfo e) {
        PathInfo path = jdbc.query("""
            SELECT * FROM class_progression_path WHERE school_id=? AND source_session_id=?
             AND target_session_id=? AND source_class_id=? AND active
            """, rs -> rs.next() ? pathInfo(rs) : null, TenantContext.get(), sourceSession.id, targetSessionId, e.classId);
        RuleInfo rule = resolveRule(sourceSession.id, e.subsystem, e.level);
        AnnualEvidence annual = jdbc.query("""
            SELECT v.id, v.average, v.snapshot_json->'conduct'->>'decisionCode' AS decision_code
              FROM bulletin_version v
              JOIN academic_reporting_period p ON p.id=v.reporting_period_id
             WHERE v.school_id=? AND v.student_id=? AND v.academic_session_id=?
               AND p.period_type='ANNUAL_RESULT' AND v.state='PUBLISHED'
             ORDER BY v.published_at DESC NULLS LAST, v.created_at DESC LIMIT 1
            """, rs -> rs.next() ? new AnnualEvidence(rs.getObject("id", UUID.class), rs.getBigDecimal("average"), rs.getString("decision_code")) : null,
                TenantContext.get(), e.studentId, sourceSession.id);
        BigDecimal average = annual == null ? null : annual.average;
        String averageSource = annual == null ? "MISSING" : "PUBLISHED_ANNUAL_BULLETIN";
        if (annual == null) {
            average = jdbc.query("""
                SELECT general_average FROM journey_entry WHERE school_id=? AND student_id=? AND academic_year=?
                """, rs -> rs.next() ? rs.getBigDecimal(1) : null, TenantContext.get(), e.studentId, sourceSession.code);
            averageSource = average == null ? "MISSING" : "JOURNEY_LEGACY_FALLBACK";
            if (average == null) {
                average = jdbc.query("SELECT round(avg(mark),2) FROM grade WHERE school_id=? AND student_id=?",
                        rs -> rs.next() ? rs.getBigDecimal(1) : null, TenantContext.get(), e.studentId);
                averageSource = average == null ? "MISSING" : "LEGACY_GRADES_FALLBACK";
            }
        }
        String recommendation;
        UUID mapped = path == null ? null : path.targetClassId;
        String explanation;
        if (path == null) {
            recommendation = "REVIEW"; explanation = "Aucun parcours de progression configuré pour cette classe.";
        } else if (path.terminal) {
            recommendation = "GRADUATE"; explanation = "Classe terminale : sortie/diplôme recommandé.";
        } else if (annual != null && annual.decisionCode != null && DECISIONS.contains(annual.decisionCode.toUpperCase(Locale.ROOT))) {
            recommendation = annual.decisionCode.toUpperCase(Locale.ROOT);
            explanation = "Décision annuelle publiée : " + recommendation + " (bulletin annuel " + annual.id + ").";
        } else if (average == null && rule.requireAverage) {
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
        String evidence = "{\"averageSource\":\"" + averageSource + "\"" + annualId + annualDecision + ",\"promoteMin\":" + rule.promoteMin
                + ",\"reviewMin\":" + rule.reviewMin + ",\"explanation\":\"" + jsonEscape(explanation) + "\"}";
        jdbc.update("""
            INSERT INTO promotion_decision
                (school_id,batch_id,student_id,source_enrollment_id,source_class_id,mapped_target_class_id,
                 target_class_id,final_average,recommendation,final_decision,evidence)
            VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb)
            """, TenantContext.get(), batchId, e.studentId, e.enrollmentId, e.classId, mapped,
                target, average, recommendation, recommendation, evidence);
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
                     level_snapshot,subsystem_snapshot,status,enrolled_on,source,reason,previous_enrollment_id)
                VALUES (?,?,?,?,?,?,?,?, 'ACTIVE', ?, 'PROMOTION', ?, ?)
                """, newEnrollment, TenantContext.get(), d.studentId, batch.targetSessionId, target.id,
                    target.name, target.level, target.subsystem, targetSession.start,
                    d.overrideReason == null ? "Promotion de fin d'année" : d.overrideReason, d.sourceEnrollmentId);
        }
        jdbc.update("""
            UPDATE student_enrollment SET status='COMPLETED', exited_on=?, reason=?, version=version+1
             WHERE id=? AND school_id=? AND status='ACTIVE'
            """, sourceSession.end, "Décision de fin d'année : " + d.finalDecision, d.sourceEnrollmentId, TenantContext.get());
        jdbc.update("UPDATE promotion_decision SET committed_enrollment_id=?, version=version+1 WHERE id=?", newEnrollment, d.id);
        String sourceClassName = schoolClass(d.sourceClassId).name;
        String targetName = d.targetClassId == null ? null : schoolClass(d.targetClassId).name;
        jdbc.update("""
            INSERT INTO journey_entry
                (school_id,student_id,academic_year,class_name,level,subsystem,result,general_average,
                 decision,note,recorded_by,source_session_id,target_session_id,promotion_batch_id,
                 recommendation,final_decision,target_class_name,override_reason,decision_by,decision_at)
            SELECT ?,?,?,?,sc.level,sc.subsystem,?,?,?, ?,?,?,?,?,?,?,?,?,?,now()
              FROM school_class sc WHERE sc.id=?
            ON CONFLICT (school_id,student_id,academic_year) DO UPDATE SET
                result=excluded.result, general_average=COALESCE(excluded.general_average,journey_entry.general_average),
                decision=excluded.decision, note=excluded.note, source_session_id=excluded.source_session_id,
                target_session_id=excluded.target_session_id, promotion_batch_id=excluded.promotion_batch_id,
                recommendation=excluded.recommendation, final_decision=excluded.final_decision,
                target_class_name=excluded.target_class_name, override_reason=excluded.override_reason,
                decision_by=excluded.decision_by, decision_at=excluded.decision_at
            """, TenantContext.get(), d.studentId, sourceSession.code, sourceClassName, result, d.finalAverage,
                d.finalDecision + (targetName == null ? "" : " → " + targetName),
                d.overrideReason, currentUser(), sourceSession.id, targetSession.id, batch.id,
                d.recommendation, d.finalDecision, targetName, d.overrideReason, currentUser(), d.sourceClassId);
        Boolean targetCurrent = jdbc.queryForObject("SELECT is_current FROM academic_session WHERE id=?", Boolean.class, targetSession.id);
        if (Boolean.TRUE.equals(targetCurrent) && d.targetClassId != null) {
            ClassInfo target = schoolClass(d.targetClassId);
            jdbc.update("UPDATE student SET class_id=?,class_name=?,level=?,subsystem=?,repeats=? WHERE id=? AND school_id=?",
                    target.id, target.name, target.level, target.subsystem,
                    !"PROMOTE".equals(d.finalDecision), d.studentId, TenantContext.get());
        }
    }

    private RuleInfo resolveRule(UUID sessionId, String subsystem, String level) {
        RuleInfo found = jdbc.query("""
            SELECT * FROM promotion_rule WHERE school_id=? AND academic_session_id=? AND active
             AND (subsystem IS NULL OR subsystem=?) AND (level IS NULL OR level=?)
             ORDER BY (subsystem IS NOT NULL)::int + (level IS NOT NULL)::int DESC LIMIT 1
            """, rs -> rs.next() ? ruleInfo(rs) : null, TenantContext.get(), sessionId, subsystem, level);
        return found == null ? new RuleInfo(BigDecimal.TEN, BigDecimal.valueOf(8), true) : found;
    }

    private ProgressionPathView pathById(UUID id) {
        ProgressionPathView value = jdbc.query("""
            SELECT p.*, sc.name source_name, tc.name target_name FROM class_progression_path p
            JOIN school_class sc ON sc.id=p.source_class_id LEFT JOIN school_class tc ON tc.id=p.target_class_id
            WHERE p.id=? AND p.school_id=?
            """, rs -> rs.next() ? path(rs, 0) : null, id, TenantContext.get());
        if (value == null) throw ApiException.notFound("Parcours de progression");
        return value;
    }
    private PromotionCandidateView candidateById(UUID id) {
        PromotionCandidateView value = jdbc.query("""
            SELECT d.*,d.evidence->>'explanation' explanation,st.matricule,st.first_name,st.last_name,sc.name source_name,
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
            SELECT b.*,ss.label source_label,ts.label target_label FROM promotion_batch b
            JOIN academic_session ss ON ss.id=b.source_session_id JOIN academic_session ts ON ts.id=b.target_session_id
            WHERE b.id=? AND b.school_id=?
            """, rs -> rs.next() ? batchInfo(rs) : null, id, TenantContext.get());
        if (b == null) throw ApiException.notFound("Lot de promotion"); return b;
    }
    private SessionInfo session(UUID id) {
        SessionInfo s = jdbc.query("SELECT id,code,label,start_date,end_date FROM academic_session WHERE id=? AND school_id=?",
                rs -> rs.next() ? new SessionInfo(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("label"),rs.getObject("start_date",LocalDate.class),rs.getObject("end_date",LocalDate.class)) : null,
                id, TenantContext.get());
        if (s == null) throw ApiException.notFound("Session académique"); return s;
    }
    private void assertSession(UUID id) { session(id); }
    private ClassInfo schoolClass(UUID id) {
        ClassInfo c = jdbc.query("SELECT id,name,level,subsystem FROM school_class WHERE id=? AND school_id=?",
                rs -> rs.next() ? new ClassInfo(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("level"),rs.getString("subsystem")) : null,
                id, TenantContext.get());
        if (c == null) throw ApiException.notFound("Classe"); return c;
    }
    private String studentName(UUID id) { return jdbc.queryForObject("SELECT last_name||' '||first_name FROM student WHERE id=? AND school_id=?", String.class, id, TenantContext.get()); }
    private UUID currentUser() { var a=SecurityContextHolder.getContext().getAuthentication(); return a!=null&&a.getPrincipal() instanceof AppUserPrincipal p?p.userId():null; }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String jsonEscape(String value) { return value.replace("\\","\\\\").replace("\"","\\\""); }

    private ProgressionPathView path(ResultSet rs, int n) throws SQLException { return new ProgressionPathView(rs.getObject("id",UUID.class),rs.getObject("source_session_id",UUID.class),rs.getObject("source_class_id",UUID.class),rs.getString("source_name"),rs.getObject("target_session_id",UUID.class),rs.getObject("target_class_id",UUID.class),rs.getString("target_name"),rs.getBoolean("terminal"),rs.getBoolean("active"),rs.getLong("version")); }
    private PromotionRuleView rule(ResultSet rs, int n) throws SQLException { return new PromotionRuleView(rs.getObject("id",UUID.class),rs.getObject("academic_session_id",UUID.class),rs.getString("subsystem"),rs.getString("level"),rs.getBigDecimal("promote_min"),rs.getBigDecimal("review_min"),rs.getBoolean("require_final_average"),rs.getBoolean("active"),rs.getLong("version")); }
    private EnrollmentInfo enrollment(ResultSet rs, int n) throws SQLException { return new EnrollmentInfo(rs.getObject("enrollment_id",UUID.class),rs.getObject("student_id",UUID.class),rs.getObject("school_class_id",UUID.class),rs.getString("class_name_snapshot"),rs.getString("level_snapshot"),rs.getString("subsystem_snapshot"),rs.getString("matricule"),rs.getString("first_name"),rs.getString("last_name")); }
    private PromotionCandidateView candidate(ResultSet rs, int n) throws SQLException { return new PromotionCandidateView(rs.getObject("id",UUID.class),rs.getObject("student_id",UUID.class),rs.getString("matricule"),rs.getString("last_name")+" "+rs.getString("first_name"),rs.getObject("source_enrollment_id",UUID.class),rs.getObject("source_class_id",UUID.class),rs.getString("source_name"),rs.getObject("mapped_target_class_id",UUID.class),rs.getString("mapped_name"),rs.getObject("target_class_id",UUID.class),rs.getString("target_name"),rs.getBigDecimal("final_average"),rs.getString("recommendation"),rs.getString("final_decision"),rs.getString("override_reason"),rs.getString("explanation"),rs.getLong("version")); }
    private DecisionInfo mapDecisionInfo(ResultSet rs, int n) throws SQLException { return new DecisionInfo(rs.getObject("id",UUID.class),rs.getObject("batch_id",UUID.class),rs.getObject("student_id",UUID.class),rs.getObject("source_enrollment_id",UUID.class),rs.getObject("source_class_id",UUID.class),rs.getObject("mapped_target_class_id",UUID.class),rs.getObject("target_class_id",UUID.class),rs.getBigDecimal("final_average"),rs.getString("recommendation"),rs.getString("final_decision"),rs.getString("override_reason"),rs.getLong("version")); }
    private PathInfo pathInfo(ResultSet rs) throws SQLException { return new PathInfo(rs.getObject("target_class_id",UUID.class),rs.getBoolean("terminal")); }
    private RuleInfo ruleInfo(ResultSet rs) throws SQLException { return new RuleInfo(rs.getBigDecimal("promote_min"),rs.getBigDecimal("review_min"),rs.getBoolean("require_final_average")); }
    private BatchInfo batchInfo(ResultSet rs) throws SQLException { return new BatchInfo(rs.getObject("id",UUID.class),rs.getString("name"),rs.getObject("source_session_id",UUID.class),rs.getString("source_label"),rs.getObject("target_session_id",UUID.class),rs.getString("target_label"),rs.getString("status"),rs.getLong("version"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("committed_at")==null?null:rs.getTimestamp("committed_at").toInstant()); }
    private record SessionInfo(UUID id,String code,String label,LocalDate start,LocalDate end) {}
    private record ClassInfo(UUID id,String name,String level,String subsystem) {}
    private record EnrollmentInfo(UUID enrollmentId,UUID studentId,UUID classId,String className,String level,String subsystem,String matricule,String firstName,String lastName) {}
    private record PathInfo(UUID targetClassId,boolean terminal) {}
    private record RuleInfo(BigDecimal promoteMin,BigDecimal reviewMin,boolean requireAverage) {}
    private record AnnualEvidence(UUID id, BigDecimal average, String decisionCode) {}
    private record BatchInfo(UUID id,String name,UUID sourceSessionId,String sourceLabel,UUID targetSessionId,String targetLabel,String status,long version,Instant createdAt,Instant committedAt) {}
    private record DecisionInfo(UUID id,UUID batchId,UUID studentId,UUID sourceEnrollmentId,UUID sourceClassId,UUID mappedTargetClassId,UUID targetClassId,BigDecimal finalAverage,String recommendation,String finalDecision,String overrideReason,long version) {}
}
