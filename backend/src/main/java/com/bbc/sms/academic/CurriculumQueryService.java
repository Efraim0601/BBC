package com.bbc.sms.academic;

import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.TeachingAssignmentResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The single read model for the session/class curriculum used by setup,
 * grade entry, and result preparation.  It intentionally reads
 * academic_curriculum_subject rather than the legacy subject coefficient.
 */
@Service
public class CurriculumQueryService {
    private final JdbcTemplate jdbc;
    private final TeachingAssignmentResolver assignments;

    public CurriculumQueryService(JdbcTemplate jdbc, TeachingAssignmentResolver assignments) {
        this.jdbc = jdbc;
        this.assignments = assignments;
    }

    public Scope scope(UUID academicSessionId, UUID classId) {
        ClassRow classRow = jdbc.query("""
                SELECT s.id, s.code, s.label, c.name, c.subsystem
                  FROM academic_session s
                  JOIN school_class c ON c.school_id=s.school_id
                 WHERE s.id=? AND s.school_id=? AND c.id=? AND c.school_id=?
                """, rs -> rs.next()
                ? new ClassRow(rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5))
                : null, academicSessionId, TenantContext.get(), classId, TenantContext.get());
        if (classRow == null) throw ApiException.notFound("Session académique ou classe");
        return new Scope(academicSessionId, classId, classRow.sessionCode(), classRow.sessionLabel(),
                classRow.className(), classRow.subsystem(), loadSubjects(academicSessionId, classId));
    }

    public List<SubjectRow> applicable(Scope scope, AcademicReportingPeriod period) {
        LocalDate start = period.getStartDate();
        LocalDate end = period.getEndDate();
        return scope.subjects().stream()
                .filter(row -> (row.activeFrom() == null || !row.activeFrom().isAfter(end))
                        && (row.activeTo() == null || !row.activeTo().isBefore(start)))
                .map(row -> withTeacher(row, scope.academicSessionId(), scope.classId(), start))
                .toList();
    }

    private List<SubjectRow> loadSubjects(UUID sessionId, UUID classId) {
        return jdbc.query("""
                SELECT c.id, c.subject_id, s.code,
                       COALESCE(s.label->>'fr', s.label->>'en', s.code),
                       COALESCE(s.label->>'en', s.label->>'fr', s.code),
                       c.display_order, c.coefficient, c.max_score, c.mandatory,
                       c.remark_required, c.active_from, c.active_to, c.version
                  FROM academic_curriculum_subject c
                  JOIN subject s ON s.id=c.subject_id
                 WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=?
                 ORDER BY c.display_order, upper(s.code), c.id
                """, (rs, n) -> new SubjectRow(
                    rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                    rs.getString(3), rs.getString(4), rs.getString(5),
                    rs.getInt(6), rs.getInt(7), rs.getBigDecimal(8),
                    rs.getBoolean(9), rs.getBoolean(10),
                    rs.getObject(11, LocalDate.class), rs.getObject(12, LocalDate.class),
                    rs.getLong(13), null, null, null, null),
                TenantContext.get(), sessionId, classId);
    }

    private SubjectRow withTeacher(SubjectRow row, UUID sessionId, UUID classId, LocalDate date) {
        TeachingAssignmentResolver.Resolution resolution = assignments.resolve(
                sessionId, classId, row.subjectCode(), date);
        return row.withTeacher(resolution.teacherId(), resolution.teacherName(),
                resolution.status(), resolution.code());
    }

    public record Scope(UUID academicSessionId, UUID classId, String sessionCode,
                        String sessionLabel, String className, String subsystem,
                        List<SubjectRow> subjects) {
        public String contentLanguage() {
            return "EN".equalsIgnoreCase(subsystem) ? "en" : "fr";
        }
    }

    private record ClassRow(String sessionCode, String sessionLabel, String className, String subsystem) {}

    public record SubjectRow(UUID curriculumSubjectId, UUID subjectId, String subjectCode,
                             String labelFr, String labelEn, int displayOrder,
                             int coefficient, BigDecimal maxScore, boolean mandatory,
                             boolean remarkRequired, LocalDate activeFrom, LocalDate activeTo,
                             long version, UUID teacherId, String teacherName,
                             String teacherStatus, String teacherErrorCode) {
        public String label(String language) {
            return "en".equalsIgnoreCase(language)
                    ? first(labelEn, labelFr, subjectCode)
                    : first(labelFr, labelEn, subjectCode);
        }

        private static String first(String... values) {
            for (String value : values) if (value != null && !value.isBlank()) return value;
            return "";
        }

        private SubjectRow withTeacher(UUID id, String name, String status, String errorCode) {
            return new SubjectRow(curriculumSubjectId, subjectId, subjectCode, labelFr, labelEn,
                    displayOrder, coefficient, maxScore, mandatory, remarkRequired,
                    activeFrom, activeTo, version, id, name, status, errorCode);
        }
    }
}
