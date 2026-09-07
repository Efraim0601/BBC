package com.bbc.sms.settings;

import com.bbc.sms.identity.School;
import com.bbc.sms.identity.SchoolRepository;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SchoolBrandingTest {
    @Test void brandingUsesOnlyCurrentTenantAndDoesNotGrantSettingsAuthority() {
        UUID tenant=UUID.randomUUID(); TenantContext.set(tenant);
        try {
            SchoolRepository schools=mock(SchoolRepository.class);
            JdbcTemplate jdbc=mock(JdbcTemplate.class);
            AuthorizationPolicyService policy=mock(AuthorizationPolicyService.class);
            School school=new School();school.setId(tenant);school.setCode("QA");school.setName("Actual school");school.setCity("Maroua");
            when(schools.findById(tenant)).thenReturn(Optional.of(school));
            var result=new SchoolProfileService(schools,jdbc,policy).branding();
            assertThat(result.name()).isEqualTo("Actual school");
            assertThat(result.city()).isEqualTo("Maroua");
            verify(schools).findById(tenant);verifyNoInteractions(policy);
            assertThat(java.util.Arrays.stream(result.getClass().getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
                    .doesNotContain("schoolStartTime","schoolEndTime","password","smtpHost");
        } finally {TenantContext.clear();}
    }
}
