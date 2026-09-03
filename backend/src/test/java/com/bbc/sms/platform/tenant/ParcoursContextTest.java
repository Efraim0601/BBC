package com.bbc.sms.platform.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParcoursContextTest {

    @AfterEach
    void clear() {
        ParcoursContext.clear();
    }

    @Test
    void selectedParcoursMatchesBothLevelAndSubsystem() {
        ParcoursContext.set(new ParcoursContext.Scope("secondary", "FR"));

        assertThat(ParcoursContext.includes("secondary", "FR")).isTrue();
        assertThat(ParcoursContext.includes("secondary", "EN")).isFalse();
        assertThat(ParcoursContext.includes("primary", "FR")).isFalse();
    }

    @Test
    void sectionLockRemainsAnUpperBoundWithoutASelectedParcours() {
        ParcoursContext.lockSection("primary");

        assertThat(ParcoursContext.includes("primary", "FR")).isTrue();
        assertThat(ParcoursContext.includes("primary", "EN")).isTrue();
        assertThat(ParcoursContext.includes("secondary", "FR")).isFalse();
    }

    @Test
    void allParcoursContextDoesNotNarrowGlobalAccounts() {
        assertThat(ParcoursContext.includes("maternelle", "FR")).isTrue();
        assertThat(ParcoursContext.includes("secondary", "EN")).isTrue();
    }
}
