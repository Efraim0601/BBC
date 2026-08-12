package com.bbc.sms.documents;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StandardReportTemplateProvisioningServiceTest {
    @Test
    void exposesExactlyTheFourTenantStandardFamiliesAndThreeLayouts() {
        assertThat(StandardReportTemplateProvisioningService.standardKeys())
                .containsExactly("REPORT_CARD:FR:TERM", "REPORT_CARD:FR:ANNUAL",
                        "REPORT_CARD:EN:TERM", "REPORT_CARD:EN:ANNUAL");
        assertThat(StandardReportTemplateProvisioningService.LAYOUT_LEVELS)
                .containsExactly("maternelle", "primary", "secondary");
        assertThat(StandardReportTemplateProvisioningService.standardConfig())
                .contains("\"maternelle\":\"NURSERY\"", "\"primary\":\"PRIMARY\"",
                        "\"secondary\":\"SECONDARY\"", "verificationQr", "teacherProvenance");
    }
}
