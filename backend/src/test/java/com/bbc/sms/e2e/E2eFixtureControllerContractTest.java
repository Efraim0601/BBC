package com.bbc.sms.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/** Contract guard for the test-only second-tenant fixture route. */
class E2eFixtureControllerContractTest {

    @Test
    void productionProfileDoesNotRegisterTheFixtureController() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod");
            context.register(E2eFixtureController.class);
            context.refresh();

            assertThat(context.getBeansOfType(E2eFixtureController.class)).isEmpty();
        }
    }

    @Test
    void productionProfileWinsIfE2eIsAccidentallyActivatedToo() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod", "e2e");
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource(
                            "test", java.util.Map.of("bbc.e2e.fixtures.enabled", "true")));
            context.register(E2eFixtureController.class);
            context.refresh();

            assertThat(context.getBeansOfType(E2eFixtureController.class)).isEmpty();
        }
    }

    @Test
    void fixturePropertyMustAlsoBeExplicitlyEnabled() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("e2e");
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource(
                            "test", java.util.Map.of("bbc.e2e.fixtures.enabled", "false")));
            context.register(E2eFixtureController.class);
            context.refresh();

            assertThat(context.getBeansOfType(E2eFixtureController.class)).isEmpty();
        }
    }

    @Test
    void fixtureRouteDeclaresTestOnlyActivationContract() {
        Profile profile = E2eFixtureController.class.getAnnotation(Profile.class);
        ConditionalOnProperty property = E2eFixtureController.class
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("e2e & !prod");
        assertThat(property).isNotNull();
        assertThat(property.prefix()).isEqualTo("bbc.e2e.fixtures");
        assertThat(property.name()).containsExactly("enabled");
        assertThat(property.havingValue()).isEqualTo("true");
    }

    @Test
    void fixtureRouteKeepsTheNormalPermissionManageGuard() throws Exception {
        PreAuthorize guard = E2eFixtureController.class
                .getDeclaredMethod("provisionSecondSchool", E2eFixtureController.FixtureRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(guard).isNotNull();
        assertThat(guard.value()).isEqualTo("@policy.canAction('PERMISSION_MANAGE')");
    }
}
