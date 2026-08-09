package com.bbc.sms.timetable;

import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Resolves the one authoritative teacher for a session/class/subject.
 *
 * The timetable is a consumer of this service.  It never becomes a second
 * assignment registry: primary classes use the dated HOMEROOM assignment and
 * secondary classes use exactly one dated RESPONSIBLE class-subject row.
 */
@Service
public class TeachingAssignmentResolver {
    private final JdbcTemplate jdbc;

    public TeachingAssignmentResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Resolution resolve(UUID academicSessionId, UUID classId, String subjectCode, LocalDate effectiveDate) {
        UUID schoolId = TenantContext.get();
        String code = subjectCode == null ? "" : subjectCode.trim().toUpperCase(Locale.ROOT);
        if (code.isBlank()) return Resolution.missing(code, "SUBJECT_REQUIRED",
                "La matière est obligatoire.", "Subject is required.");

        Integer curriculum = jdbc.queryForObject("""
            SELECT count(*) FROM academic_curriculum_subject cs
              JOIN subject s ON s.id=cs.subject_id
             WHERE cs.school_id=? AND cs.academic_session_id=? AND cs.class_id=?
               AND upper(s.code)=upper(?) AND (cs.active_from IS NULL OR cs.active_from<=?)
               AND (cs.active_to IS NULL OR cs.active_to>=?)
            """, Integer.class, schoolId, academicSessionId, classId, code, effectiveDate, effectiveDate);
        if (curriculum == null || curriculum == 0) {
            return Resolution.missing(code, "SUBJECT_NOT_IN_CURRICULUM",
                    "La matière " + code + " n'est pas affectée à cette classe dans Configuration académique > Matières de classe.",
                    "Subject " + code + " is not assigned to this class in Academic setup > Class subjects.");
        }

        String level = jdbc.queryForObject("SELECT lower(level) FROM school_class WHERE id=? AND school_id=?",
                String.class, classId, schoolId);
        if (!"secondary".equalsIgnoreCase(level)) {
            List<AssignmentRow> rows = jdbc.query("""
                SELECT a.id,a.employee_id,e.name,e.code,a.version,a.source
                  FROM class_teacher_assignment a
                  JOIN employee e ON e.id=a.employee_id
                 WHERE a.school_id=? AND a.academic_session_id=? AND a.class_id=?
                   AND a.role='HOMEROOM' AND a.status='ACTIVE'
                   AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>=?)
                 ORDER BY a.effective_from DESC,a.created_at DESC
                """, (rs, n) -> new AssignmentRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getLong(5), rs.getString(6)),
                    schoolId, academicSessionId, classId, effectiveDate, effectiveDate);
            if (rows.size() > 1) return Resolution.ambiguous(code, "HOMEROOM_ASSIGNMENT_AMBIGUOUS",
                    "Plusieurs enseignants titulaires actifs sont affectés à cette classe.",
                    "Several active homeroom teachers are assigned to this class.");
            if (rows.isEmpty()) return Resolution.missing(code, "HOMEROOM_ASSIGNMENT_MISSING",
                    "Configurez l'enseignant titulaire avant de planifier cette classe primaire.",
                    "Configure the homeroom teacher before scheduling this primary class.");
            AssignmentRow row = rows.getFirst();
            return Resolution.ok(code, row, "HOMEROOM",
                    "Hérité de l'enseignant titulaire de la classe.",
                    "Inherited from the class homeroom teacher.");
        }

        List<AssignmentRow> rows = jdbc.query("""
            SELECT ast.id,ast.employee_id,e.name,e.code,ast.version,ast.source
              FROM academic_class_subject_teacher ast
              JOIN subject s ON s.id=ast.subject_id
              JOIN employee e ON e.id=ast.employee_id
             WHERE ast.school_id=? AND ast.academic_session_id=? AND ast.class_id=?
               AND upper(s.code)=upper(?) AND ast.role='RESPONSIBLE' AND ast.active=true
               AND (ast.effective_from IS NULL OR ast.effective_from<=?)
               AND (ast.effective_to IS NULL OR ast.effective_to>=?)
             ORDER BY ast.effective_from DESC NULLS LAST,ast.created_at DESC
            """, (rs, n) -> new AssignmentRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                    rs.getString(3), rs.getString(4), rs.getLong(5), rs.getString(6)),
                schoolId, academicSessionId, classId, code, effectiveDate, effectiveDate);
        if (rows.size() > 1) return Resolution.ambiguous(code, "RESPONSIBLE_ASSIGNMENT_AMBIGUOUS",
                "Plusieurs enseignants responsables actifs sont affectés à " + code + ".",
                "Several active responsible teachers are assigned to " + code + ".");
        if (rows.isEmpty()) return Resolution.missing(code, "RESPONSIBLE_ASSIGNMENT_MISSING",
                "Affectez un enseignant responsable pour " + code + " dans Configuration académique > Matières de classe.",
                "Assign a responsible teacher for " + code + " in Academic setup > Class subjects.");
        AssignmentRow row = rows.getFirst();
        return Resolution.ok(code, row, "RESPONSIBLE",
                "Hérité de l'enseignant responsable affecté dans Matières de classe.",
                "Inherited from the responsible teacher assigned in Class subjects.");
    }

    public record Resolution(String subjectCode, UUID teacherId, String teacherName, String teacherCode,
                             UUID assignmentId, long assignmentVersion, String source, String status,
                             String code, String messageFr, String messageEn, boolean locked) {
        public boolean available() { return "RESOLVED".equals(status) && teacherId != null; }

        private static Resolution ok(String code, AssignmentRow row, String source, String fr, String en) {
            return new Resolution(code, row.teacherId(), row.teacherName(), row.teacherCode(), row.id(),
                    row.version(), source, "RESOLVED", "ASSIGNMENT_RESOLVED", fr, en, true);
        }

        private static Resolution missing(String code, String errorCode, String fr, String en) {
            return new Resolution(code, null, null, null, null, 0, null, "MISSING", errorCode, fr, en, true);
        }

        private static Resolution ambiguous(String code, String errorCode, String fr, String en) {
            return new Resolution(code, null, null, null, null, 0, null, "AMBIGUOUS", errorCode, fr, en, true);
        }
    }

    private record AssignmentRow(UUID id, UUID teacherId, String teacherName, String teacherCode,
                                 long version, String source) {}
}
