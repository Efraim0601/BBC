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
    void academicAccessActionsHaveExplicitFallbackRequirements() {
        assertThat(PermissionActions.CATALOG).containsKeys(
                "ACADEMIC_CLASS_RESULTS_VIEW", "ACADEMIC_REPORT_CARD_VIEW",
                "ACADEMIC_SUBJECT_GRADE_EDIT", "ACADEMIC_ASSESSMENT_MANAGE",
                "ACADEMIC_ACCESS_DELEGATE");
        assertThat(PermissionActions.CATALOG.get("ACADEMIC_CLASS_RESULTS_VIEW"))
                .isEqualTo(new PermissionActions.Requirement("academic", "read"));
        assertThat(PermissionActions.CATALOG.get("ACADEMIC_SUBJECT_GRADE_EDIT"))
                .isEqualTo(new PermissionActions.Requirement("academic", "write"));
    }
}
