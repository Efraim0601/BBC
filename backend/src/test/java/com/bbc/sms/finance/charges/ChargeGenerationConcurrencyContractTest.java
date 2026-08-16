package com.bbc.sms.finance.charges;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.charges.ChargeDtos.PreviewRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChargeGenerationConcurrencyContractTest {

    @Test
    void locksReadyGenerationKeysOnceInDeterministicOrder() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<ConnectionCallback<Void>> callbacks = new ArrayList<>();
        when(jdbc.execute(any(ConnectionCallback.class))).thenAnswer(invocation -> {
            callbacks.add(invocation.getArgument(0));
            return null;
        });

        ChargeGenerationService service = new ChargeGenerationService(
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, jdbc);
        UUID schoolId = UUID.randomUUID();
        UUID enrollmentA = UUID.randomUUID();
        UUID enrollmentB = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        List<PreviewRow> rows = List.of(
                row(enrollmentB, planId, lineId, "READY"),
                row(enrollmentA, planId, lineId, "READY"),
                row(enrollmentA, planId, lineId, "READY"),
                row(UUID.randomUUID(), planId, lineId, "BLOCKED"));

        service.lockGenerationKeys(schoolId, rows);

        assertThat(callbacks).hasSize(2);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        for (ConnectionCallback<Void> callback : callbacks) callback.doInConnection(connection);

        List<String> keys = List.of(
                schoolId + "|" + ChargeGenerationService.generationKey(rows.get(1)),
                schoolId + "|" + ChargeGenerationService.generationKey(rows.get(0)));
        keys = keys.stream().sorted().toList();
        var ordered = inOrder(statement);
        ordered.verify(statement).setString(1, keys.get(0));
        ordered.verify(statement).execute();
        ordered.verify(statement).setString(1, keys.get(1));
        ordered.verify(statement).execute();
    }

    private static PreviewRow row(UUID enrollmentId, UUID planId, UUID lineId, String status) {
        return new PreviewRow(enrollmentId, UUID.randomUUID(), "Student", "M-001", planId, 1,
                "CLASS", "PERF-001", lineId, "TUITION", "Tuition", 100, 100, 1,
                false, null, false, "NONE", null, status, null, null, null);
    }
}
