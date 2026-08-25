package com.bbc.sms.platform.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * Resolves the two attendance authorities explicitly instead of treating all
 * teacher attendance as a timetable problem.  Primary/maternelle attendance
 * is a dated DAILY roster owned by the active titulaire; secondary attendance
 * is a dated, published timetable occurrence owned by the responsible teacher
 * or an approved substitute.
 */
@Service
public class AttendanceScopeResolver {
    private final JdbcTemplate jdbc;

    public AttendanceScopeResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasRequiredContext(PolicyResourceContext context) {
        if (context == null || context.schoolId() == null || context.academicSessionId() == null
                || context.effectiveDate() == null || context.classId() == null) return false;
        if (primaryOrMaternelle(context.schoolId(), context.classId())) {
            return context.timetableOccurrenceId() == null
                    && (context.subjectCode() == null || context.subjectCode().isBlank())
                    && context.periodKey() != null
                    && "DAILY".equalsIgnoreCase(context.periodKey().trim());
        }
        return context.timetableOccurrenceId() != null
                && context.subjectCode() != null && !context.subjectCode().isBlank()
                && context.periodKey() != null && !context.periodKey().isBlank();
    }

    public boolean allowsTeacher(AppUserPrincipal principal, PolicyResourceContext context,
                                 String configuredScopeMode) {
        if (!hasRequiredContext(context) || principal == null) return false;
        String scope = configuredScopeMode == null ? "" : configuredScopeMode.toUpperCase(Locale.ROOT);
        UUID employeeId = employeeId(principal, context.schoolId());
        if (employeeId == null) return false;
        if (primaryOrMaternelle(context.schoolId(), context.classId())) {
            return ("TITULAIRE_CLASSES".equals(scope) || "ASSIGNED_CLASSES".equals(scope))
                    && datedTitulaire(context, employeeId);
        }
        if ("TITULAIRE_CLASSES".equals(scope)) return datedTitulaire(context, employeeId);
        return ("TIMETABLE_OCCURRENCES_ASSIGNED".equals(scope) || "ASSIGNED_CLASSES".equals(scope))
                && publishedOccurrence(context, employeeId);
    }

    public boolean publishedOccurrenceAssigned(AppUserPrincipal principal,
                                               PolicyResourceContext context) {
        if (principal == null || context == null || context.schoolId() == null) return false;
        UUID employeeId = employeeId(principal, context.schoolId());
        return employeeId != null && hasRequiredContext(context)
                && !primaryOrMaternelle(context.schoolId(), context.classId())
                && publishedOccurrence(context, employeeId);
    }

    /** Own timetable validation used for the SELF action. */
    public boolean ownPublishedSchedule(AppUserPrincipal principal, PolicyResourceContext context) {
        if (principal == null || context == null || context.schoolId() == null) return false;
        UUID employeeId = employeeId(principal, context.schoolId());
        if (employeeId == null) return false;
        if (context.timetableOccurrenceId() != null) {
            return context.academicSessionId() != null && context.effectiveDate() != null
                    && context.classId() != null && context.subjectCode() != null
                    && context.periodKey() != null
                    && publishedOccurrence(context, employeeId);
        }
        // Viewing one's schedule is safe even when it is empty. Requiring an
        // existing published slot turns "no lessons" into a misleading 403 for
        // newly assigned Titulaires and teachers awaiting publication.
        return context.ownerEmployeeId() != null && context.ownerEmployeeId().equals(employeeId);
    }

