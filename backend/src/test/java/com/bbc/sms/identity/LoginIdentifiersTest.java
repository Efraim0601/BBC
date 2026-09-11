package com.bbc.sms.identity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LoginIdentifiersTest {
    @Test void equivalentCameroonFormatsProduceOneIdentifier() {
        for (String input : new String[]{"600000001", "+237 600 000 001", "00237 600 000 001", "237600000001", "+237(600)000-001"}) {
            assertThat(LoginIdentifiers.phone(input)).isEqualTo("+237600000001");
            assertThat(LoginIdentifiers.candidates(input)).contains("+237600000001", "600000001", "237600000001", "00237600000001");
        }
    }
    @Test void foreignNumbersNeedTheirExplicitCountryCode() {
        assertThat(LoginIdentifiers.phone("+33 6 12 34 56 78")).isEqualTo("+33612345678");
        assertThat(LoginIdentifiers.phone("0612345678")).isNull();
    }
    @Test void malformedPhonesAreNotSilentlyAccepted() {
        for (String input : new String[]{"", "call600000001", "+000000000", "++237600000001", "123", "+1234567890123456", "123456789"}) {
            assertThat(LoginIdentifiers.phone(input)).isNull();
        }
    }
    @Test void normalizesEmailsWithoutChangingLegacyUsernames() {
        assertThat(LoginIdentifiers.email(" Teacher@Example.test ")).isEqualTo("teacher@example.test");
        assertThat(LoginIdentifiers.email("not email")).isNull();
        assertThat(LoginIdentifiers.email("a".repeat(250)+"@example.test")).isNull();
        assertThat(LoginIdentifiers.candidates(" legacy.User ")).containsExactly("legacy.User");
        assertThat(LoginIdentifiers.candidates(" ")).isEmpty();
    }
}
