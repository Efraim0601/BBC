package com.bbc.sms.attendance;

import com.bbc.sms.attendance.dto.AttendanceDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.realtime.RealtimeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.settings.SchoolProfileService;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class AttendanceService {

    /**
     * A reader counts as online if it checked in within this window. The on-site agent
     * posts on every scan, so a quiet stretch mid-lesson is normal — the window is wide
     * enough not to flap, tight enough that an unplugged reader shows up within the hour.
     */
    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(60);

    private final AttendanceRepository repo;
    private final DeviceRepository devices;
    private final StudentRepository students;
    private final RealtimeService realtime;
    private final SchoolProfileService schoolProfile;
    private final TeacherScopeService teacherScope;
    private final AuthorizationPolicyService policy;
    private final JdbcTemplate jdbc;

    public AttendanceService(AttendanceRepository repo, DeviceRepository devices,
                             StudentRepository students, RealtimeService realtime,
                             SchoolProfileService schoolProfile, TeacherScopeService teacherScope,
                             AuthorizationPolicyService policy, JdbcTemplate jdbc) {
        this.repo = repo;
        this.devices = devices;
        this.students = students;
        this.realtime = realtime;
        this.schoolProfile = schoolProfile;
        this.teacherScope = teacherScope;
        this.policy = policy;
        this.jdbc = jdbc;
    }

    /** Reader health for the tenant — drives the Attendance and Settings status cards. */
    @Transactional(readOnly = true)
    public List<DeviceView> devices() {
        policy.require("ATTENDANCE_DEVICE_VIEW",
                new PolicyResourceContext(TenantContext.get(), null, LocalDate.now(), ParcoursContext.get(),
                        null, null, null, null, null, null, null, null));
        OffsetDateTime now = OffsetDateTime.now();
        return devices.findBySchoolIdOrderByLabel(TenantContext.get()).stream()
                .map(d -> {
                    OffsetDateTime seen = d.getLastSeenAt();
                    Long minutes = seen == null ? null : Duration.between(seen, now).toMinutes();
                    boolean online = d.isActive() && seen != null
                            && Duration.between(seen, now).compareTo(ONLINE_WINDOW) <= 0;
                    return new DeviceView(d.getId(), d.getLabel(), d.getLocation(), d.getModel(),
                            d.isActive(), online, seen, minutes);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public DailyBoard board(LocalDate date) {
        UUID schoolId = TenantContext.get();
        // Stage 1: fetch only session/class context. No student identity or mark
        // data is materialized until the exact occurrence has been authorized.
        List<BoardSession> candidates = jdbc.query("""
                SELECT s.id, s.academic_session_id, s.school_class_id, c.name AS class_name,
                       c.level, s.session_date, s.model, s.period_key, s.subject_code,
                       occurrence.id AS occurrence_id
                  FROM attendance_session s
                  JOIN school_class c ON c.id=s.school_class_id AND c.school_id=s.school_id
                  LEFT JOIN LATERAL (
                       SELECT ts.id
                         FROM timetable_slot ts
                         JOIN timetable_version tv ON tv.id=ts.timetable_version_id
                          AND tv.school_id=ts.school_id
                          AND tv.academic_session_id=ts.academic_session_id
                          AND tv.status='PUBLISHED'
                          AND tv.effective_from<=s.session_date
                          AND (tv.effective_to IS NULL OR tv.effective_to>=s.session_date)
                         LEFT JOIN timetable_period tp ON tp.school_id=ts.school_id
                          AND tp.slot_idx=ts.slot_idx AND tp.active
                        WHERE ts.school_id=s.school_id
                          AND ts.academic_session_id=s.academic_session_id
                          AND ts.class_id=s.school_class_id
                          AND ts.day_idx=(extract(isodow from s.session_date)::int-1)
                          AND upper(coalesce(ts.subject_code,''))=upper(coalesce(s.subject_code,''))
                          AND (upper(coalesce(tp.label,''))=upper(coalesce(s.period_key,''))
                               OR upper('P'||(ts.slot_idx+1)::text)=upper(coalesce(s.period_key,''))
                               OR ts.slot_idx::text=coalesce(s.period_key,''))
                        ORDER BY tv.version_no DESC, ts.id LIMIT 1
                  ) occurrence ON upper(coalesce(s.model,''))<>'DAILY'
                 WHERE s.school_id=? AND s.session_date=?
                 ORDER BY c.name, s.period_key, s.subject_code
                """, (rs, n) -> new BoardSession(rs.getObject("id", UUID.class),
                rs.getObject("academic_session_id", UUID.class), rs.getObject("school_class_id", UUID.class),
                rs.getString("class_name"), rs.getString("level"), rs.getObject("session_date", LocalDate.class),
                rs.getString("model"), rs.getString("period_key"), rs.getString("subject_code"),
                rs.getObject("occurrence_id", UUID.class)), schoolId, date);
        Set<UUID> permittedSessionIds = candidates.stream()
                .filter(session -> policy.decide("ATTENDANCE_ROSTER_VIEW", boardContext(session)).allowed())
                .map(BoardSession::id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (permittedSessionIds.isEmpty()) return new DailyBoard(date, 0, 0, 0, List.of());

        // Stage 2: query marks and minimized student fields only for permitted
        // sessions, using the effective enrollment rather than student.class_id.
        String placeholders = String.join(",", Collections.nCopies(permittedSessionIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(schoolId); args.addAll(permittedSessionIds);
        List<AttendanceView> views = jdbc.query(("""
                SELECT m.student_id, st.matricule,
                       upper(st.last_name) || ' ' || st.first_name AS student_name,
                       c.name AS class_name, s.session_date,
                       lower(m.status) AS status, m.marked_at::text AS marked_at,
                       coalesce(m.late_minutes,0) AS late_minutes, lower(m.source) AS source
                  FROM attendance_mark m
                  JOIN attendance_session s ON s.id=m.attendance_session_id AND s.school_id=m.school_id
                  JOIN student st ON st.id=m.student_id AND st.school_id=m.school_id AND st.active
                  JOIN student_enrollment e ON e.student_id=st.id AND e.school_id=m.school_id
                   AND e.academic_session_id=s.academic_session_id AND e.status='ACTIVE'
                   AND e.school_class_id=s.school_class_id
                   AND e.enrolled_on<=s.session_date AND (e.exited_on IS NULL OR e.exited_on>=s.session_date)
                  JOIN school_class c ON c.id=e.school_class_id AND c.school_id=m.school_id
                 WHERE m.school_id=? AND s.id IN (%s)
                 ORDER BY c.name, st.last_name, st.first_name
                """).formatted(placeholders), (rs, n) -> new AttendanceView(
                rs.getObject("student_id", UUID.class), rs.getString("matricule"), rs.getString("student_name"),
                rs.getString("class_name"), rs.getObject("session_date", LocalDate.class), rs.getString("status"),
                rs.getString("marked_at"), rs.getInt("late_minutes"), rs.getString("source")), args.toArray());
        int present = (int) views.stream().filter(v -> "present".equals(v.status())).count();
        int late = (int) views.stream().filter(v -> "late".equals(v.status())).count();
        int absent = (int) views.stream().filter(v -> "absent".equals(v.status())).count();
        return new DailyBoard(date, present, late, absent, views);
    }

    @Transactional
    public AttendanceView mark(MarkRequest req) {
        UUID schoolId = TenantContext.get();
        // This compatibility endpoint has no occurrence fields. It is therefore
        // maintenance-only; ordinary ATTENDANCE_MARK grants must use the
        // session/roster workflow instead.
        policy.require("ATTENDANCE_RECONCILE", new PolicyResourceContext(schoolId, null,
                req.date(), ParcoursContext.get(), null, null, null, null, null, null, null, null));
        List<Map<String, Object>> enrollmentRows = jdbc.query("""
                SELECT e.academic_session_id, e.school_class_id, c.level
                  FROM student_enrollment e
                  JOIN school_class c ON c.id=e.school_class_id AND c.school_id=e.school_id
                 WHERE e.school_id=? AND e.student_id=? AND e.status='ACTIVE'
                   AND e.enrolled_on<=? AND (e.exited_on IS NULL OR e.exited_on>=?)
                 ORDER BY e.enrolled_on DESC LIMIT 1
                """, (rs, n) -> Map.of("academic_session_id", rs.getObject("academic_session_id", UUID.class),
                        "school_class_id", rs.getObject("school_class_id", UUID.class),
                        "level", rs.getString("level")),
                schoolId, req.studentId(), req.date(), req.date());
        if (enrollmentRows.isEmpty()) throw ApiException.forbidden("Aucune inscription active ne couvre cette date.");
        Map<String, Object> enrollment = enrollmentRows.getFirst();
        policy.require("ATTENDANCE_MARK", new PolicyResourceContext(schoolId,
                (UUID) enrollment.get("academic_session_id"), req.date(), ParcoursContext.get(),
                (UUID) enrollment.get("school_class_id"), null, req.studentId(), null, null, null,
                "DAILY", (String) enrollment.get("level")));
        Student student = students.findByIdAndSchoolId(req.studentId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));
        AttendanceRecord rec = repo.findBySchoolIdAndStudentIdAndDate(schoolId, req.studentId(), req.date())
                .orElseGet(AttendanceRecord::new);
        rec.setSchoolId(schoolId);
        rec.setStudentId(req.studentId());
        rec.setDate(req.date());
        rec.setStatus(req.status());
        rec.setCheckInTime(req.checkInTime());
        rec.setLateMinutes(req.lateMinutes());
        rec.setSource("manual");
        AttendanceView view = toView(repo.save(rec), student);
        realtime.broadcast(schoolId, "attendance", view);
        return view;
    }

    /** Called by the device endpoint (already authenticated by API key). */
    @Transactional
    public AttendanceView deviceCheckin(UUID deviceId, String apiKey, DeviceCheckin in) {
        Device device = devices.findByIdAndApiKeyAndActiveTrue(deviceId, apiKey)
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Périphérique non autorisé"));
        UUID schoolId = device.getSchoolId();

        // Heartbeat: stamped even for a deduplicated replay — the reader did reach us,
        // which is exactly what the "online" indicator is asking about.
        device.setLastSeenAt(OffsetDateTime.now());
        devices.save(device);

        if (in.dedupKey() != null && repo.existsByDedupKey(in.dedupKey())) {
            // Idempotent replay after a reconnection — ignore silently.
            Student s = students.findBySchoolIdAndMatriculeAndActiveTrue(schoolId, in.matricule()).orElse(null);
            return s == null ? null
                    : repo.findBySchoolIdAndStudentIdAndDate(schoolId, s.getId(), LocalDate.now())
                          .map(r -> toView(r, s)).orElse(null);
        }

        Student student = students.findBySchoolIdAndMatriculeAndActiveTrue(schoolId, in.matricule())
                .orElseThrow(() -> ApiException.notFound("Élève " + in.matricule()));

        LocalTime t = in.time() != null && !in.time().isBlank()
                ? LocalTime.parse(in.time()) : LocalTime.now();
        LocalDate today = LocalDate.now();
        // Weekends and configured holidays are never counted as late/absent scans.
        if (today.getDayOfWeek().getValue() >= 6 || schoolProfile.isHoliday(today)) {
            AttendanceRecord rec = repo.findBySchoolIdAndStudentIdAndDate(schoolId, student.getId(), today)
                    .orElseGet(AttendanceRecord::new);
            rec.setSchoolId(schoolId);
            rec.setStudentId(student.getId());
            rec.setDate(today);
            rec.setStatus("present");
            rec.setCheckInTime(String.format("%02d:%02d", t.getHour(), t.getMinute()));
            rec.setLateMinutes(0);
            rec.setSource("fingerprint");
            rec.setDedupKey(in.dedupKey());
            AttendanceView view = toView(repo.save(rec), student);
            realtime.broadcast(schoolId, "attendance", view);
            return view;
        }

        LocalTime start = schoolProfile.schoolStart();
        int lateMin = (int) Math.max(0, java.time.Duration.between(start, t).toMinutes());
        String status = lateMin > 0 ? "late" : "present";

        AttendanceRecord rec = repo.findBySchoolIdAndStudentIdAndDate(schoolId, student.getId(), today)
                .orElseGet(AttendanceRecord::new);
        rec.setSchoolId(schoolId);
        rec.setStudentId(student.getId());
        rec.setDate(today);
        rec.setStatus(status);
        rec.setCheckInTime(String.format("%02d:%02d", t.getHour(), t.getMinute()));
        rec.setLateMinutes(lateMin);
        rec.setSource("fingerprint");
        rec.setDedupKey(in.dedupKey());

        AttendanceView view = toView(repo.save(rec), student);
        realtime.broadcast(schoolId, "attendance", view);   // <- live update to the board
        return view;
    }

    private AttendanceView toView(AttendanceRecord r, Student s) {
        String name = s == null ? "—" : s.getLastName().toUpperCase() + " " + s.getFirstName();
        String matricule = s == null ? "" : s.getMatricule();
        String className = s == null ? "" : s.getClassName();
        return new AttendanceView(r.getStudentId(), matricule, name, className,
                r.getDate(), r.getStatus(), r.getCheckInTime(), r.getLateMinutes(), r.getSource());
    }

    private PolicyResourceContext boardContext(BoardSession session) {
        String period = session.periodKey() == null || session.periodKey().isBlank()
                ? ("DAILY".equalsIgnoreCase(session.model()) ? "DAILY" : null) : session.periodKey();
        return new PolicyResourceContext(TenantContext.get(), session.academicSessionId(), session.date(),
                ParcoursContext.get(), session.classId(), session.subjectCode(), null,
                session.occurrenceId(), null, null, period, session.level());
    }

    record BoardSession(UUID id, UUID academicSessionId, UUID classId, String className,
                        String level, LocalDate date, String model, String periodKey,
                        String subjectCode, UUID occurrenceId) {}
}
