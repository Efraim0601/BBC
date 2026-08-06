package com.bbc.sms.foundation.calendar;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static com.bbc.sms.foundation.calendar.CalendarDtos.*;

@Service
public class SchoolCalendarService {
    private final JdbcTemplate jdbc;
    private final AcademicSessionRepository sessions;
    private final SchoolClassRepository classes;
    private final AuditService audit;

    public SchoolCalendarService(JdbcTemplate jdbc, AcademicSessionRepository sessions,
                                 SchoolClassRepository classes, AuditService audit) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.classes = classes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CalendarDayView> days(UUID sessionId) {
        findSession(sessionId);
        return jdbc.query("""
            SELECT id, academic_session_id, day_of_week, teaching_day, start_time, end_time, version
            FROM school_calendar_day WHERE school_id=? AND academic_session_id=? ORDER BY day_of_week
            """, (rs, i) -> new CalendarDayView(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                rs.getInt(3), rs.getBoolean(4), rs.getObject(5, LocalTime.class),
                rs.getObject(6, LocalTime.class), rs.getLong(7)), TenantContext.get(), sessionId);
    }

    @Transactional
    public CalendarDayView updateDay(UUID sessionId, CalendarDayUpdate in) {
        AcademicSession session = findSession(sessionId);
        if (List.of("CLOSED", "ARCHIVED").contains(session.getStatus())) throw ApiException.conflict("Calendrier en lecture seule");
        if (in.dayOfWeek() < 1 || in.dayOfWeek() > 7) throw ApiException.badRequest("Jour de semaine invalide");
        if (in.teachingDay() && (in.startTime() == null || in.endTime() == null || !in.startTime().isBefore(in.endTime()))) {
            throw ApiException.badRequest("Les horaires d’un jour de classe sont invalides");
        }
        List<CalendarDayView> old = days(sessionId).stream().filter(d -> d.dayOfWeek() == in.dayOfWeek()).toList();
        if (!old.isEmpty() && in.version() != null && old.get(0).version() != in.version()) throw ApiException.conflict("Jour modifié par un autre utilisateur");
        UUID id = old.isEmpty() ? UUID.randomUUID() : old.get(0).id();
        jdbc.update("""
            INSERT INTO school_calendar_day
            (id,school_id,academic_session_id,day_of_week,teaching_day,start_time,end_time)
            VALUES (?,?,?,?,?,?,?)
            ON CONFLICT (school_id,academic_session_id,day_of_week) DO UPDATE SET
              teaching_day=excluded.teaching_day,start_time=excluded.start_time,
              end_time=excluded.end_time,version=school_calendar_day.version+1
            """, id, TenantContext.get(), sessionId, in.dayOfWeek(), in.teachingDay(), in.startTime(), in.endTime());
        CalendarDayView result = days(sessionId).stream().filter(d -> d.dayOfWeek() == in.dayOfWeek()).findFirst().orElseThrow();
        audit.record("CALENDAR_DAY_UPDATED", "AcademicSession", sessionId.toString(), old.isEmpty() ? null : old.get(0), result, null);
        return result;
    }

