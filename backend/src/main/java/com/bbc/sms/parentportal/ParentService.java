package com.bbc.sms.parentportal;

import com.bbc.sms.academic.Grade;
import com.bbc.sms.academic.GradeRepository;
import com.bbc.sms.classkit.ClassKitService;
import com.bbc.sms.classkit.dto.ClassKitDtos.ClassResourceView;
import com.bbc.sms.parentportal.dto.ParentDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parent portal read/write logic. Owns JPA only for {@code parent_suggestion};
 * parent↔child links, fee balances and attendance are read via JdbcTemplate.
 * Student and Grade data are reached through read-only repositories.
 */
@Service
public class ParentService {

    private final JdbcTemplate jdbc;
    private final StudentRepository students;
    private final GradeRepository grades;
    private final SuggestionRepository suggestions;
    private final ClassKitService classKit;

    public ParentService(JdbcTemplate jdbc,
                         StudentRepository students,
                         GradeRepository grades,
                         SuggestionRepository suggestions,
                         ClassKitService classKit) {
        this.jdbc = jdbc;
        this.students = students;
        this.grades = grades;
        this.suggestions = suggestions;
        this.classKit = classKit;
    }

    /** Student ids linked to the given parent account. */
    List<UUID> childIds(UUID schoolId, UUID parentUserId) {
        return jdbc.query(
                "SELECT student_id FROM parent_student WHERE parent_user_id = ?",
                (rs, i) -> (UUID) rs.getObject("student_id"),
                parentUserId);
    }

    /** Guard: a parent may only ever touch one of its own children. */
    void assertOwnership(UUID schoolId, UUID parentUserId, UUID studentId) {
        if (!childIds(schoolId, parentUserId).contains(studentId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Accès refusé");
        }
    }

    public List<ChildView> children(AppUserPrincipal p) {
        UUID schoolId = p.schoolId();
        List<ChildView> out = new ArrayList<>();
        for (UUID studentId : childIds(schoolId, p.userId())) {
            Student s = students.findByIdAndSchoolId(studentId, schoolId).orElse(null);
            if (s == null) continue; // cross-tenant / inactive safety

            String name = s.getLastName().toUpperCase() + " " + s.getFirstName();

            long[] balance = {0L};
            String[] status = {"unpaid"};
            jdbc.query(
                    "SELECT balance, status FROM student_fee WHERE school_id = ? AND student_id = ?",
                    rs -> {
                        balance[0] = rs.getLong("balance");
                        status[0] = rs.getString("status");
                    },
                    schoolId, studentId);

            int attendanceRate = attendanceRate(schoolId, studentId);

            out.add(new ChildView(studentId, name, s.getClassName(), balance[0], status[0], attendanceRate));
        }
        return out;
    }

    private int attendanceRate(UUID schoolId, UUID studentId) {
        List<String> statuses = jdbc.query(
                "SELECT status FROM attendance_record WHERE school_id = ? AND student_id = ?",
                (rs, i) -> rs.getString("status"),
                schoolId, studentId);
        if (statuses.isEmpty()) return 0;
        long ok = statuses.stream().filter(st -> "present".equals(st) || "late".equals(st)).count();
        return (int) (ok * 100 / statuses.size());
    }

    public List<GradeView> grades(AppUserPrincipal p, UUID studentId) {
        assertOwnership(p.schoolId(), p.userId(), studentId);
        List<GradeView> out = new ArrayList<>();
        for (Grade g : grades.findBySchoolIdAndStudentId(p.schoolId(), studentId)) {
            out.add(new GradeView(g.getSubjectCode(), g.getSequence(), g.getMark()));
        }
        return out;
    }

    /** Published supplies/books list for the class of one of the parent's children. */
    public ClassResourceView resources(AppUserPrincipal p, UUID studentId, String kind) {
        assertOwnership(p.schoolId(), p.userId(), studentId);
        Student s = students.findByIdAndSchoolId(studentId, p.schoolId())
                .orElseThrow(() -> ApiException.notFound("Élève"));
        if (s.getClassId() == null) {
            return new ClassResourceView(null, s.getClassName(), kind, false, null, List.of());
        }
        return classKit.publishedForClass(s.getClassId(), kind);
    }

    public SuggestionView createSuggestion(AppUserPrincipal p, SuggestionRequest req) {
        ParentSuggestion ps = new ParentSuggestion();
        ps.setSchoolId(p.schoolId());
        ps.setParentUserId(p.userId());
        ps.setCategory(req.category());
        ps.setMessage(req.message());
        ps.setStatus("new");
        ParentSuggestion saved = suggestions.save(ps);
        return toView(saved);
    }

    public List<SuggestionView> mySuggestions(AppUserPrincipal p) {
        return suggestions.findBySchoolIdAndParentUserIdOrderByCreatedAtDesc(p.schoolId(), p.userId())
                .stream().map(this::toView).toList();
    }

    public List<SuggestionView> allSuggestions(UUID schoolId) {
        return suggestions.findBySchoolIdOrderByCreatedAtDesc(schoolId)
                .stream().map(this::toView).toList();
    }

    private SuggestionView toView(ParentSuggestion ps) {
        return new SuggestionView(ps.getId(), ps.getCategory(), ps.getMessage(), ps.getStatus(), ps.getCreatedAt());
    }
}
