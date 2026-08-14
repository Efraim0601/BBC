package com.bbc.sms.platform.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the endpoint compatibility gates auditable: a controller may only
 * name an action that is present in the V2 catalogue, and the converted
 * academic surfaces must not regress to a module-only academic gate.
 */
class PermissionActionControllerContractTest {
    private static final Pattern ACTION = Pattern.compile("@perm\\.canAction\\('([A-Z0-9_]+)'\\)");

    @Test
    void everyControllerActionGateExistsInTheStableCatalogue() throws Exception {
        Set<String> used = controllerSources().stream()
                .flatMap(source -> ACTION.matcher(source).results())
                .map(match -> match.group(1))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(used).isNotEmpty();
        assertThat(PermissionActions.CATALOG.keySet()).containsAll(used);
    }

    @Test
    void convertedAcademicControllersDoNotLeaveModuleOnlyAcademicGuards() throws Exception {
        Path root = Path.of("src/main/java/com/bbc/sms/academic");
        for (String name : new String[]{
                "AcademicController.java", "BulletinController.java",
                "AcademicScopeController.java", "AcademicAccessController.java",
                "SecondaryCompetencyController.java"}) {
            Path file = root.resolve(name);
            if (name.equals("SecondaryCompetencyController.java")) file = root.resolve("secondary").resolve(name);
            if (name.equals("AcademicScopeController.java") || name.equals("AcademicAccessController.java")) {
                file = root.resolve("security").resolve(name);
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(source)
                    .as("explicit V2 gate in %s", file)
                    .doesNotContain("@perm.can('academic'")
                    .doesNotContain("@perm.can(\"academic\"");
        }
    }

    @Test
    void sensitiveStudentGuardianEnrollmentAndTimetableRoutesNameExactActions() throws Exception {
        assertContainsActions(Path.of("src/main/java/com/bbc/sms/student/StudentController.java"),
                "STUDENT_PHOTO_VIEW", "STUDENT_PHOTO_MANAGE", "STUDENT_DIRECTORY_VIEW",
                "STUDENT_PROFILE_VIEW", "STUDENT_PROFILE_EDIT", "STUDENT_PROFILE_DEACTIVATE",
                "GUARDIAN_VIEW", "GUARDIAN_LINK_MANAGE");
        assertContainsActions(Path.of("src/main/java/com/bbc/sms/foundation/enrollment/EnrollmentController.java"),
                "ENROLLMENT_VIEW", "ENROLLMENT_CREATE", "ENROLLMENT_TRANSFER", "ENROLLMENT_WITHDRAW");
        assertContainsActions(Path.of("src/main/java/com/bbc/sms/guardian/GuardianController.java"),
                "GUARDIAN_VIEW", "GUARDIAN_LINK_MANAGE", "GUARDIAN_DIRECTORY_SEARCH",
                "GUARDIAN_DIRECTORY_MANAGE");
        assertContainsActions(Path.of("src/main/java/com/bbc/sms/timetable/TimetableController.java"),
                "TIMETABLE_SUBSTITUTION_VIEW", "TIMETABLE_SUBSTITUTION_MANAGE",
                "TIMETABLE_CLASS_SCHEDULE_VIEW", "TIMETABLE_ROOM_VIEW", "TIMETABLE_MASTER_VIEW",
                "TIMETABLE_MY_SCHEDULE_VIEW", "TIMETABLE_TEACHER_SCHEDULE_VIEW_ALL",
                "TIMETABLE_EXPORT");
    }

    private void assertContainsActions(Path file, String... actions) throws Exception {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        for (String action : actions) {
            assertThat(source).as("stable action %s in %s", action, file)
                    .contains(action);
        }
    }

    private Set<String> controllerSources() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            return paths.filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        }
    }
}
