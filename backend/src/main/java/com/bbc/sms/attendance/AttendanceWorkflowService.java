package com.bbc.sms.attendance;

import com.bbc.sms.attendance.dto.AttendanceDtos.*;
import com.bbc.sms.foundation.cohort.AcademicCohortResolver;
import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import com.bbc.sms.timetable.TimetableSlot;
import com.bbc.sms.timetable.TimetableSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceWorkflowService {
    private static final Set<String> STATUSES = Set.of("UNMARKED", "PRESENT", "ABSENT", "LATE", "EXCUSED");

    private final JdbcTemplate jdbc;
    private final SchoolClassRepository classes;
    private final TimetableSlotRepository slots;
    private final AcademicSessionRepository sessions;
    private final TeacherScopeService teacherScope;
    private final AuthorizationPolicyService policy;
    private final AcademicCohortResolver cohorts;

    @Autowired
    public AttendanceWorkflowService(JdbcTemplate jdbc, SchoolClassRepository classes,
                                     TimetableSlotRepository slots, AcademicSessionRepository sessions,
                                     TeacherScopeService teacherScope,
                                     AuthorizationPolicyService policy,
                                     AcademicCohortResolver cohorts) {
        this.jdbc = jdbc;
        this.classes = classes;
        this.slots = slots;
        this.sessions = sessions;
        this.teacherScope = teacherScope;
        this.policy = policy;
        this.cohorts = cohorts;
    }

    /** Focused attendance unit tests can exercise policy behavior without SQL cohort tables. */
    public AttendanceWorkflowService(JdbcTemplate jdbc, SchoolClassRepository classes,
                                     TimetableSlotRepository slots, AcademicSessionRepository sessions,
                                     TeacherScopeService teacherScope,
                                     AuthorizationPolicyService policy) {
        this(jdbc, classes, slots, sessions, teacherScope, policy, null);
    }

    @Transactional(readOnly = true)
    public List<PolicyView> policies() {
        policy.require("ATTENDANCE_POLICY_VIEW",
                new PolicyResourceContext(TenantContext.get(), null, LocalDate.now(), ParcoursContext.get(),
                        null, null, null, null, null, null, null, null));
        return policyRows();
    }

    private List<PolicyView> policyRows() {
        return jdbc.query("""
            SELECT id, level, model, late_after_minutes, chronic_absence_percent, require_absence_reason
              FROM attendance_policy WHERE school_id=? ORDER BY level
            """, (rs, n) -> new PolicyView(rs.getObject("id", UUID.class), rs.getString("level"),
                rs.getString("model"), rs.getInt("late_after_minutes"),
                rs.getBigDecimal("chronic_absence_percent"), rs.getBoolean("require_absence_reason")),
            TenantContext.get());
    }

    @Transactional
    public PolicyView updatePolicy(String level, PolicyRequest request) {
        policy.require("ATTENDANCE_POLICY_MANAGE",
                new PolicyResourceContext(TenantContext.get(), null, LocalDate.now(), ParcoursContext.get(),
                        null, null, null, null, null, null, null, null));
        String normalizedLevel = normalizeLevel(level);
        String model = request.model() == null ? "" : request.model().toUpperCase(Locale.ROOT);
        if (!Set.of("DAILY", "PERIOD").contains(model)) throw ApiException.badRequest("Modèle d'appel invalide");
        if ((normalizedLevel.equals("maternelle") || normalizedLevel.equals("primary")) && !model.equals("DAILY"))
            throw ApiException.badRequest("La maternelle et le primaire utilisent un appel quotidien");
        if (normalizedLevel.equals("secondary") && !model.equals("PERIOD"))
            throw ApiException.badRequest("Le secondaire utilise un appel par matière et période");
        BigDecimal threshold = request.chronicAbsencePercent() == null ? new BigDecimal("20") : request.chronicAbsencePercent();
        if (threshold.signum() < 0 || threshold.compareTo(new BigDecimal("100")) > 0)
            throw ApiException.badRequest("Le seuil d'absence doit être compris entre 0 et 100");
        UUID id = jdbc.queryForObject("""
            INSERT INTO attendance_policy(school_id, level, model, late_after_minutes,
                chronic_absence_percent, require_absence_reason, updated_at)
            VALUES (?,?,?,?,?,?,now())
            ON CONFLICT (school_id, level) DO UPDATE SET model=excluded.model,
                late_after_minutes=excluded.late_after_minutes,
                chronic_absence_percent=excluded.chronic_absence_percent,
                require_absence_reason=excluded.require_absence_reason, updated_at=now()
            RETURNING id
            """, UUID.class, TenantContext.get(), normalizedLevel, model,
            Math.max(0, request.lateAfterMinutes()), threshold, request.requireAbsenceReason());
        audit(null, "POLICY_UPDATED", normalizedLevel, null);
        return policyRows().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<AttendanceClass> attendanceClasses() {
        Map<String, String> policy = policyRows().stream().collect(
            java.util.stream.Collectors.toMap(PolicyView::level, PolicyView::model));
        AcademicSession currentSession = sessions.findBySchoolIdOrderByStartDateDesc(TenantContext.get()).stream()
            .filter(AcademicSession::isCurrent).findFirst().orElse(null);
        UUID currentSessionId = currentSession == null ? null : currentSession.getId();
        LocalDate scopeDate = currentSession == null ? LocalDate.now()
                : LocalDate.now().isBefore(currentSession.getStartDate()) ? currentSession.getStartDate()
                : LocalDate.now().isAfter(currentSession.getEndDate()) ? currentSession.getEndDate() : LocalDate.now();
        Set<UUID> allowed = currentSessionId == null
                ? teacherScope.allowedClassIds()
                : teacherScope.allowedClassIds(currentSessionId, scopeDate);
        Map<UUID, Integer> enrollmentCounts = currentSessionId == null ? Map.of() : jdbc.query("""
            SELECT school_class_id, count(*) FROM student_enrollment
             WHERE school_id=? AND academic_session_id=? AND status='ACTIVE'
             GROUP BY school_class_id
            """, rs -> { Map<UUID, Integer> counts = new HashMap<>(); while (rs.next())
                counts.put(rs.getObject(1, UUID.class), rs.getInt(2)); return counts; }, TenantContext.get(), currentSessionId);
        return classes.findBySchoolIdOrderByName(TenantContext.get()).stream()
            .filter(c -> currentSessionId == null || attendanceRepresentative(c, currentSessionId))
            .filter(c -> allowed == null || allowed.contains(c.getId()) || sharedAllowed(c, currentSessionId, allowed))
            .filter(c -> attendanceClassAllowed(c, currentSessionId, scopeDate))
            .map(c -> new AttendanceClass(c.getId(), currentSessionId == null ? c.getName()
                    : cohorts == null ? c.getName() : cohorts.displayName(currentSessionId, c.getId(), c.getName()), c.getLevel(), c.getSubsystem(),
                policy.getOrDefault(normalizeLevel(c.getLevel()), defaultModel(c.getLevel())),
                currentSessionId == null || cohorts == null ? enrollmentCounts.getOrDefault(c.getId(), 0)
                    : cohorts.rosterCount(currentSessionId, c.getId(), "ACTIVE")))
            .toList();
    }

    @Transactional
    public List<SessionSummary> sessionOptions(UUID classId, LocalDate date) {
        AcademicSession academic = requireAcademicSession(date);
        classId = canonicalAttendanceClass(academic.getId(), classId);
        SchoolClass schoolClass = requireClass(classId);
        teacherScope.assertClass(academic.getId(), classId, date);
        String model = modelFor(schoolClass.getLevel());
        List<SessionKey> keys = keysFor(schoolClass, date, model).stream()
                .filter(key -> policy.decide("ATTENDANCE_ROSTER_VIEW",
                        attendanceContext(academic.getId(), schoolClass, date, key)).allowed())
                .toList();
        for (SessionKey key : keys) ensureSession(academic, schoolClass, date, model, key, false);
        return summaries(classId, date);
    }

    @Transactional
    public RosterView roster(UUID classId, LocalDate date, String periodKey) {
        AcademicSession academic = requireAcademicSession(date);
        classId = canonicalAttendanceClass(academic.getId(), classId);
        SchoolClass schoolClass = requireClass(classId);
        teacherScope.assertClass(academic.getId(), classId, date);
        String model = modelFor(schoolClass.getLevel());
        List<SessionKey> keys = keysFor(schoolClass, date, model);
        SessionKey key;
        if (model.equals("DAILY")) {
            if (periodKey != null && !periodKey.isBlank() && !"DAILY".equalsIgnoreCase(periodKey.trim()))
                throw ApiException.badRequest("Le primaire et la maternelle utilisent exclusivement la période DAILY");
            key = new SessionKey("DAILY", null, null);
        }
        else key = keys.stream().filter(k -> k.periodKey().equals(periodKey)).findFirst()
            .orElseThrow(() -> ApiException.badRequest("Sélectionnez une période publiée dans l'emploi du temps"));
        policy.require("ATTENDANCE_ROSTER_VIEW", attendanceContext(academic.getId(), schoolClass, date, key));
        UUID sessionId = ensureSession(academic, schoolClass, date, model, key, true);
        return rosterById(sessionId);
    }

    @Transactional(readOnly = true)
    public RosterView rosterById(UUID sessionId) {
        SessionSummary summary = summary(sessionId);
        PolicyResourceContext context = attendanceContextFor(summary, sessionId);
        policy.require("ATTENDANCE_ROSTER_VIEW", context);
        assertAttendanceClass(context);
        List<RosterMark> marks = jdbc.query("""
            SELECT m.student_id, st.matricule,
                   upper(st.last_name) || ' ' || st.first_name AS student_name,
                   lower(m.status) AS status, m.reason, m.note, m.late_minutes, lower(m.source) AS source
              FROM attendance_mark m JOIN student st ON st.id=m.student_id
             WHERE m.school_id=? AND m.attendance_session_id=?
             ORDER BY st.last_name, st.first_name
            """, (rs, n) -> new RosterMark(rs.getObject("student_id", UUID.class), rs.getString("matricule"),
                rs.getString("student_name"), rs.getString("status"), rs.getString("reason"),
                rs.getString("note"), rs.getInt("late_minutes"), rs.getString("source")),
            TenantContext.get(), sessionId);
        List<SessionEventView> events = jdbc.query("""
            SELECT action, coalesce(actor_username,'Système') actor, reason, occurred_at
              FROM attendance_session_event WHERE school_id=? AND attendance_session_id=?
             ORDER BY occurred_at DESC
            """, (rs, n) -> new SessionEventView(rs.getString("action"), rs.getString("actor"),
                rs.getString("reason"), rs.getObject("occurred_at", OffsetDateTime.class)),
            TenantContext.get(), sessionId);
        return new RosterView(summary, marks, events);
    }

    @Transactional
    public RosterView save(BulkMarkRequest request) {
        SessionSummary current = summary(request.sessionId());
        PolicyResourceContext context = attendanceContextFor(current, request.sessionId());
        policy.require("ATTENDANCE_MARK", context);
        assertAttendanceClass(context);
        if ("FINALIZED".equals(current.status()))
            throw ApiException.conflict("Cet appel est finalisé. Rouvrez-le avec un motif avant de le modifier.");
        int bumped = jdbc.update("""
            UPDATE attendance_session SET version=version+1, updated_at=now()
             WHERE id=? AND school_id=? AND version=? AND status<>'FINALIZED'
            """, request.sessionId(), TenantContext.get(), request.version());
        if (bumped == 0) throw ApiException.conflict("L'appel a été modifié par un autre utilisateur. Rechargez la liste avant de réessayer.");
        boolean reasonRequired = reasonRequired(current.classId());
        for (MarkInput mark : request.marks()) {
            String status = mark.status() == null ? "" : mark.status().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(status)) throw ApiException.badRequest("Statut de présence invalide");
            if (reasonRequired && Set.of("ABSENT", "EXCUSED").contains(status)
                && (mark.reason() == null || mark.reason().isBlank()))
                throw ApiException.badRequest("Un motif est obligatoire pour chaque absence");
            int changed = jdbc.update("""
                UPDATE attendance_mark SET status=?, reason=?, note=?, late_minutes=?, source='ROSTER',
                       marked_at=now(), marked_by=?, version=version+1
                 WHERE school_id=? AND attendance_session_id=? AND student_id=?
                """, status, trim(mark.reason()), trim(mark.note()), Math.max(0, mark.lateMinutes()),
                actorId(), TenantContext.get(), request.sessionId(), mark.studentId());
            if (changed == 0) throw ApiException.badRequest("Un élève ne fait pas partie de cette liste d'appel");
        }
        event(request.sessionId(), "SAVED", null, "{\"marks\":" + request.marks().size() + "}");
        return rosterById(request.sessionId());
    }

    @Transactional
    public RosterView finalizeSession(UUID id, ActionRequest request) {
        SessionSummary current = summary(id);
        PolicyResourceContext context = attendanceContextFor(current, id);
        policy.require("ATTENDANCE_FINALIZE", context);
        assertAttendanceClass(context);
        Integer unmarked = jdbc.queryForObject("SELECT count(*) FROM attendance_mark WHERE attendance_session_id=? AND status='UNMARKED'", Integer.class, id);
        if (unmarked != null && unmarked > 0)
            throw ApiException.badRequest("Finalisation impossible : " + unmarked + " élève(s) ne sont pas encore marqués");
        int changed = jdbc.update("""
            UPDATE attendance_session SET status='FINALIZED', finalized_at=now(), finalized_by=?,
                   version=version+1, updated_at=now()
             WHERE id=? AND school_id=? AND version=? AND status<>'FINALIZED'
            """, actorId(), id, TenantContext.get(), request.version());
        if (changed == 0) throw ApiException.conflict("L'appel a déjà changé ou a été finalisé. Rechargez-le.");
        invalidateValidatedBulletins(id, current.classId(), current.date());
        event(id, "FINALIZED", trim(request.reason()), null);
        queueGuardianNotifications(id);
        return rosterById(id);
    }

    /** A finalized attendance result changes the inputs of still-editable validated bulletins. */
    private void invalidateValidatedBulletins(UUID sessionId, UUID classId, LocalDate date) {
        UUID academicSessionId = jdbc.queryForObject("SELECT academic_session_id FROM attendance_session WHERE id=? AND school_id=?",
                UUID.class, sessionId, TenantContext.get());
        jdbc.update("""
            UPDATE bulletin_version v SET state='SUPERSEDED'
             WHERE v.school_id=? AND v.state='VALIDATED'
               AND v.student_id IN (SELECT e.student_id FROM student_enrollment e
                                      WHERE e.school_id=? AND e.academic_session_id=? AND e.school_class_id=? AND e.status='ACTIVE')
               AND v.reporting_period_id IN (SELECT p.id FROM academic_reporting_period p
                                               WHERE p.academic_session_id=? AND p.start_date<=? AND p.end_date>=?)
            """, TenantContext.get(), TenantContext.get(), academicSessionId, classId,
                academicSessionId, Date.valueOf(date), Date.valueOf(date));
    }

    @Transactional
    public RosterView reopen(UUID id, ActionRequest request) {
        if (request.reason() == null || request.reason().isBlank())
            throw ApiException.badRequest("Le motif de réouverture est obligatoire");
        SessionSummary current = summary(id);
        PolicyResourceContext context = attendanceContextFor(current, id);
        policy.require("ATTENDANCE_REOPEN", context);
        assertAttendanceClass(context);
        int changed = jdbc.update("""
            UPDATE attendance_session SET status='REOPENED', reopened_at=now(), reopened_by=?, reopen_reason=?,
                   version=version+1, updated_at=now()
             WHERE id=? AND school_id=? AND version=? AND status='FINALIZED'
            """, actorId(), request.reason().trim(), id, TenantContext.get(), request.version());
        if (changed == 0) throw ApiException.conflict("Seul un appel finalisé et à jour peut être rouvert");
        event(id, "REOPENED", request.reason().trim(), null);
        return rosterById(id);
    }

    @Transactional
    public GenerationResult generate(LocalDate from, LocalDate to, boolean preview) {
        policy.require("ATTENDANCE_POLICY_MANAGE",
                new PolicyResourceContext(TenantContext.get(), null, from, ParcoursContext.get(),
                        null, null, null, null, null, null, null, null));
        if (from == null || to == null || from.isAfter(to)) throw ApiException.badRequest("Période de génération invalide");
        if (from.plusYears(1).isBefore(to)) throw ApiException.badRequest("La génération est limitée à une année à la fois");
        int expected = 0, synchronizedCount = 0;
        for (SchoolClass schoolClass : classes.findBySchoolIdOrderByName(TenantContext.get())) {
            String model = modelFor(schoolClass.getLevel());
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (!isTeachingDay(date)) continue;
                AcademicSession academic;
                try { academic = requireAcademicSession(date); } catch (ApiException ignored) { continue; }
                if (cohorts != null && !schoolClass.getId().equals(
                        cohorts.attendanceClass(academic.getId(), schoolClass.getId()))) continue;
                List<SessionKey> keys = keysFor(schoolClass, date, model);
                expected += keys.size();
                if (!preview) for (SessionKey key : keys) {
                    // Generation snapshots the enrolled roster as UNMARKED. Those rows are
                    // intentional analytics denominator entries until a teacher completes the call.
                    ensureSession(academic, schoolClass, date, model, key, true);
                    synchronizedCount++;
                }
            }
        }
        if (!preview) audit(null, "SESSIONS_GENERATED", from + "/" + to, "expected=" + expected);
        return new GenerationResult(preview, from, to, expected, synchronizedCount);
    }

    @Transactional(readOnly = true)
    public AnalyticsView analytics(LocalDate from, LocalDate to, UUID classId) {
        if (from == null || to == null || from.isAfter(to)) throw ApiException.badRequest("Période d'analyse invalide");
        if (classId != null) teacherScope.assertClass(classId);
        Set<UUID> allowed = teacherScope.allowedClassIds();
        Set<UUID> permittedSessions = permittedAnalyticsSessions(from, to, classId, allowed);
        if (permittedSessions.isEmpty()) return emptyAnalytics(from, to);
        StringBuilder sql = new StringBuilder("""
            SELECT st.id student_id, st.matricule, upper(st.last_name) || ' ' || st.first_name student_name,
                   c.name class_name, count(*) expected,
                   count(*) FILTER (WHERE m.status='PRESENT') present,
                   count(*) FILTER (WHERE m.status='LATE') late,
                   count(*) FILTER (WHERE m.status='ABSENT') absent,
                   count(*) FILTER (WHERE m.status='EXCUSED') excused,
                   count(*) FILTER (WHERE m.status='UNMARKED') unmarked
              FROM attendance_mark m
              JOIN attendance_session s ON s.id=m.attendance_session_id
              JOIN student st ON st.id=m.student_id
              JOIN school_class c ON c.id=s.school_class_id
             WHERE m.school_id=? AND s.session_date BETWEEN ? AND ?
            """);
        List<Object> args = new ArrayList<>(List.of(TenantContext.get(), Date.valueOf(from), Date.valueOf(to)));
        if (classId != null) { sql.append(" AND s.school_class_id=?"); args.add(classId); }
        if (allowed != null) {
            if (allowed.isEmpty()) return emptyAnalytics(from, to);
            sql.append(" AND s.school_class_id IN (").append(String.join(",", Collections.nCopies(allowed.size(), "?"))).append(")");
            args.addAll(allowed);
        }
        sql.append(" AND s.id IN (").append(String.join(",", Collections.nCopies(permittedSessions.size(), "?"))).append(")");
        args.addAll(permittedSessions);
        sql.append(" GROUP BY st.id, st.matricule, st.last_name, st.first_name, c.name ORDER BY c.name, st.last_name, st.first_name");
        List<StudentAnalytics> students = jdbc.query(sql.toString(), (rs, n) -> {
            int expected = rs.getInt("expected"), present = rs.getInt("present"), late = rs.getInt("late");
            return new StudentAnalytics(rs.getObject("student_id", UUID.class), rs.getString("matricule"),
                rs.getString("student_name"), rs.getString("class_name"), expected, present, late,
                rs.getInt("absent"), rs.getInt("excused"), rs.getInt("unmarked"), percent(present + late, expected));
        }, args.toArray());
        int expected = students.stream().mapToInt(StudentAnalytics::expected).sum();
        int present = students.stream().mapToInt(StudentAnalytics::present).sum();
        int late = students.stream().mapToInt(StudentAnalytics::late).sum();
        int absent = students.stream().mapToInt(StudentAnalytics::absent).sum();
        int excused = students.stream().mapToInt(StudentAnalytics::excused).sum();
        int unmarked = students.stream().mapToInt(StudentAnalytics::unmarked).sum();
        return new AnalyticsView(from, to, expected, present, late, absent, excused, unmarked,
            percent(present + late, expected), students);
    }

    @Transactional(readOnly = true)
    public List<DeviceReconciliation> reconciliation(LocalDate date) {
        policy.require("ATTENDANCE_RECONCILE",
                new PolicyResourceContext(TenantContext.get(), null, date == null ? LocalDate.now() : date,
                        ParcoursContext.get(), null, null, null, null, null, null, null, null));
        LocalDate target = date == null ? LocalDate.now() : date;
        return jdbc.query("""
            SELECT r.id, r.student_id, st.matricule, upper(st.last_name)||' '||st.first_name student_name,
                   st.class_name, r.att_date, r.status, r.check_in_time,
                   m.id IS NOT NULL reconciled, m.attendance_session_id
              FROM attendance_record r JOIN student st ON st.id=r.student_id
              LEFT JOIN attendance_mark m ON m.device_record_id=r.id
             WHERE r.school_id=? AND r.att_date=? AND r.source='fingerprint'
             ORDER BY r.check_in_time, st.last_name
            """, (rs, n) -> new DeviceReconciliation(rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class), rs.getString("matricule"), rs.getString("student_name"),
                rs.getString("class_name"), rs.getObject("att_date", LocalDate.class), rs.getString("status"),
                rs.getString("check_in_time"), rs.getBoolean("reconciled"),
                rs.getObject("attendance_session_id", UUID.class)), TenantContext.get(), Date.valueOf(target));
    }

    @Transactional
    public RosterView reconcile(ReconcileRequest request) {
        SessionSummary session = summary(request.sessionId());
        policy.require("ATTENDANCE_RECONCILE",
                new PolicyResourceContext(TenantContext.get(), null, session.date(), ParcoursContext.get(),
                        null, null, null, null, null, null, null, null));
        teacherScope.assertClass(session.classId());
        Map<String, Object> device = jdbc.queryForMap("""
            SELECT id, student_id, status, late_minutes FROM attendance_record
             WHERE id=? AND school_id=? AND source='fingerprint'
            """, request.deviceRecordId(), TenantContext.get());
        Integer alreadyReconciled = jdbc.queryForObject("""
            SELECT count(*) FROM attendance_mark
             WHERE school_id=? AND device_record_id=?
            """, Integer.class, TenantContext.get(), request.deviceRecordId());
        if (alreadyReconciled != null && alreadyReconciled > 0) {
            throw ApiException.conflict("Ce pointage lecteur est déjà rapproché.");
        }
        UUID studentId = (UUID) device.get("student_id");
        String status = String.valueOf(device.get("status")).toUpperCase(Locale.ROOT);
        int changed = jdbc.update("""
            UPDATE attendance_mark SET status=?, late_minutes=?, source='FINGERPRINT', device_record_id=?,
                   marked_at=now(), marked_by=?, version=version+1
             WHERE school_id=? AND attendance_session_id=? AND student_id=?
            """, status, device.get("late_minutes"), request.deviceRecordId(), actorId(),
            TenantContext.get(), request.sessionId(), studentId);
        if (changed == 0) throw ApiException.badRequest("Le pointage concerne un élève absent de cette liste d'appel");
        jdbc.update("UPDATE attendance_session SET version=version+1, updated_at=now() WHERE id=?", request.sessionId());
        event(request.sessionId(), "DEVICE_RECONCILED", null, "{\"deviceRecordId\":\"" + request.deviceRecordId() + "\"}");
        return rosterById(request.sessionId());
    }

    @Transactional
    public AlertScanResult scanAlerts(LocalDate from, LocalDate to) {
        policy.require("ATTENDANCE_POLICY_MANAGE",
                new PolicyResourceContext(TenantContext.get(), null, from == null ? LocalDate.now() : from,
                        ParcoursContext.get(), null, null, null, null, null, null, null, null));
        AnalyticsView view = analytics(from, to, null);
        BigDecimal fallback = policies().stream().map(PolicyView::chronicAbsencePercent)
            .min(BigDecimal::compareTo).orElse(new BigDecimal("20"));
        int changed = 0;
        for (StudentAnalytics row : view.students()) {
            BigDecimal absence = percent(row.absent() + row.unmarked(), row.expected());
            if (absence.compareTo(fallback) < 0) continue;
            String severity = absence.compareTo(new BigDecimal("35")) >= 0 ? "critical" : "warn";
            changed += jdbc.update("""
                INSERT INTO alert(school_id, student_id, type, severity, title, detail, dedup_key, status)
                VALUES (?,?,'absences',?,'Absentéisme chronique',?,?,'open')
                ON CONFLICT (school_id, dedup_key) DO UPDATE SET severity=excluded.severity,
                    title=excluded.title, detail=excluded.detail
                WHERE alert.status='open'
                """, TenantContext.get(), row.studentId(), severity,
                row.absent() + " absence(s), " + row.unmarked() + " non marqué(s) sur " + row.expected()
                    + " séances attendues (" + absence + " %).",
                "attendance:" + row.studentId() + ":" + from + ":" + to);
        }
        return new AlertScanResult(changed, fallback);
    }

    @Transactional(readOnly = true)
    public List<NotificationView> notifications(String status) {
        policy.require("ATTENDANCE_NOTIFICATION_VIEW",
                new PolicyResourceContext(TenantContext.get(), null, LocalDate.now(), ParcoursContext.get(),
                        null, null, null, null, null, null, null, null));
        String normalized = status == null || status.isBlank() ? null : status.toUpperCase(Locale.ROOT);
        return jdbc.query("""
            SELECT n.id, n.attendance_session_id, n.student_id,
                   upper(st.last_name)||' '||st.first_name student_name,
                   coalesce(g.display_name,'Portail parent') guardian_name,
                   n.channel, n.recipient, n.status, n.attempt_count, n.created_at
              FROM attendance_notification n
              JOIN student st ON st.id=n.student_id
              LEFT JOIN guardian g ON g.id=n.guardian_id
             WHERE n.school_id=? AND n.status=coalesce(?,n.status)
             ORDER BY n.created_at DESC LIMIT 250
            """, (rs, i) -> new NotificationView(rs.getObject("id", UUID.class),
                rs.getObject("attendance_session_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getString("student_name"), rs.getString("guardian_name"), rs.getString("channel"),
                rs.getString("recipient"), rs.getString("status"), rs.getInt("attempt_count"),
                rs.getObject("created_at", OffsetDateTime.class)), TenantContext.get(), normalized);
    }

    private void queueGuardianNotifications(UUID sessionId) {
        jdbc.update("""
            INSERT INTO attendance_notification(school_id, attendance_session_id, attendance_mark_id,
                student_id, guardian_id, channel, recipient, subject, message)
            SELECT m.school_id, s.id, m.id, st.id, g.id,
                   CASE WHEN g.normalized_email IS NOT NULL THEN 'EMAIL'
                        WHEN g.normalized_phone IS NOT NULL THEN 'SMS' ELSE 'IN_APP' END,
                   coalesce(g.normalized_email, g.normalized_phone),
                   'Information de présence - ' || st.first_name || ' ' || st.last_name,
                   CASE WHEN m.status='ABSENT' THEN 'Absence enregistrée'
                        WHEN m.status='LATE' THEN 'Retard de ' || m.late_minutes || ' minute(s) enregistré'
                        ELSE 'Présence excusée enregistrée' END
                       || ' le ' || to_char(s.session_date,'DD/MM/YYYY')
                       || CASE WHEN s.subject_code IS NULL THEN '' ELSE ' (' || s.subject_code || ')' END
              FROM attendance_session s
              JOIN attendance_mark m ON m.attendance_session_id=s.id
              JOIN student st ON st.id=m.student_id
              LEFT JOIN student_guardian sg ON sg.student_id=st.id AND sg.school_id=s.school_id
                   AND sg.receives_attendance AND sg.portal_access
                   AND sg.effective_from<=s.session_date
                   AND (sg.effective_to IS NULL OR sg.effective_to>=s.session_date)
              LEFT JOIN guardian g ON g.id=sg.guardian_id AND g.status IN ('ACTIVE','INVITED','NO_PORTAL')
             WHERE s.id=? AND s.school_id=? AND m.status IN ('ABSENT','LATE','EXCUSED')
            ON CONFLICT DO NOTHING
            """, sessionId, TenantContext.get());
        jdbc.update("""
            INSERT INTO alert(school_id,student_id,type,severity,title,detail,dedup_key,status)
            SELECT m.school_id,m.student_id,'absences',
                   CASE WHEN m.status='ABSENT' THEN 'warn' ELSE 'info' END,
                   CASE WHEN m.status='ABSENT' THEN 'Absence du jour' ELSE 'Retard du jour' END,
                   coalesce(m.reason,'Aucun motif renseigné'),
                   'attendance-session:'||s.id||':'||m.student_id,'open'
              FROM attendance_session s JOIN attendance_mark m ON m.attendance_session_id=s.id
             WHERE s.id=? AND s.school_id=? AND m.status IN ('ABSENT','LATE')
            ON CONFLICT (school_id,dedup_key) DO NOTHING
            """, sessionId, TenantContext.get());
    }

    private UUID ensureSession(AcademicSession academic, SchoolClass schoolClass, LocalDate date,
                               String model, SessionKey key, boolean initializeMarks) {
        String sourceVersion = academic.getId() + ":" + model;
        int durationMinutes = durationMinutes(academic, date, model, key);
        UUID expectedId = jdbc.queryForObject("""
            INSERT INTO expected_school_session(school_id, academic_session_id, school_class_id,
                session_date, model, period_key, source, source_version)
            VALUES (?,?,?,?,?,?,'ATTENDANCE',?)
            ON CONFLICT (school_id, academic_session_id, school_class_id, session_date, period_key)
            DO UPDATE SET model=excluded.model, source_version=excluded.source_version
            RETURNING id
            """, UUID.class, TenantContext.get(), academic.getId(), schoolClass.getId(), Date.valueOf(date),
            model, key.periodKey(), sourceVersion);
        UUID sessionId = jdbc.queryForObject("""
            INSERT INTO attendance_session(school_id, academic_session_id, expected_session_id,
                school_class_id, session_date, model, period_key, subject_code, duration_minutes)
            VALUES (?,?,?,?,?,?,?,?,?)
            ON CONFLICT (school_id, academic_session_id, school_class_id, session_date, period_key)
            DO UPDATE SET expected_session_id=excluded.expected_session_id,
                          subject_code=coalesce(excluded.subject_code, attendance_session.subject_code),
                          duration_minutes=CASE WHEN attendance_session.duration_minutes=0 THEN excluded.duration_minutes ELSE attendance_session.duration_minutes END
            RETURNING id
            """, UUID.class, TenantContext.get(), academic.getId(), expectedId, schoolClass.getId(),
            Date.valueOf(date), model, key.periodKey(), key.subjectCode(), durationMinutes);
        if (initializeMarks) jdbc.update("""
            INSERT INTO attendance_mark(school_id, attendance_session_id, student_id)
            SELECT ?, ?, st.id FROM student_enrollment e
              JOIN student st ON st.id=e.student_id AND st.active
             WHERE e.school_id=? AND e.academic_session_id=? AND e.status='ACTIVE'
               AND (e.school_class_id=? OR e.cohort_id=(SELECT cohort_id FROM academic_cohort_programme
                    WHERE school_id=? AND academic_session_id=? AND school_class_id=? AND active LIMIT 1))
            ON CONFLICT DO NOTHING
            """, TenantContext.get(), sessionId, TenantContext.get(), academic.getId(), schoolClass.getId(),
                TenantContext.get(), academic.getId(), schoolClass.getId());
        return sessionId;
    }

    /** Duration used for analytics: daily school hours or the timetable period length. */
    private int durationMinutes(AcademicSession academic, LocalDate date, String model, SessionKey key) {
        Integer duration = jdbc.query("SELECT EXTRACT(EPOCH FROM (end_time - start_time))/60 FROM school_calendar_day WHERE school_id=? AND academic_session_id=? AND day_of_week=? AND teaching_day",
                rs -> rs.next() && rs.getObject(1) != null ? rs.getInt(1) : null,
                TenantContext.get(), academic.getId(), date.getDayOfWeek().getValue());
        if ("PERIOD".equals(model) && key.periodKey() != null && key.periodKey().startsWith("P")) {
            try {
                int slot = Integer.parseInt(key.periodKey().substring(1)) - 1;
                int fallback = duration == null ? 0 : duration;
                duration = jdbc.query("SELECT EXTRACT(EPOCH FROM (end_time - start_time))/60 FROM timetable_period WHERE school_id=? AND slot_idx=? AND active",
                        rs -> rs.next() && rs.getObject(1) != null ? rs.getInt(1) : fallback,
                        TenantContext.get(), slot);
            } catch (NumberFormatException ignored) { /* keep daily fallback */ }
        }
        return duration == null ? 0 : Math.max(0, duration);
    }

    private List<SessionKey> keysFor(SchoolClass schoolClass, LocalDate date, String model) {
        if (!isTeachingDay(date)) return List.of();
        if (model.equals("DAILY")) return List.of(new SessionKey("DAILY", null, null));
        int dayIdx = date.getDayOfWeek().getValue() - 1;
        AcademicSession academic = requireAcademicSession(date);
        Boolean published = jdbc.query("""
            SELECT status='PUBLISHED' FROM timetable_class_config
             WHERE school_id=? AND academic_session_id=? AND class_id=?
            """, rs -> rs.next() && rs.getBoolean(1), TenantContext.get(), academic.getId(), schoolClass.getId());
        if (!Boolean.TRUE.equals(published)) return List.of();
        // A session is generated from the timetable version effective on the date,
        // never from archived/reopened copies. Reading the slot table without the
        // published-version join multiplies Secondary occurrences after each
        // timetable revision and creates duplicate attendance rosters.
        return jdbc.query("""
            SELECT DISTINCT ON (ts.slot_idx) ts.id, ts.slot_idx, ts.subject_code
              FROM timetable_slot ts
              JOIN timetable_version tv ON tv.id=ts.timetable_version_id
               AND tv.school_id=ts.school_id
               AND tv.academic_session_id=ts.academic_session_id
               AND tv.status='PUBLISHED'
               AND tv.effective_from<=?
               AND (tv.effective_to IS NULL OR tv.effective_to>=?)
             WHERE ts.school_id=? AND ts.academic_session_id=? AND ts.class_id=?
               AND ts.day_idx=? AND ts.subject_code IS NOT NULL
               AND trim(ts.subject_code)<>''
             ORDER BY ts.slot_idx, tv.version_no DESC, ts.id
            """, (rs, n) -> new SessionKey("P" + (rs.getInt("slot_idx") + 1),
                    rs.getString("subject_code"), rs.getObject("id", UUID.class)),
            date, date, TenantContext.get(), academic.getId(), schoolClass.getId(), dayIdx);
    }

    private boolean isTeachingDay(LocalDate date) {
        Integer teaching = jdbc.query("""
            SELECT CASE
                       WHEN EXISTS (
                           SELECT 1 FROM school_holiday h
                            WHERE h.school_id=s.school_id AND h.holiday_date=?
                       ) THEN 0
                       WHEN d.id IS NULL THEN NULL
                       WHEN d.teaching_day THEN 1 ELSE 0
                   END
              FROM academic_session s LEFT JOIN school_calendar_day d
                ON d.academic_session_id=s.id AND d.day_of_week=?
             WHERE s.school_id=? AND ? BETWEEN s.start_date AND s.end_date
             ORDER BY s.is_current DESC LIMIT 1
            """, rs -> rs.next() ? (Integer) rs.getObject(1) : null,
            Date.valueOf(date), date.getDayOfWeek().getValue(), TenantContext.get(), Date.valueOf(date));
        // A newly opened academic session may not have explicit weekday rows yet.
        // Preserve the school's standard Monday-Friday calendar until admins customize it.
        return teaching == null ? date.getDayOfWeek().getValue() <= 5 : teaching == 1;
    }

    private List<SessionSummary> summaries(UUID classId, LocalDate date) {
        return jdbc.query(summarySql() + " WHERE s.school_id=? AND s.school_class_id=? AND s.session_date=? GROUP BY " + summaryGroup() + " ORDER BY s.period_key",
            this::mapSummary, TenantContext.get(), classId, Date.valueOf(date)).stream()
            .filter(summary -> policy.decide("ATTENDANCE_ROSTER_VIEW",
                    attendanceContextFor(summary, summary.id())).allowed())
            .toList();
    }

    private SessionSummary summary(UUID id) {
        List<SessionSummary> rows = jdbc.query(summarySql() + " WHERE s.school_id=? AND s.id=? GROUP BY " + summaryGroup(),
            this::mapSummary, TenantContext.get(), id);
        if (rows.isEmpty()) throw ApiException.notFound("Séance d'appel");
        return rows.getFirst();
    }

    private String summarySql() {
        return """
            SELECT s.id, s.school_class_id, c.name class_name, s.session_date, s.model, s.period_key,
                   s.subject_code, s.status, s.version, count(m.id) total,
                   count(m.id) FILTER (WHERE m.status<>'UNMARKED') marked
              FROM attendance_session s JOIN school_class c ON c.id=s.school_class_id
              LEFT JOIN attendance_mark m ON m.attendance_session_id=s.id
            """;
    }
    private String summaryGroup() { return "s.id, s.school_class_id, c.name, s.session_date, s.model, s.period_key, s.subject_code, s.status, s.version"; }
    private SessionSummary mapSummary(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new SessionSummary(rs.getObject("id", UUID.class), rs.getObject("school_class_id", UUID.class),
            rs.getString("class_name"), rs.getObject("session_date", LocalDate.class), rs.getString("model"),
            rs.getString("period_key"), rs.getString("subject_code"), rs.getString("status"),
            rs.getLong("version"), rs.getInt("total"), rs.getInt("marked"));
    }

    private AcademicSession requireAcademicSession(LocalDate date) {
        List<AcademicSession> matches = sessions.findBySchoolIdOrderByStartDateDesc(TenantContext.get()).stream()
            .filter(s -> !date.isBefore(s.getStartDate()) && !date.isAfter(s.getEndDate()))
            .toList();
        if (matches.isEmpty()) throw ApiException.badRequest("Aucune année scolaire ne couvre la date " + date);
        return matches.getFirst();
    }

    private SchoolClass requireClass(UUID id) {
        return classes.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Classe"));
    }
    private String modelFor(String level) {
        String normalized = normalizeLevel(level);
        return jdbc.query("SELECT model FROM attendance_policy WHERE school_id=? AND level=?",
            rs -> rs.next() ? rs.getString(1) : defaultModel(normalized), TenantContext.get(), normalized);
    }
    private boolean reasonRequired(UUID classId) {
        SchoolClass c = requireClass(classId);
        Boolean result = jdbc.query("SELECT require_absence_reason FROM attendance_policy WHERE school_id=? AND level=?",
            rs -> rs.next() && rs.getBoolean(1), TenantContext.get(), normalizeLevel(c.getLevel()));
        return Boolean.TRUE.equals(result);
    }
    private String normalizeLevel(String level) {
        String value = level == null ? "" : level.trim().toLowerCase(Locale.ROOT);
        if (Set.of("nursery", "maternelle", "maternal").contains(value)) return "maternelle";
        if (Set.of("primary", "primaire").contains(value)) return "primary";
        if (Set.of("secondary", "secondaire").contains(value)) return "secondary";
        throw ApiException.badRequest("Niveau scolaire non reconnu : " + level);
    }
    private String defaultModel(String level) { return normalizeLevel(level).equals("secondary") ? "PERIOD" : "DAILY"; }
    private BigDecimal percent(int numerator, int denominator) {
        return denominator == 0 ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(numerator)
            .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
    private AnalyticsView emptyAnalytics(LocalDate from, LocalDate to) {
        return new AnalyticsView(from, to, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO.setScale(2), List.of());
    }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private AppUserPrincipal principal() {
        Object p = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .map(a -> a.getPrincipal()).orElse(null);
        return p instanceof AppUserPrincipal user ? user : null;
    }
    private UUID actorId() { return principal() == null ? null : principal().userId(); }
    private String actorUsername() { return principal() == null ? "system" : principal().username(); }
    private void event(UUID sessionId, String action, String reason, String details) {
        jdbc.update("""
            INSERT INTO attendance_session_event(school_id, attendance_session_id, actor_user_id,
                actor_username, action, reason, details) VALUES (?,?,?,?,?,?,?::jsonb)
            """, TenantContext.get(), sessionId, actorId(), actorUsername(), action, reason,
            details == null ? "{}" : details);
    }
    private void audit(UUID aggregateId, String action, String reason, String details) {
        jdbc.update("""
            INSERT INTO audit_event(school_id, actor_user_id, actor_username, action, aggregate_type,
                aggregate_id, reason, after_data) VALUES (?,?,?,?,?,?,?,?::jsonb)
            """, TenantContext.get(), actorId(), actorUsername(), action, "ATTENDANCE",
            aggregateId == null ? null : aggregateId.toString(), reason,
            details == null ? "{}" : "{\"detail\":\"" + details.replace("\"", "'") + "\"}");
    }

    private PolicyResourceContext attendanceContext(UUID academicSessionId, SchoolClass schoolClass,
                                                    LocalDate date, SessionKey key) {
        return new PolicyResourceContext(TenantContext.get(), academicSessionId, date, ParcoursContext.get(),
                schoolClass.getId(), key.subjectCode(), null, key.occurrenceId(), null, null,
                key.periodKey(), schoolClass.getLevel());
    }

    private PolicyResourceContext attendanceContextFor(SessionSummary summary, UUID attendanceSessionId) {
        UUID academicSessionId = jdbc.queryForObject(
                "SELECT academic_session_id FROM attendance_session WHERE school_id=? AND id=?",
                UUID.class, TenantContext.get(), attendanceSessionId);
        SchoolClass schoolClass = requireClass(summary.classId());
        UUID occurrenceId = "DAILY".equalsIgnoreCase(summary.model()) ? null
                : resolvePublishedOccurrence(academicSessionId, schoolClass.getId(), summary.date(),
                summary.periodKey(), summary.subjectCode());
        return attendanceContext(academicSessionId, schoolClass, summary.date(),
                new SessionKey(summary.periodKey(), summary.subjectCode(), occurrenceId));
    }

    private void assertAttendanceClass(PolicyResourceContext context) {
        if (context == null || context.classId() == null) return;
        if (context.academicSessionId() != null && context.effectiveDate() != null) {
            teacherScope.assertClass(context.academicSessionId(), context.classId(), context.effectiveDate());
        } else {
            teacherScope.assertClass(context.classId());
        }
    }

    private boolean attendanceClassAllowed(SchoolClass schoolClass, UUID academicSessionId, LocalDate date) {
        if (academicSessionId == null) return false;
        String model = modelFor(schoolClass.getLevel());
        List<SessionKey> keys = keysFor(schoolClass, date, model);
        return keys.stream().anyMatch(key -> policy.decide("ATTENDANCE_ROSTER_VIEW",
                attendanceContext(academicSessionId, schoolClass, date, key)).allowed());
    }

    /** Only one selector entry represents a shared daily roster. */
    private boolean attendanceRepresentative(SchoolClass schoolClass, UUID sessionId) {
        if (cohorts == null) return true;
        UUID representative = cohorts.attendanceClass(sessionId, schoolClass.getId());
        return representative == null || representative.equals(schoolClass.getId());
    }

    private boolean sharedAllowed(SchoolClass schoolClass, UUID sessionId, Set<UUID> allowed) {
        if (cohorts == null || sessionId == null || allowed == null) return false;
        UUID cohortId = cohorts.cohortForClass(sessionId, schoolClass.getId());
        if (cohortId == null || !cohorts.isSharedBilingual(sessionId, cohortId)) return false;
        return jdbc.query("""
                SELECT EXISTS (
                    SELECT 1 FROM academic_cohort_programme
                     WHERE school_id=? AND academic_session_id=? AND cohort_id=?
                       AND school_class_id = ANY(?) AND active)
                """, rs -> rs.next() && rs.getBoolean(1), TenantContext.get(), sessionId,
                cohortId, allowed.toArray(UUID[]::new));
    }

    private UUID canonicalAttendanceClass(UUID sessionId, UUID classId) {
        return cohorts == null ? classId : cohorts.attendanceClass(sessionId, classId);
    }

    private Set<UUID> permittedAnalyticsSessions(LocalDate from, LocalDate to, UUID requestedClassId,
                                                 Set<UUID> teacherClasses) {
        Map<UUID, SchoolClass> classesById = classes.findBySchoolIdOrderByName(TenantContext.get()).stream()
                .collect(Collectors.toMap(SchoolClass::getId, c -> c));
        List<AnalyticsSession> candidates = jdbc.query("""
                SELECT id, academic_session_id, school_class_id, session_date, model, period_key, subject_code
                  FROM attendance_session
                 WHERE school_id=? AND session_date BETWEEN ? AND ?
                """, (rs, n) -> new AnalyticsSession(rs.getObject("id", UUID.class),
                rs.getObject("academic_session_id", UUID.class), rs.getObject("school_class_id", UUID.class),
                rs.getObject("session_date", LocalDate.class), rs.getString("model"),
                rs.getString("period_key"), rs.getString("subject_code")), TenantContext.get(),
                Date.valueOf(from), Date.valueOf(to));
        Set<UUID> permitted = new LinkedHashSet<>();
        for (AnalyticsSession candidate : candidates) {
            if (requestedClassId != null && !requestedClassId.equals(candidate.classId())) continue;
            if (teacherClasses != null && !teacherClasses.contains(candidate.classId())) continue;
            SchoolClass schoolClass = classesById.get(candidate.classId());
            if (schoolClass == null) continue;
            UUID occurrenceId = "DAILY".equalsIgnoreCase(candidate.model()) ? null
                    : resolvePublishedOccurrence(candidate.academicSessionId(), candidate.classId(), candidate.date(),
                    candidate.periodKey(), candidate.subjectCode());
            SessionKey key = new SessionKey(candidate.periodKey(), candidate.subjectCode(), occurrenceId);
            PolicyResourceContext context = attendanceContext(candidate.academicSessionId(), schoolClass,
                    candidate.date(), key);
            if (policy.decide("ATTENDANCE_ANALYTICS_VIEW", context).allowed()) permitted.add(candidate.id());
        }
        return permitted;
    }

    /** Resolve the exact published slot used by a secondary attendance session. */
    private UUID resolvePublishedOccurrence(UUID academicSessionId, UUID classId, LocalDate date,
                                            String periodKey, String subjectCode) {
        int dayIndex = date.getDayOfWeek().getValue() - 1;
        List<UUID> ids = jdbc.query("""
                SELECT s.id
                  FROM timetable_slot s
                  JOIN timetable_version v ON v.id=s.timetable_version_id
                   AND v.school_id=s.school_id AND v.academic_session_id=s.academic_session_id
                   AND v.status='PUBLISHED' AND v.effective_from<=?
                   AND (v.effective_to IS NULL OR v.effective_to>=?)
                  LEFT JOIN timetable_period p ON p.school_id=s.school_id
                   AND p.slot_idx=s.slot_idx AND p.active
                 WHERE s.school_id=? AND s.academic_session_id=? AND s.class_id=? AND s.day_idx=?
                   AND upper(coalesce(s.subject_code,''))=upper(?)
                   AND (upper(coalesce(p.label,''))=upper(?)
                        OR upper('P'||(s.slot_idx+1)::text)=upper(?)
                        OR s.slot_idx::text=?)
                 ORDER BY s.id
                 LIMIT 1
                """, (rs, n) -> rs.getObject(1, UUID.class), date, date, TenantContext.get(),
                academicSessionId, classId, dayIndex, subjectCode, periodKey, periodKey, periodKey);
        return ids.stream().findFirst().orElse(null);
    }

    private record AnalyticsSession(UUID id, UUID academicSessionId, UUID classId, LocalDate date,
                                    String model, String periodKey, String subjectCode) {}
    private record SessionKey(String periodKey, String subjectCode, UUID occurrenceId) {}
}
