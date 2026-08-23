package com.bbc.sms.foundation.cohort;

import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Central compatibility resolver for class-group rosters.  An ordinary class
 * still resolves to its own enrollment rows; a paired programme class resolves
 * to the one shared cohort enrollment set.
 */
@Service
public class AcademicCohortResolver {
    private final JdbcTemplate jdbc;

    public AcademicCohortResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UUID cohortForClass(UUID sessionId, UUID classId) {
        if (sessionId == null || classId == null) return null;
        return jdbc.query("""
                SELECT cohort_id FROM academic_cohort_programme
                 WHERE school_id=? AND academic_session_id=? AND school_class_id=? AND active
                LIMIT 1
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), sessionId, classId);
    }

    public List<UUID> rosterStudentIds(UUID sessionId, UUID classId, String status) {
        UUID cohortId = cohortForClass(sessionId, classId);
        if (cohortId == null) return jdbc.query("""
                SELECT student_id FROM student_enrollment
                 WHERE school_id=? AND academic_session_id=? AND school_class_id=? AND status=?
                ORDER BY created_at, student_id
                """, (rs, n) -> rs.getObject(1, UUID.class),
                TenantContext.get(), sessionId, classId, status);
        return jdbc.query("""
                SELECT student_id FROM student_enrollment
                 WHERE school_id=? AND academic_session_id=? AND cohort_id=? AND status=?
                ORDER BY created_at, student_id
                """, (rs, n) -> rs.getObject(1, UUID.class),
                TenantContext.get(), sessionId, cohortId, status);
    }

    /**
     * Same resolver as {@link #rosterStudentIds(UUID, UUID, String)}, but with
     * the effective-date bounds used by academic and attendance workflows.
     * A future planned enrollment must not appear in a roster before its
     * planned/enrolled date, and a dated historical roster must respect exits.
     */
    public List<UUID> rosterStudentIds(UUID sessionId, UUID classId, String status,
                                       java.time.LocalDate effectiveDate) {
        UUID cohortId = cohortForClass(sessionId, classId);
        String scope = cohortId == null
                ? "e.school_class_id=?"
                : "e.cohort_id=?";
        UUID membership = cohortId == null ? classId : cohortId;
        String dateClause = effectiveDate == null ? "" :
                " AND e.enrolled_on<=? AND (e.exited_on IS NULL OR e.exited_on>=?)";
        List<Object> args = new java.util.ArrayList<>(List.of(
                TenantContext.get(), sessionId, membership, status));
        if (effectiveDate != null) args.addAll(List.of(effectiveDate, effectiveDate));
        String sql = "SELECT e.student_id FROM student_enrollment e "
                + "WHERE e.school_id=? AND e.academic_session_id=? AND " + scope
                + " AND e.status=?" + dateClause
                + " ORDER BY e.created_at, e.student_id";
        return jdbc.query(sql, (rs, n) -> rs.getObject(1, UUID.class), args.toArray());
    }

    public boolean studentBelongsToClass(UUID sessionId, UUID classId, UUID studentId,
                                         String status) {
        return studentBelongsToClass(sessionId, classId, studentId, status, null);
    }

    public boolean studentBelongsToClass(UUID sessionId, UUID classId, UUID studentId,
                                         String status, java.time.LocalDate effectiveDate) {
        UUID cohortId = cohortForClass(sessionId, classId);
        String scope = cohortId == null ? "school_class_id=?" : "cohort_id=?";
        UUID membership = cohortId == null ? classId : cohortId;
        String dateClause = effectiveDate == null ? "" :
                " AND enrolled_on<=? AND (exited_on IS NULL OR exited_on>=?)";
        List<Object> args = new java.util.ArrayList<>(List.of(
                TenantContext.get(), sessionId, membership, studentId, status));
        if (effectiveDate != null) args.addAll(List.of(effectiveDate, effectiveDate));
        Integer count = cohortId == null
                ? jdbc.queryForObject("SELECT count(*) FROM student_enrollment WHERE school_id=? AND academic_session_id=? AND " + scope + " AND student_id=? AND status=?" + dateClause,
                    Integer.class, args.toArray())
                : jdbc.queryForObject("SELECT count(*) FROM student_enrollment WHERE school_id=? AND academic_session_id=? AND " + scope + " AND student_id=? AND status=?" + dateClause,
                    Integer.class, args.toArray());
        return count != null && count > 0;
    }

    public int rosterCount(UUID sessionId, UUID classId, String status) {
        return rosterStudentIds(sessionId, classId, status).size();
    }

    public UUID preferredClassForCohort(UUID sessionId, UUID cohortId) {
        return jdbc.query("""
                SELECT school_class_id FROM academic_cohort_programme
                 WHERE school_id=? AND academic_session_id=? AND cohort_id=? AND active
                 ORDER BY display_order, subsystem
                 LIMIT 1
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.get(), sessionId, cohortId);
    }

