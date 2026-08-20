package com.bbc.sms.guardian;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GuardianAccessServiceTest {
    @Test
    void featureFlagsAndEffectiveDateAreBothRequired() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        GuardianAccessService access = new GuardianAccessService(jdbc);
        UUID schoolId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 7);

        assertThat(access.canAccess(schoolId, userId, studentId, "academic", date)).isTrue();
        verify(jdbc).queryForObject(
                org.mockito.ArgumentMatchers.contains("sg.receives_academic=true"),
                eq(Integer.class), eq(schoolId), eq(userId), eq(studentId), eq(date), eq(date));
    }

    @Test
    void unknownParentFeatureFailsClosedWithoutBuildingDynamicSql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        GuardianAccessService access = new GuardianAccessService(jdbc);

        assertThat(access.canAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "staff_endpoint", LocalDate.now())).isFalse();
        verifyNoInteractions(jdbc);
    }
}