    @Transactional
    public GenerationResult generate(GenerateRequest in) {
        AcademicSession session = findSession(in.academicSessionId());
        if (!in.dryRun() && List.of("CLOSED", "ARCHIVED").contains(session.getStatus())) {
            throw ApiException.conflict("Cette session est en lecture seule : utilisez la prévisualisation sans régénérer les données");
        }
        LocalDate start = in.startDate() == null ? session.getStartDate() : in.startDate();
        LocalDate end = in.endDate() == null ? session.getEndDate() : in.endDate();
        if (start.isBefore(session.getStartDate()) || end.isAfter(session.getEndDate()) || start.isAfter(end)) {
            throw ApiException.badRequest("Plage de génération hors session");
        }
        Map<Integer, CalendarDayView> config = new HashMap<>();
        days(session.getId()).forEach(d -> config.put(d.dayOfWeek(), d));
        if (config.isEmpty()) throw ApiException.conflict("Configurez les jours de classe avant la génération");
        Set<LocalDate> holidays = new HashSet<>(jdbc.query(
                "SELECT holiday_date FROM school_holiday WHERE school_id=? AND holiday_date BETWEEN ? AND ?",
                (rs, i) -> rs.getObject(1, LocalDate.class), TenantContext.get(), start, end));
        List<LocalDate> teachingDates = start.datesUntil(end.plusDays(1))
                .filter(d -> Optional.ofNullable(config.get(d.getDayOfWeek().getValue())).map(CalendarDayView::teachingDay).orElse(false))
                .filter(d -> !holidays.contains(d)).toList();
        List<SchoolClass> schoolClasses = classes.findBySchoolIdOrderByName(TenantContext.get());
        String version = sourceVersion(session, config.values(), holidays);
        Integer existing = jdbc.queryForObject("""
                SELECT count(*) FROM expected_school_session
                WHERE school_id=? AND academic_session_id=? AND session_date BETWEEN ? AND ?
                """, Integer.class, TenantContext.get(), session.getId(), start, end);
        int expected = teachingDates.size() * schoolClasses.size();
        if (in.dryRun()) return new GenerationResult(session.getId(), start, end, teachingDates.size(),
                schoolClasses.size(), expected, existing == null ? 0 : existing, 0, 0, version, true, List.of());

        int removed = jdbc.update("""
                DELETE FROM expected_school_session e
                WHERE e.school_id=? AND e.academic_session_id=? AND e.session_date BETWEEN ? AND ?
                  AND e.session_date>CURRENT_DATE
                  AND NOT EXISTS (
                    SELECT 1 FROM attendance_record ar JOIN student st ON st.id=ar.student_id
                    WHERE ar.school_id=e.school_id AND ar.att_date=e.session_date
                      AND st.class_id=e.school_class_id)
                """, TenantContext.get(), session.getId(), start, end);
        int inserted = 0;
        for (LocalDate date : teachingDates) {
            for (SchoolClass cls : schoolClasses) {
                inserted += jdbc.update("""
                    INSERT INTO expected_school_session
                    (school_id,academic_session_id,school_class_id,session_date,model,period_key,source,source_version)
                    VALUES (?,?,?,?, 'DAILY','DAILY','CALENDAR',?)
                    ON CONFLICT (school_id,academic_session_id,school_class_id,session_date,period_key)
                    DO UPDATE SET source_version=excluded.source_version,generated_at=now(),cancelled=false,closure_reason=null
                    """, TenantContext.get(), session.getId(), cls.getId(), date, version);
            }
        }
        GenerationResult result = new GenerationResult(session.getId(), start, end, teachingDates.size(),
                schoolClasses.size(), expected, existing == null ? 0 : existing, inserted, removed, version, false,
                LocalDate.now().isAfter(end) ? List.of("La plage est historique; les présences existantes ont été préservées.") : List.of());
        audit.record("EXPECTED_SESSIONS_GENERATED", "AcademicSession", session.getId().toString(), null, result, null);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ExpectedSessionView> expected(UUID sessionId, LocalDate start, LocalDate end, UUID classId) {
        AcademicSession session = findSession(sessionId);
        LocalDate from = start == null ? session.getStartDate() : start;
        LocalDate to = end == null ? session.getEndDate() : end;
        String sql = """
            SELECT id,academic_session_id,school_class_id,session_date,model,period_key,source_version,cancelled,closure_reason
            FROM expected_school_session WHERE school_id=? AND academic_session_id=? AND session_date BETWEEN ? AND ?
            """ + (classId == null ? "" : " AND school_class_id=?") + " ORDER BY session_date,school_class_id";
        Object[] args = classId == null ? new Object[]{TenantContext.get(), sessionId, from, to}
                : new Object[]{TenantContext.get(), sessionId, from, to, classId};
        return jdbc.query(sql, (rs, i) -> new ExpectedSessionView(UUID.fromString(rs.getString(1)),
                UUID.fromString(rs.getString(2)), UUID.fromString(rs.getString(3)), rs.getObject(4, LocalDate.class),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getBoolean(8), rs.getString(9)), args);
    }

    private AcademicSession findSession(UUID id) {
        return sessions.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Session académique"));
    }
    private static String sourceVersion(AcademicSession session, Collection<CalendarDayView> config, Set<LocalDate> holidays) {
        String raw = session.getId() + "|" + session.getVersion() + "|" + config + "|" + holidays.stream().sorted().toList();
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))).substring(0, 32); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