    /** The class used to represent a shared daily roster in attendance. */
    public UUID attendanceClass(UUID sessionId, UUID classId) {
        UUID cohortId = cohortForClass(sessionId, classId);
        if (cohortId == null || !isSharedBilingual(sessionId, cohortId)) return classId;
        return preferredClassForCohort(sessionId, cohortId);
    }

    /** Friendly programme pair label for selectors that represent one roster. */
    public String displayName(UUID sessionId, UUID classId, String fallback) {
        UUID cohortId = cohortForClass(sessionId, classId);
        if (cohortId == null || !isSharedBilingual(sessionId, cohortId)) return fallback;
        String label = jdbc.query("""
                SELECT string_agg(c.name || ' (' || p.subsystem || ')', ' · '
                                  ORDER BY p.display_order,p.subsystem)
                  FROM academic_cohort_programme p JOIN school_class c ON c.id=p.school_class_id
                 WHERE p.school_id=? AND p.academic_session_id=? AND p.cohort_id=? AND p.active
                """, rs -> rs.next() ? rs.getString(1) : null,
                TenantContext.get(), sessionId, cohortId);
        return label == null || label.isBlank() ? fallback : label;
    }

    public boolean isSharedBilingual(UUID sessionId, UUID cohortId) {
        Boolean shared = jdbc.query("""
                SELECT mode='SHARED_BILINGUAL' FROM academic_cohort
                 WHERE school_id=? AND academic_session_id=? AND id=?
                """, rs -> rs.next() ? rs.getBoolean(1) : false,
                TenantContext.get(), sessionId, cohortId);
        return Boolean.TRUE.equals(shared);
    }

    /**
     * Timetable scope for a class. A shared bilingual cohort owns one physical
     * weekly grid, while every returned programme class keeps its own subjects
     * and teacher assignment.
     */
    public TimetableScope timetableScope(UUID sessionId, UUID classId, String fallbackName) {
        UUID cohortId = cohortForClass(sessionId, classId);
        if (cohortId == null || !isSharedBilingual(sessionId, cohortId)) {
            return new TimetableScope(null, classId, fallbackName, false,
                    List.of(new TimetableProgramme(classId, fallbackName, null)));
        }
        List<TimetableProgramme> programmes = jdbc.query("""
                SELECT p.school_class_id,c.name,p.subsystem
                  FROM academic_cohort_programme p
                  JOIN school_class c ON c.id=p.school_class_id AND c.school_id=p.school_id
                 WHERE p.school_id=? AND p.academic_session_id=? AND p.cohort_id=? AND p.active
                 ORDER BY p.display_order,p.subsystem,c.name
                """, (rs, n) -> new TimetableProgramme(rs.getObject(1, UUID.class),
                        rs.getString(2), rs.getString(3)),
                TenantContext.get(), sessionId, cohortId);
        if (programmes.size() < 2) {
            return new TimetableScope(null, classId, fallbackName, false,
                    List.of(new TimetableProgramme(classId, fallbackName, null)));
        }
        String displayName = programmes.stream().map(TimetableProgramme::className)
                .collect(java.util.stream.Collectors.joining(" / "));
        return new TimetableScope(cohortId, programmes.getFirst().classId(), displayName, true, programmes);
    }

    public List<UUID> timetableClassIds(UUID sessionId, UUID classId, String fallbackName) {
        return timetableScope(sessionId, classId, fallbackName).programmes().stream()
                .map(TimetableProgramme::classId).toList();
    }

    public record TimetableProgramme(UUID classId, String className, String subsystem) {}
    public record TimetableScope(UUID cohortId, UUID ownerClassId, String displayName,
                                 boolean shared, List<TimetableProgramme> programmes) {}
}
