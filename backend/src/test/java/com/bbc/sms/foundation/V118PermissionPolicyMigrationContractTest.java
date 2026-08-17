package com.bbc.sms.foundation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V118PermissionPolicyMigrationContractTest {
    @Test
    void declaresTheCompletePolicyFoundationAndFailClosedInvariants() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/V118__permission_policy_v2_foundation.sql")) {
            assertThat(stream).as("V118 migration resource").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS permission_action")
                .contains("CREATE TABLE IF NOT EXISTS permission_role_action")
                .contains("CREATE TABLE IF NOT EXISTS permission_user_action")
                .contains("CREATE TABLE IF NOT EXISTS app_user_role")
                .contains("CREATE TABLE IF NOT EXISTS permission_policy_rollout")
                .contains("CREATE TABLE IF NOT EXISTS permission_compatibility_report")
                .contains("CREATE TABLE IF NOT EXISTS permission_policy_audit")
                .contains("CREATE TABLE IF NOT EXISTS permission_role_template_rule")
                .contains("CHECK (effect <> 'INHERIT' OR (scope_mode = 'NONE' AND scope_payload IS NULL))")
                .contains("COALESCE(effective_from, '-infinity'::date)")
                .contains("COALESCE(effective_to, 'infinity'::date)")
                .contains("trg_app_user_parcours_version")
                .contains("trg_academic_assignment_version")
                .contains("trg_class_titulaire_version")
                .contains("trg_timetable_slot_scope_version")
                .contains("trg_timetable_substitution_scope_version")
                .contains("trg_teacher_class_scope_version")
                .contains("trg_employee_scope_version")
                .contains("trg_guardian_identity_scope_version")
                .contains("trg_timetable_version_scope_version")
                .contains("trg_school_class_scope_version")
                .contains("principal_legacy_compat")
                .contains("LEGACY_COMPATIBILITY")
                .contains("primary_teacher")
                .contains("secondary_teacher")
                .contains("form_teacher")
                .contains("finance_collector")
                .contains("parent_portal")
                .contains("GUARDIAN_DIRECTORY_SEARCH")
                .contains("GUARDIAN_DIRECTORY_MANAGE")
                .contains("Rechercher un responsable")
                .contains("Administrer le répertoire des responsables")
                .contains("guardian directory search compatibility backfill")
                .contains("guardian directory manage compatibility backfill")
                .contains("SCHOOL_ALL");
    }

    @Test
    void doesNotUseGeneratedActionLabelsAsThePublishedFrenchCatalogue() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/V118__permission_policy_v2_foundation.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("label_fr").contains("description_fr");
        assertThat(sql).doesNotContain("'Action ' || initcap(replace(lower(code),'_',' '))");
        assertThat(sql).doesNotContain("UNIQUE (template_code, action_code, effect, scope_mode,");
    }
}
