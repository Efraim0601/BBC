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
    void gradeEntryWorkflowAdmitsBothTeacherSubmitAndManagementReviewBranches() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/academic/AcademicController.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .as("the shared workflow endpoint must retain branch-specific V2 gates")
                .contains("@perm.staffOnly() and (@perm.canAction('GRADE_SUBMIT') or @perm.canAction('ACADEMIC_GRADE_PACKET_REVIEW'))");
    }

    @Test
    void reportCardCouncilRoutesDeferResourceScopeToTheService() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/academic/AcademicController.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .as("council routes must not reject resource-scoped V2 user authority at the envelope")
                .contains("@GetMapping(\"/report-card-inputs\")")
                .contains("@PutMapping(\"/report-card-inputs\")")
                .contains("@PostMapping(\"/report-card-inputs/{studentId}/submit\")")
                .contains("@PostMapping(\"/report-card-inputs/{studentId}/review\")")
                .doesNotContain("@perm.canAction('ACADEMIC_COUNCIL_INPUT_VIEW') and @perm.staffOnly()")
                .doesNotContain("@perm.canAction('ACADEMIC_COUNCIL_INPUT_EDIT') and @perm.staffOnly()")
                .doesNotContain("@perm.canAction('GRADE_SUBMIT') and @perm.staffOnly()")
                .doesNotContain("@perm.canAction('ACADEMIC_GRADE_PACKET_REVIEW') and @perm.staffOnly()");
    }

    @Test
    void academicDelegationRoutesKeepTheResourceAwareDecisionInTheService() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/academic/security/AcademicAccessController.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("private static final String ADMIN_READ = \"@perm.staffOnly()\";")
                .contains("private static final String ADMIN_WRITE = \"@perm.staffOnly()\";")
                .doesNotContain("@perm.canAction('ACADEMIC_ACCESS_DELEGATE')")
                .doesNotContain("@perm.canAction('ACADEMIC_ACCESS_AUDIT_VIEW')");
    }

    @Test
    void academicDelegationPersistenceBindsApprovalAndTypesNullableStatus() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/academic/security/AcademicAccessDelegationService.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .as("an active grant must record both requester and approver")
                .contains("grant.from(), grant.to(), request.reason().trim(), actor, actor,")
                .as("the optional list status must be explicitly typed for PostgreSQL")
                .contains("CAST(? AS text) IS NULL OR d.status=CAST(? AS text)")
                .as("only the overlap trigger should use the overlap error code")
                .contains("DELEGATION_PERSISTENCE_CONFLICT");
    }

    @Test
    void officialReportCardDocumentsUseTheV2PolicyGate() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/academic/AcademicController.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .as("official document generation must defer STUDENT scope to the snapshot service")
                .contains("@PreAuthorize(\"@perm.staffOnly()\")")
                .contains("snapshotService.requireDocumentGeneration(snapshot)")
                .doesNotContain("@perm.canAction('DOCUMENT_GENERATE')")
                .doesNotContain("@policy.canAction('DOCUMENT_GENERATE')");
    }

    @Test
    void setupReadsAllowV2PolicyResolutionWhileWritesKeepTheParcoursEnvelope() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/setup/SetupController.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("private static final String READ = \"@perm.staffOnly()\";")
                .contains("private static final String WRITE = \"@parcours.allows() and @perm.staffOnly()\";")
                .contains("private static final String CLASS_WRITE = WRITE;")
                .contains("private static final String SUBJECT_WRITE = WRITE;")
                .containsPattern("(?s)@GetMapping\\(\\\"/classes\\\"\\)\\s+@PreAuthorize\\(READ\\)")
                .containsPattern("(?s)@PostMapping\\(\\\"/classes\\\"\\)\\s+@ResponseStatus\\(HttpStatus.CREATED\\)\\s+@PreAuthorize\\(CLASS_WRITE\\)")
                .doesNotContain("private static final String CLASS_WRITE = READ;")
                .doesNotContain("private static final String SUBJECT_WRITE = READ;");
    }

    @Test
    void contextualOperationsDeferStudentScopeToTheirServices() throws Exception {
        assertContextualServiceBoundary(Path.of("src/main/java/com/bbc/sms/health/HealthController.java"),
                "HEALTH_CONFIDENTIAL_VIEW", "HEALTH_MANAGE");
        assertContextualServiceBoundary(Path.of("src/main/java/com/bbc/sms/documents/DocumentController.java"),
                "STUDENT_DOCUMENT_VIEW", "STUDENT_DOCUMENT_GENERATE", "STUDENT_DOCUMENT_REVOKE");
        assertContextualServiceBoundary(Path.of("src/main/java/com/bbc/sms/journey/JourneyController.java"),
                "JOURNEY_VIEW", "JOURNEY_MANAGE");
        String classKit = Files.readString(Path.of("src/main/java/com/bbc/sms/classkit/ClassKitController.java"),
                StandardCharsets.UTF_8);
        assertThat(classKit)
                .contains("@parcours.allows() and @perm.staffOnly()")
                .doesNotContain("@perm.canAction('CLASSKIT_VIEW')")
                .doesNotContain("@perm.canAction('CLASSKIT_MANAGE')");
        assertContextualServiceBoundary(Path.of("src/main/java/com/bbc/sms/discipline/DisciplineController.java"),
                "DISCIPLINE_VIEW", "DISCIPLINE_MANAGE");
        assertContextualServiceBoundary(Path.of("src/main/java/com/bbc/sms/coursebook/CoursebookController.java"),
                "COURSEBOOK_VIEW", "COURSEBOOK_MANAGE");
        assertThat(Files.readString(Path.of("src/main/java/com/bbc/sms/coursebook/CoursebookController.java"),
                StandardCharsets.UTF_8))
                .contains("@GetMapping(\"/classes\")")
                .contains("@GetMapping(\"/subjects\")")
                .contains("return service.classes()")
                .contains("return service.subjects(className)");
        assertThat(Files.readString(Path.of("src/main/java/com/bbc/sms/events/EventController.java"), StandardCharsets.UTF_8))
                .contains("@policy.canAction('EVENTS_VIEW')")
                .contains("@policy.canAction('EVENTS_MANAGE')");
        assertThat(Files.readString(Path.of("src/main/java/com/bbc/sms/messaging/CorrespondenceController.java"), StandardCharsets.UTF_8))
                .contains("@policy.canAction('MESSAGES_VIEW')")
                .contains("@policy.canAction('MESSAGES_MANAGE')");
        assertThat(Files.readString(Path.of("src/main/java/com/bbc/sms/alerts/AlertController.java"), StandardCharsets.UTF_8))
                .contains("@policy.canAction('ALERTS_VIEW')")
                .contains("@policy.canAction('ALERTS_MANAGE')");
        assertThat(Files.readString(Path.of("src/main/java/com/bbc/sms/reports/ReportController.java"), StandardCharsets.UTF_8))
                .contains("@policy.canAction('REPORTS_VIEW')");
    }

    private void assertContextualServiceBoundary(Path file, String... actions) throws Exception {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(source).as("staff envelope in %s", file).contains("@perm.staffOnly()");
        for (String action : actions) {
            assertThat(source).as("contextual action %s stays in the service for %s", action, file)
                    .doesNotContain("@perm.canAction('" + action + "')")
                    .doesNotContain("@policy.canAction('" + action + "')");
        }
    }

    @Test
    void sensitiveStudentGuardianEnrollmentAndTimetableRoutesNameExactActions() throws Exception {
        assertContainsActions(Path.of("src/main/java/com/bbc/sms/student/StudentController.java"),
                "STUDENT_PHOTO_VIEW", "STUDENT_PHOTO_MANAGE",
                "GUARDIAN_VIEW", "GUARDIAN_LINK_MANAGE");
        // Directory/profile actions are intentionally resolved in StudentService
        // so the server can attach the concrete student/session/class scope.
        assertContainsActions(Path.of("src/main/java/com/bbc/sms/student/StudentService.java"),
                "STUDENT_DIRECTORY_VIEW", "STUDENT_PROFILE_VIEW", "STUDENT_PROFILE_EDIT",
                "STUDENT_PROFILE_DEACTIVATE");
        // Enrollment routes retain the staff envelope in the controller; the
        // resource-scoped V2 action checks live in EnrollmentService.
        assertContainsActions(Path.of("src/main/java/com/bbc/sms/foundation/enrollment/EnrollmentService.java"),
                "ENROLLMENT_VIEW", "ENROLLMENT_CREATE", "ENROLLMENT_TRANSFER", "ENROLLMENT_WITHDRAW");
        // Student-scoped guardian actions are resolved after the controller
        // resolves the concrete student/relationship resource.
        assertContainsActions(Path.of("src/main/java/com/bbc/sms/guardian/GuardianService.java"),
                "GUARDIAN_VIEW", "GUARDIAN_LINK_MANAGE", "GUARDIAN_DIRECTORY_SEARCH",
                "GUARDIAN_DIRECTORY_MANAGE");
        // Resource-scoped timetable reads/writes are enforced after the
        // controller resolves the class, version, teacher, or substitution.
        assertContainsActions(Path.of("src/main/java/com/bbc/sms/timetable/TimetableVersionService.java"),
                "TIMETABLE_SUBSTITUTION_VIEW", "TIMETABLE_SUBSTITUTION_MANAGE");
        assertContainsActions(Path.of("src/main/java/com/bbc/sms/timetable/TimetableService.java"),
                "TIMETABLE_CLASS_SCHEDULE_VIEW", "TIMETABLE_MY_SCHEDULE_VIEW",
                "TIMETABLE_TEACHER_SCHEDULE_VIEW_ALL");
        assertContainsActions(Path.of("src/main/java/com/bbc/sms/timetable/TimetableController.java"),
                "TIMETABLE_ROOM_VIEW", "TIMETABLE_MASTER_VIEW", "TIMETABLE_EXPORT");
    }

    @Test
    void studentDirectoryClassOptionsDoNotRequireStudentCreationAuthority() throws Exception {
        String controller = Files.readString(
                Path.of("src/main/java/com/bbc/sms/student/StudentController.java"),
                StandardCharsets.UTF_8);
        String studentService = Files.readString(
                Path.of("src/main/java/com/bbc/sms/student/StudentService.java"),
                StandardCharsets.UTF_8);
        String setupService = Files.readString(
                Path.of("src/main/java/com/bbc/sms/setup/SetupService.java"),
                StandardCharsets.UTF_8);

        assertThat(PermissionActions.CATALOG).containsKey("STUDENT_PROFILE_CREATE");
        int classOptionsStart = controller.indexOf("@GetMapping(\"/class-options\")");
        int createStart = controller.indexOf("@GetMapping(\"/roster\")", classOptionsStart);
        String classOptionsRoute = controller.substring(classOptionsStart, createStart);
        assertThat(classOptionsRoute)
                .contains("@GetMapping(\"/class-options\")")
                .contains("@PreAuthorize(\"@perm.staffOnly()\")")
                .doesNotContain("@policy.canAction('STUDENT_PROFILE_CREATE')");
        assertThat(studentService).contains("listClassesForStudentDirectory()");
        assertThat(setupService)
                .contains("listClassesForStudentProfile()")
                .contains("listClassesForStudentDirectory()")
                .contains("requireSchool(\"STUDENT_PROFILE_CREATE\")");
    }

    @Test
    void teacherClassAssignmentEnablesAssignmentDerivedParcours() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/staff/StaffService.java"),
                StandardCharsets.UTF_8);
        assertThat(source)
                .contains("teacher_class")
                .contains("parcours_scope_mode='ASSIGNMENT_DERIVED'");
    }

    @Test
    void staffControllerUsesV2HrEnvelope() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/staff/StaffController.java"),
                StandardCharsets.UTF_8);
        assertThat(source)
                .contains("@policy.canAction('HR_VIEW')")
                .contains("@policy.canAction('HR_MANAGE')")
                .doesNotContain("@perm.can('hr'")
                .doesNotContain("@perm.can(\"hr\"");
    }

    @Test
    void promotionControllerUsesV2ActionEnvelope() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/bbc/sms/journey/JourneyPromotionController.java"),
                StandardCharsets.UTF_8);
        assertThat(source)
                .contains("@policy.canAction('PROGRESSION_CONFIGURE')")
                .contains("@policy.canAction('PROMOTION_RECOMMEND')")
                .contains("@policy.canAction('PROMOTION_REVIEW')")
                .contains("@policy.canAction('PROMOTION_OVERRIDE')")
                .contains("@policy.canAction('PROMOTION_COMMIT')")
                .doesNotContain("@perm.canAction");
    }

    @Test
    void promotionWorkspaceScopeCorrectionIsForwardOnlyAndNarrow() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V140__promotion_workspace_school_scope.sql"),
                StandardCharsets.UTF_8);
        assertThat(migration)
                .contains("SET scope_type='SCHOOL'")
                .contains("'PROGRESSION_VIEW'")
                .contains("'PROMOTION_RECOMMEND'")
                .contains("'PROMOTION_REVIEW'")
                .contains("'PROMOTION_OVERRIDE'")
                .doesNotContain("permission_role_action")
                .doesNotContain("permission_action_grant");
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
