package com.bbc.sms.finance.accounting;

import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentMatchers;

import java.lang.reflect.Constructor;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentSequenceServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DocumentSequenceService sequences = new DocumentSequenceService(jdbc);

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.set(UUID.randomUUID());
        when(jdbc.queryForObject(anyString(), ArgumentMatchers.<RowMapper<Object>>any(), any(), any(), any()))
                .thenReturn(sequenceRow("JRN/2026-01/", 1L, 6));
        when(jdbc.update(anyString(), any(), any(), any(), any(), anyInt())).thenReturn(1);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void formatsAndAllocatesJournalNumberFromLockedSequenceRow() {
        assertThat(sequences.allocateJournalNumber("2026-01")).isEqualTo("JRN/2026-01/000001");
    }

    private Object sequenceRow(String prefix, long nextNumber, int padding) throws Exception {
        Class<?> type = Class.forName(DocumentSequenceService.class.getName() + "$SequenceRow");
        Constructor<?> constructor = type.getDeclaredConstructor(String.class, long.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(prefix, nextNumber, padding);
    }
}
