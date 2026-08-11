package com.bbc.sms.academic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectCommentPolicyTest {
    @Test
    void stripsMarkupPreservesUnicodeAndNormalizesBlankValues() {
        assertThat(SubjectCommentPolicy.sanitize("  <b>Très bien</b>\r\n\u0000  "))
                .isEqualTo("Très bien");
        assertThat(SubjectCommentPolicy.sanitize("   ")).isNull();
    }

    @Test
    void rejectsOverlongAndUnknownAppreciationValues() {
        assertThatThrownBy(() -> SubjectCommentPolicy.sanitize("x".repeat(501)))
                .hasMessageContaining("500");
        assertThatThrownBy(() -> SubjectCommentPolicy.appreciation("<script>"))
                .hasMessageContaining("autorisé");
        assertThat(SubjectCommentPolicy.appreciation(" encouragement ")).isEqualTo("ENCOURAGEMENT");
    }
}
