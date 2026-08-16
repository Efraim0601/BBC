package com.bbc.sms.timetable;

import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TeachingAssignmentResolverTest {
    private final UUID school = UUID.randomUUID();
    private final UUID session = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void primarySubjectsResolveTheDatedHomeroomAssignment() throws Exception {
        UUID teacher = UUID.randomUUID();
        UUID assignment = UUID.randomUUID();
        TeachingAssignmentResolver resolver = resolver("primary", List.of(row(assignment, teacher, 7L, "ACADEMIC_SETUP")));

        TeachingAssignmentResolver.Resolution result = resolver.resolve(session, classId, "MATH", java.time.LocalDate.of(2026, 9, 1));

        assertThat(result.available()).isTrue();
        assertThat(result.teacherId()).isEqualTo(teacher);
        assertThat(result.source()).isEqualTo("HOMEROOM");
        assertThat(result.assignmentId()).isEqualTo(assignment);
        assertThat(result.locked()).isTrue();
    }

    @Test
    void secondarySubjectsResolveOnlyTheResponsibleAssignment() throws Exception {
        UUID teacher = UUID.randomUUID();
        TeachingAssignmentResolver resolver = resolver("secondary", List.of(row(UUID.randomUUID(), teacher, 3L, "ACADEMIC_SETUP")));

        TeachingAssignmentResolver.Resolution result = resolver.resolve(session, classId, "ENG", java.time.LocalDate.of(2026, 9, 1));

        assertThat(result.available()).isTrue();
        assertThat(result.teacherId()).isEqualTo(teacher);
        assertThat(result.source()).isEqualTo("RESPONSIBLE");
        assertThat(result.code()).isEqualTo("ASSIGNMENT_RESOLVED");
    }

    @Test
    void missingAndDuplicateAssignmentsRemainExplicitBlockers() throws Exception {
        TeachingAssignmentResolver missing = resolver("secondary", List.of());
        TeachingAssignmentResolver.Resolution noTeacher = missing.resolve(session, classId, "SCI", java.time.LocalDate.of(2026, 9, 1));
        assertThat(noTeacher.status()).isEqualTo("MISSING");
        assertThat(noTeacher.code()).isEqualTo("RESPONSIBLE_ASSIGNMENT_MISSING");

        TeachingAssignmentResolver duplicate = resolver("secondary", List.of(
                row(UUID.randomUUID(), UUID.randomUUID(), 1L, "MANUAL"),
                row(UUID.randomUUID(), UUID.randomUUID(), 2L, "MANUAL")));
        TeachingAssignmentResolver.Resolution twoTeachers = duplicate.resolve(session, classId, "SCI", java.time.LocalDate.of(2026, 9, 1));
        assertThat(twoTeachers.status()).isEqualTo("AMBIGUOUS");
        assertThat(twoTeachers.code()).isEqualTo("RESPONSIBLE_ASSIGNMENT_AMBIGUOUS");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private TeachingAssignmentResolver resolver(String level, List<ResultSet> rows) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantContext.set(school);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            return sql.contains("count(*)") ? 1 : level;
        }).when(jdbc).queryForObject(anyString(), any(Class.class), any(Object[].class));
        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1, RowMapper.class);
            return rows.stream().map(row -> {
                try { return mapper.mapRow(row, 0); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            }).toList();
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        return new TeachingAssignmentResolver(jdbc);
    }

    private ResultSet row(UUID assignmentId, UUID teacherId, long version, String source) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getObject(1, UUID.class)).thenReturn(assignmentId);
        when(row.getObject(2, UUID.class)).thenReturn(teacherId);
        when(row.getString(3)).thenReturn("Teacher");
        when(row.getString(4)).thenReturn("T-1");
        when(row.getLong(5)).thenReturn(version);
        when(row.getString(6)).thenReturn(source);
        return row;
    }
}
