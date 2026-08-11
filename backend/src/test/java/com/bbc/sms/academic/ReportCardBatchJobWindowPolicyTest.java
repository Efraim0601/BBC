package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.BulletinBatchJobCreateRequest;
import com.bbc.sms.academic.dto.AcademicDtos.BulletinBatchRepairTarget;
import com.bbc.sms.academic.dto.AcademicDtos.BulletinBatchWindowView;
import com.bbc.sms.documents.DocumentStorage;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.session.AcademicWindowPolicyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportCardBatchJobWindowPolicyTest {
    private final UUID schoolId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID periodId = UUID.randomUUID();

    @Mock JdbcTemplate jdbc;
    @Mock TeacherScopeService teacherScope;
    @Mock ReportCardBatchEligibilityService eligibility;
    @Mock ReportCardBatchJobWorker worker;
    @Mock DocumentStorage storage;
    @Mock ObjectMapper mapper;
    @Mock AuditService audit;
    @Mock AcademicWindowPolicyService windows;

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void createRechecksTheCurrentTrimesterWindowBeforePersistingAJob() {
        TenantContext.set(schoolId);
        Instant now = Instant.parse("2026-09-15T10:00:00Z");
        BulletinBatchWindowView closed = new BulletinBatchWindowView("CLOSED", false, "T1", "Trimester 1",
                List.of("S1", "S2", "T1_RESULT"), "Africa/Douala", now, null,
                Instant.parse("2026-09-01T00:00:00Z"), null,
                new BulletinBatchRepairTarget("/settings", Map.of("tab", "sessions")));
        when(eligibility.preview(classId, periodId, "en"))
                .thenReturn(new ReportCardBatchEligibilityService.EligibilityPreview(
                        "PUBLISHED_ONLY", "PUBLISHED_ONLY_V1", UUID.randomUUID(), "2026-2027", classId,
                        "4eme A", periodId, "S1", "Sequence 1", List.of(), "fingerprint", now, "en", closed));
        doThrow(ApiException.conflictWithDetails("TRIMESTER_WINDOW_CLOSED", "T1 is closed.",
                Map.of("governingTrimester", "T1", "affectedMilestones", List.of("S1", "S2", "T1_RESULT"))))
                .when(windows).assertAllowed(periodId, AcademicWindowPolicyService.Action.BATCH_GENERATION);

        ReportCardBatchJobService service = new ReportCardBatchJobService(jdbc, teacherScope, eligibility,
                worker, storage, mapper, audit, windows);

        assertThatThrownBy(() -> service.create(new BulletinBatchJobCreateRequest(classId, periodId, "en")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((ApiException) error).getCode())
                        .isEqualTo("TRIMESTER_WINDOW_CLOSED"));
        verifyNoInteractions(jdbc);
    }
}
