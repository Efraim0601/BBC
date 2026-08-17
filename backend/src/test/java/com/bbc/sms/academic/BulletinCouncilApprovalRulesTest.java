package com.bbc.sms.academic;

import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BulletinCouncilApprovalRulesTest {

    @Test
    void sequenceReportCardsDoNotRequireClassCouncilApproval() {
        assertThat(BulletinSnapshotService.requiresCouncilApproval(period("SEQUENCE"))).isFalse();
    }

    @Test
    void aggregateResultsRequireClassCouncilApproval() {
        assertThat(BulletinSnapshotService.requiresCouncilApproval(period("TERM_RESULT"))).isTrue();
        assertThat(BulletinSnapshotService.requiresCouncilApproval(period("ANNUAL_RESULT"))).isTrue();
    }

    private AcademicReportingPeriod period(String type) {
        AcademicReportingPeriod period = new AcademicReportingPeriod();
        period.setPeriodType(type);
        return period;
    }
}
