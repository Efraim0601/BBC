package com.bbc.sms.guardian;

import com.bbc.sms.platform.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class GuardianAccessService {
    private final JdbcTemplate jdbc;

    public GuardianAccessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UUID> childIds(UUID schoolId, UUID userId) {
        return jdbc.query("""
                SELECT sg.student_id
                 FROM guardian g
                  JOIN student_guardian sg ON sg.guardian_id=g.id
                 WHERE g.school_id=? AND g.app_user_id=? AND g.status='ACTIVE'
                   AND sg.effective_from<=current_date
                   AND (sg.effective_to IS NULL OR sg.effective_to>=current_date)
                   AND sg.portal_access=true
                 ORDER BY sg.student_id
                """, (rs, i) -> rs.getObject(1, UUID.class), schoolId, userId);
    }

    /** Server-side child + feature-flag check used by the central policy engine. */
    public boolean canAccess(UUID schoolId, UUID userId, UUID studentId, String feature) {
        return canAccess(schoolId, userId, studentId, feature, LocalDate.now());
    }

    public boolean canAccess(UUID schoolId, UUID userId, UUID studentId, String feature,
                             LocalDate effectiveDate) {
        String column = featureColumn(feature);
        if (column == null || schoolId == null || userId == null || studentId == null) return false;
        Integer n = jdbc.queryForObject("""
                SELECT count(*)
                 FROM guardian g
                  JOIN student_guardian sg ON sg.guardian_id=g.id
                 WHERE g.school_id=? AND g.app_user_id=? AND sg.student_id=?
                   AND g.status='ACTIVE'
                   AND sg.effective_from<=? AND (sg.effective_to IS NULL OR sg.effective_to>=?)
                   AND sg.portal_access=true AND sg.%s=true
                """.formatted(column), Integer.class, schoolId, userId, studentId,
                effectiveDate, effectiveDate);
        return n != null && n > 0;
    }

    public void assertAccess(UUID schoolId, UUID userId, UUID studentId, String feature) {
        if (!canAccess(schoolId, userId, studentId, feature)) {
            throw ApiException.coded(HttpStatus.FORBIDDEN,
                    "GUARDIAN_SCOPE_DENIED",
                    "Cette relation familiale n'autorise pas l'accès à cette rubrique. Contactez l'établissement.");
        }
    }

    private String featureColumn(String feature) {
        return switch (feature == null ? "" : feature) {
            case "summary", "child_summary" -> "portal_access";
            case "academic" -> "receives_academic";
            case "attendance" -> "receives_attendance";
            case "finance" -> "receives_finance";
            case "discipline" -> "receives_discipline";
            case "health" -> "receives_health";
            default -> null;
        };
    }
}
