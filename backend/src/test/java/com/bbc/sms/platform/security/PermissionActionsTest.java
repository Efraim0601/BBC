package com.bbc.sms.platform.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionActionsTest {
    @Test
    void journeyPromotionControllerActionsHaveExplicitFallbackRequirements() {
        assertThat(PermissionActions.CATALOG).containsKeys(
                "PROGRESSION_VIEW", "PROGRESSION_CONFIGURE", "PROMOTION_REVIEW",
                "PROMOTION_RECOMMEND", "PROMOTION_COMMIT", "PROMOTION_OVERRIDE");
        assertThat(PermissionActions.CATALOG.get("PROGRESSION_VIEW"))
                .isEqualTo(new PermissionActions.Requirement("journey", "read"));
        assertThat(PermissionActions.CATALOG.get("PROGRESSION_CONFIGURE"))
                .isEqualTo(new PermissionActions.Requirement("journey", "write"));
        assertThat(PermissionActions.CATALOG.get("PROMOTION_REVIEW"))
                .isEqualTo(new PermissionActions.Requirement("journey", "write"));
    }

    @Test
    void bay67EvidenceAndCouncilActionsHaveExplicitFallbackRequirements() {
        assertThat(PermissionActions.CATALOG).containsKeys(
                "ATTENDANCE_ADJUSTMENT_VIEW", "ATTENDANCE_ADJUSTMENT_EDIT", "ATTENDANCE_ADJUSTMENT_REVIEW",
                "COUNCIL_INPUT_VIEW", "COUNCIL_INPUT_EDIT", "COUNCIL_INPUT_REVIEW", "COUNCIL_OVERRIDE");
        assertThat(PermissionActions.CATALOG.get("ATTENDANCE_ADJUSTMENT_VIEW"))
                .isEqualTo(new PermissionActions.Requirement("presence", "read"));
        assertThat(PermissionActions.CATALOG.get("COUNCIL_OVERRIDE"))
                .isEqualTo(new PermissionActions.Requirement("academic", "write"));
    }
}
