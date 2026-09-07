package com.bbc.sms.staff;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.*;
import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffApplicationScopeTest {
    private final StaffApplicationRepository apps = mock(StaffApplicationRepository.class);
    private final AuthorizationPolicyService policy = mock(AuthorizationPolicyService.class);
    private final ParcoursAccessService parcours = mock(ParcoursAccessService.class);
    private final StaffApplicationService service = new StaffApplicationService(apps, null, null, null, null, policy, parcours);
    @AfterEach void clear() { TenantContext.clear(); }
    private void grant(String scope, boolean global) {
        TenantContext.set(UUID.randomUUID());
        when(policy.require(anyString(), any())).thenReturn(PolicyDecision.allow("HR_VIEW", "ROLE", scope, 1));
        when(parcours.isGlobal(any())).thenReturn(global);
    }
    @Test void sectionGrantCannotExposeUnassignedApplications() {
        grant("PARCOURS_ALLOWED", false);
        assertThatThrownBy(() -> service.list(null)).isInstanceOf(ApiException.class);
        verifyNoInteractions(apps);
    }
    @Test void schoolWideRuleStillCannotEscapeAccountEnvelope() {
        grant("SCHOOL_ALL", false);
        assertThatThrownBy(() -> service.accept(UUID.randomUUID())).isInstanceOf(ApiException.class);
        verifyNoInteractions(apps);
    }
    @Test void globalHrReviewerCanListApplications() {
        grant("SCHOOL_ALL", true);
        when(apps.findBySchoolIdOrderBySubmittedAtDesc(any())).thenReturn(List.of());
        assertThat(service.list(null)).isEmpty();
        verify(apps).findBySchoolIdOrderBySubmittedAtDesc(any());
    }
}
