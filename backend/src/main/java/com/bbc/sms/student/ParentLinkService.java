package com.bbc.sms.student;

import com.bbc.sms.identity.AppUser;
import com.bbc.sms.identity.AppUserRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.dto.StudentDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Turns the old free-text "Parent" field into real, login-capable accounts
 * (review issue #2). A parent is an {@code app_user} with the {@code parent}
 * role, linked to one or more students through {@code parent_student} — which
 * is exactly what the parent portal already reads.
 */
@Service
public class ParentLinkService {

    private final StudentRepository students;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    public ParentLinkService(StudentRepository students, AppUserRepository users,
                             PasswordEncoder encoder, JdbcTemplate jdbc) {
        this.students = students;
        this.users = users;
        this.encoder = encoder;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ParentAccountView> list(UUID studentId) {
        UUID schoolId = TenantContext.get();
        requireStudent(schoolId, studentId);
        return jdbc.query(
                "SELECT u.id, u.display_name, u.username, u.active, "
              + "  (SELECT count(*) FROM parent_student ps2 WHERE ps2.parent_user_id = u.id) AS child_count "
              + "FROM parent_student ps JOIN app_user u ON u.id = ps.parent_user_id "
              + "WHERE ps.student_id = ? AND u.school_id = ? ORDER BY u.display_name",
                (rs, i) -> new ParentAccountView(
                        (UUID) rs.getObject("id"),
                        rs.getString("display_name"),
                        rs.getString("username"),
                        rs.getBoolean("active"),
                        rs.getInt("child_count")),
                studentId, schoolId);
    }

    /** Create a parent account (or link an existing one by username) to this student. */
    @Transactional
    public ParentAccountView link(UUID studentId, ParentLinkRequest req) {
        UUID schoolId = TenantContext.get();
        Student student = requireStudent(schoolId, studentId);
        String username = req.username().trim().toLowerCase();

        AppUser parent = users.findBySchoolIdAndUsernameAndActiveTrue(schoolId, username).orElse(null);
        if (parent == null) {
            if (req.password() == null || req.password().isBlank()) {
                throw ApiException.badRequest("Un mot de passe est requis pour créer le compte parent");
            }
            parent = new AppUser();
            parent.setSchoolId(schoolId);
            parent.setUsername(username);
            parent.setPasswordHash(encoder.encode(req.password()));
            parent.setDisplayName(req.displayName().trim());
            parent.setInitials(initialsOf(req.displayName()));
            parent.setRoleCode("parent");
            // Flush now: the raw parent_student INSERT below references this row's FK,
            // so the app_user INSERT must hit the DB before it within this transaction.
            parent = users.saveAndFlush(parent);
        } else if (!"parent".equals(parent.getRoleCode())) {
            throw ApiException.conflict("Cet identifiant appartient déjà à un compte non-parent");
        }

        Integer existing = jdbc.queryForObject(
                "SELECT count(*) FROM parent_student WHERE parent_user_id = ? AND student_id = ?",
                Integer.class, parent.getId(), studentId);
        if (existing == null || existing == 0) {
            jdbc.update("INSERT INTO parent_student (parent_user_id, student_id) VALUES (?, ?)",
                    parent.getId(), studentId);
        }

        // Keep the legacy display field populated for screens that still read it.
        if (student.getParentName() == null || student.getParentName().isBlank()) {
            student.setParentName(parent.getDisplayName());
            students.save(student);
        }

        int childCount = jdbc.queryForObject(
                "SELECT count(*) FROM parent_student WHERE parent_user_id = ?",
                Integer.class, parent.getId());
        return new ParentAccountView(parent.getId(), parent.getDisplayName(),
                parent.getUsername(), parent.isActive(), childCount);
    }

    /** Unlink a parent from a student; deactivate the account once it has no children left. */
    @Transactional
    public void unlink(UUID studentId, UUID parentUserId) {
        UUID schoolId = TenantContext.get();
        requireStudent(schoolId, studentId);
        jdbc.update("DELETE FROM parent_student WHERE parent_user_id = ? AND student_id = ?",
                parentUserId, studentId);
        Integer remaining = jdbc.queryForObject(
                "SELECT count(*) FROM parent_student WHERE parent_user_id = ?",
                Integer.class, parentUserId);
        if (remaining != null && remaining == 0) {
            users.findById(parentUserId)
                    .filter(u -> schoolId.equals(u.getSchoolId()) && "parent".equals(u.getRoleCode()))
                    .ifPresent(u -> { u.setActive(false); users.save(u); });
        }
    }

    private Student requireStudent(UUID schoolId, UUID studentId) {
        return students.findByIdAndSchoolId(studentId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));
    }

    private String initialsOf(String name) {
        String[] p = name.trim().split("\\s+");
        String first = p.length > 0 && !p[0].isEmpty() ? p[0].substring(0, 1) : "";
        String last = p.length > 1 && !p[p.length - 1].isEmpty() ? p[p.length - 1].substring(0, 1) : "";
        String s = (first + last).toUpperCase();
        return s.isEmpty() ? "PA" : s;
    }
}