    private boolean datedTitulaire(PolicyResourceContext context, UUID employeeId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                  FROM class_teacher_assignment a
                 WHERE a.school_id=? AND a.academic_session_id=?
                   AND (a.class_id=? OR EXISTS (
                       SELECT 1
                         FROM academic_cohort_programme requested
                         JOIN academic_cohort h
                           ON h.id=requested.cohort_id
                          AND h.school_id=requested.school_id
                          AND h.academic_session_id=requested.academic_session_id
                         JOIN academic_cohort_programme assigned
                           ON assigned.school_id=requested.school_id
                          AND assigned.academic_session_id=requested.academic_session_id
                          AND assigned.cohort_id=requested.cohort_id
                          AND assigned.school_class_id=a.class_id
                          AND assigned.active
                        WHERE requested.school_id=?
                          AND requested.academic_session_id=?
                          AND requested.school_class_id=?
                          AND requested.active
                          AND h.status='ACTIVE' AND h.mode='SHARED_BILINGUAL'))
                   AND a.employee_id=? AND a.role='HOMEROOM' AND a.status='ACTIVE'
                   AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>=?)
                """, Integer.class, context.schoolId(), context.academicSessionId(), context.classId(),
                context.schoolId(), context.academicSessionId(), context.classId(), employeeId,
                context.effectiveDate(), context.effectiveDate());
        return count != null && count > 0;
    }

    /**
     * Bind every occurrence dimension.  A substitution is joined to the same
     * published version/cell and replaces, rather than supplements, the
     * published responsible teacher for that date.
     */
    private boolean publishedOccurrence(PolicyResourceContext context, UUID employeeId) {
        LocalDate date = context.effectiveDate();
        int dayIndex = date.getDayOfWeek().getValue() - 1;
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                  FROM timetable_slot s
                  JOIN timetable_version v
                    ON v.id=s.timetable_version_id
                   AND v.school_id=s.school_id
                   AND v.academic_session_id=s.academic_session_id
                   AND v.status='PUBLISHED'
                   AND v.effective_from<=?
                   AND (v.effective_to IS NULL OR v.effective_to>=?)
                  JOIN school_class c ON c.id=s.class_id AND c.school_id=s.school_id
                  LEFT JOIN timetable_period p
                    ON p.school_id=s.school_id AND p.slot_idx=s.slot_idx AND p.active
                  LEFT JOIN timetable_substitution sub
                    ON sub.school_id=s.school_id
                   AND sub.academic_session_id=s.academic_session_id
                   AND sub.timetable_version_id=v.id
                   AND sub.occurrence_date=?
                   AND sub.class_id=s.class_id
                   AND sub.day_idx=s.day_idx AND sub.slot_idx=s.slot_idx
                   AND sub.status='APPROVED' AND sub.action='SUBSTITUTE'
                   AND (sub.subject_code IS NULL OR upper(sub.subject_code)=upper(s.subject_code))
                   AND (sub.original_teacher_id IS NULL
                        OR sub.original_teacher_id=coalesce(s.published_teacher_id,s.teacher_id))
                 WHERE s.school_id=? AND s.id=? AND s.academic_session_id=?
                   AND s.class_id=? AND s.day_idx=?
                   AND upper(coalesce(s.subject_code,''))=upper(?)
                   AND (upper(coalesce(p.label,''))=upper(?)
                        OR upper('P'||(s.slot_idx+1)::text)=upper(?)
                        OR s.slot_idx::text=? )
                   AND CASE WHEN sub.id IS NULL
                            THEN coalesce(s.published_teacher_id,s.teacher_id)
                            ELSE sub.replacement_teacher_id END=?
                """, Integer.class,
                date, date, date,
                context.schoolId(), context.timetableOccurrenceId(), context.academicSessionId(),
                context.classId(), dayIndex, context.subjectCode(), context.periodKey(),
                context.periodKey(), context.periodKey(), employeeId);
        return count != null && count > 0;
    }

    private boolean primaryOrMaternelle(UUID schoolId, UUID classId) {
        String level = jdbc.query("""
                SELECT lower(level) FROM school_class WHERE school_id=? AND id=?
                """, rs -> rs.next() ? rs.getString(1) : null, schoolId, classId);
        return "primary".equals(level) || "maternelle".equals(level);
    }

    private UUID employeeId(AppUserPrincipal principal, UUID schoolId) {
        return jdbc.query("""
                SELECT employee_id FROM app_user
                 WHERE id=? AND school_id=? AND active=true
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                principal.userId(), schoolId);
    }

}
