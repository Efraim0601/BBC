package com.bbc.sms.alerts;

import com.bbc.sms.alerts.dto.AlertDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alert engine. {@link #scan()} recomputes proactive at-risk signals from the
 * operational tables (attendance, discipline, fees, grades) and upserts them
 * into {@code alert} by {@code (school_id, dedup_key)} with ON CONFLICT DO
 * NOTHING — so acknowledged / resolved alerts are never resurrected.
 */
@Service
public class AlertService {

    private final AlertRepository repo;
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicyService policy;

    public AlertService(AlertRepository repo, JdbcTemplate jdbc, AuthorizationPolicyService policy) {
        this.repo = repo;
        this.jdbc = jdbc;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public List<AlertView> list() {
        requireSchool("ALERTS_VIEW");
        UUID schoolId = TenantContext.get();
        return jdbc.query("""
                SELECT a.id, a.student_id, a.type, a.severity, a.title, a.detail,
                       a.status, a.created_at,
                       UPPER(s.last_name) || ' ' || s.first_name AS student_name,
                       s.class_name
                FROM alert a
                JOIN student s ON s.id = a.student_id
                WHERE a.school_id = ? AND a.status IN ('open','ack')
                  -- Un admin de section ne suit que les élèves de son cycle ; la
                  -- clause s'efface pour les comptes non cloisonnés.
                  AND (CAST(? AS VARCHAR) IS NULL OR s.level = CAST(? AS VARCHAR))
                ORDER BY a.created_at DESC
                """,
                (rs, i) -> new AlertView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("student_id", UUID.class),
                        rs.getString("student_name"),
                        rs.getString("class_name"),
                        rs.getString("type"),
                        rs.getString("severity"),
                        rs.getString("title"),
                        rs.getString("detail"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()),
                schoolId, ParcoursContext.sectionLock(), ParcoursContext.sectionLock());
    }

    @Transactional
    public ScanResult scan() {
        requireSchool("ALERTS_MANAGE");
        UUID schoolId = TenantContext.get();
        int created = 0;
        created += scanAbsences(schoolId);
        created += scanDiscipline(schoolId);
        created += scanUnpaid(schoolId);
        created += scanGradeDrop(schoolId);
        return new ScanResult(created);
    }

    @Transactional
    public void ack(UUID id) {
        requireSchool("ALERTS_MANAGE");
        UUID schoolId = TenantContext.get();
        Alert a = repo.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Alerte"));
        a.setStatus("ack");
        a.setAckBy(currentUserId());
        a.setAckAt(Instant.now());
        repo.save(a);
    }

    @Transactional
    public void resolve(UUID id) {
        requireSchool("ALERTS_MANAGE");
        UUID schoolId = TenantContext.get();
        Alert a = repo.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> ApiException.notFound("Alerte"));
        a.setStatus("resolved");
        repo.save(a);
    }

    // ---- scan rules ---------------------------------------------------------

    private int scanAbsences(UUID schoolId) {
        int created = 0;
        var rows = jdbc.queryForList("""
                SELECT s.id AS student_id, COUNT(ar.id) AS n
                FROM student s
                JOIN attendance_record ar ON ar.student_id = s.id AND ar.school_id = s.school_id
                WHERE s.school_id = ? AND s.active = true
                  AND ar.status IN ('absent','late')
                  AND ar.att_date >= now() - interval '30 days'
                GROUP BY s.id
                HAVING COUNT(ar.id) >= 4
                """, schoolId);
        for (Map<String, Object> r : rows) {
            UUID studentId = (UUID) r.get("student_id");
            long n = ((Number) r.get("n")).longValue();
            String severity = n >= 8 ? "critical" : "warn";
            String detail = n + " absences/retards sur 30 jours";
            created += upsert(schoolId, studentId, "absences", severity,
                    "Absences répétées", detail, "absences:" + studentId);
        }
        return created;
    }

    private int scanDiscipline(UUID schoolId) {
        int created = 0;
        var rows = jdbc.queryForList("""
                SELECT s.id AS student_id, COUNT(di.id) AS n
                FROM student s
                JOIN discipline_incident di ON di.student_id = s.id AND di.school_id = s.school_id
                WHERE s.school_id = ? AND s.active = true
                  AND di.incident_date >= (now() - interval '60 days')::date
                GROUP BY s.id
                HAVING COUNT(di.id) >= 2
                """, schoolId);
        for (Map<String, Object> r : rows) {
            UUID studentId = (UUID) r.get("student_id");
            long n = ((Number) r.get("n")).longValue();
            String severity = n >= 4 ? "critical" : "warn";
            String detail = n + " incidents disciplinaires sur 60 jours";
            created += upsert(schoolId, studentId, "discipline", severity,
                    "Incidents disciplinaires", detail, "discipline:" + studentId);
        }
        return created;
    }

    private int scanUnpaid(UUID schoolId) {
        int created = 0;
        var rows = jdbc.queryForList("""
                SELECT s.id AS student_id, sf.balance AS balance, sf.status AS status
                FROM student s
                JOIN student_fee sf ON sf.student_id = s.id AND sf.school_id = s.school_id
                WHERE s.school_id = ? AND s.active = true
                  AND (sf.status = 'unpaid' OR sf.balance >= 150000)
                """, schoolId);
        for (Map<String, Object> r : rows) {
            UUID studentId = (UUID) r.get("student_id");
            Number balance = (Number) r.get("balance");
            String status = (String) r.get("status");
            boolean critical = "unpaid".equals(status)
                    && balance != null && balance.doubleValue() >= 150000;
            String severity = critical ? "critical" : "warn";
            String bal = balance == null ? "—"
                    : String.format("%,d", Math.round(balance.doubleValue())).replace(',', ' ');
            String detail = "Solde dû: " + bal + " FCFA (" + status + ")";
            created += upsert(schoolId, studentId, "unpaid", severity,
                    "Frais de scolarité impayés", detail, "unpaid:" + studentId);
        }
        return created;
    }

    private int scanGradeDrop(UUID schoolId) {
        int created = 0;
        // Average mark per (student, sequence). Only active students.
        var rows = jdbc.queryForList("""
                SELECT g.student_id AS student_id, g.sequence AS sequence, AVG(g.mark) AS avg_mark
                FROM grade g
                JOIN student s ON s.id = g.student_id AND s.school_id = g.school_id
                WHERE g.school_id = ? AND s.active = true
                GROUP BY g.student_id, g.sequence
                ORDER BY g.student_id, g.sequence
                """, schoolId);

        // Group sequences per student preserving the ascending order above.
        java.util.LinkedHashMap<UUID, List<double[]>> byStudent = new java.util.LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            UUID studentId = (UUID) r.get("student_id");
            double seq = ((Number) r.get("sequence")).doubleValue();
            double avg = ((Number) r.get("avg_mark")).doubleValue();
            byStudent.computeIfAbsent(studentId, k -> new ArrayList<>()).add(new double[]{seq, avg});
        }

        for (var entry : byStudent.entrySet()) {
            List<double[]> seqs = entry.getValue();
            if (seqs.size() < 2) continue;
            // Two most recent sequences with data (already ascending by sequence).
            double[] prev = seqs.get(seqs.size() - 2);
            double[] latest = seqs.get(seqs.size() - 1);
            double drop = prev[1] - latest[1];
            if (drop < 3) continue;
            String severity = drop >= 5 ? "critical" : "warn";
            String detail = String.format(
                    "Baisse de %.1f points entre la séquence %d (%.2f) et la séquence %d (%.2f)",
                    drop, (int) prev[0], prev[1], (int) latest[0], latest[1]);
            created += upsert(schoolId, entry.getKey(), "grade_drop", severity,
                    "Chute des résultats", detail, "grade_drop:" + entry.getKey());
        }
        return created;
    }

    /**
     * Insert a fresh alert; ON CONFLICT (school_id, dedup_key) DO NOTHING keeps
     * any existing (possibly acknowledged) row untouched. Returns 1 if a new
     * row was actually inserted, 0 otherwise.
     */
    private int upsert(UUID schoolId, UUID studentId, String type, String severity,
                       String title, String detail, String dedupKey) {
        return jdbc.update("""
                INSERT INTO alert (school_id, student_id, type, severity, title, detail, dedup_key, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'open')
                ON CONFLICT (school_id, dedup_key) DO NOTHING
                """, schoolId, studentId, type, severity, title, detail, dedupKey);
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private void requireSchool(String action) {
        policy.require(action, new PolicyResourceContext(TenantContext.get(), null, LocalDate.now(),
                null, null, null, null, null, null, null, null, null));
    }
}
