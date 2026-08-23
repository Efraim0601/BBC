package com.bbc.sms.foundation.cohort;

import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcademicCohortResolverTimetableTest {
    private final UUID schoolId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID frClassId = UUID.randomUUID();
    private final UUID enClassId = UUID.randomUUID();
    private final UUID cohortId = UUID.randomUUID();

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void sharedBilingualTimetableKeepsBothProgrammeClassesInOneOrderedScope() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantContext.set(schoolId);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            ResultSetExtractor extractor = invocation.getArgument(1, ResultSetExtractor.class);
            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(true, false);
            if (sql.contains("SELECT cohort_id")) when(rs.getObject(1, UUID.class)).thenReturn(cohortId);
            if (sql.contains("mode='SHARED_BILINGUAL'")) when(rs.getBoolean(1)).thenReturn(true);
            return extractor.extractData(rs);
        }).when(jdbc).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));
        ResultSet fr = programme(frClassId, "SIL A", "FR");
        ResultSet en = programme(enClassId, "Class 1 A", "EN");
        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1, RowMapper.class);
            return List.of(mapper.mapRow(fr, 0), mapper.mapRow(en, 1));
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        AcademicCohortResolver.TimetableScope scope =
                new AcademicCohortResolver(jdbc).timetableScope(sessionId, enClassId, "Class 1 A");

        assertThat(scope.shared()).isTrue();
        assertThat(scope.cohortId()).isEqualTo(cohortId);
        assertThat(scope.ownerClassId()).isEqualTo(frClassId);
        assertThat(scope.displayName()).isEqualTo("SIL A / Class 1 A");
        assertThat(scope.programmes()).extracting(AcademicCohortResolver.TimetableProgramme::classId)
                .containsExactly(frClassId, enClassId);
    }

    private ResultSet programme(UUID id, String name, String subsystem) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getObject(1, UUID.class)).thenReturn(id);
        when(row.getString(2)).thenReturn(name);
        when(row.getString(3)).thenReturn(subsystem);
        return row;
    }
}
