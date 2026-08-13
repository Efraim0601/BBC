package com.bbc.sms.finance.fees;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.bbc.sms.finance.fees.FeeTypeDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {"bbc.bootstrap.enabled=false"})
class FeeTypeCatalogueIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bbc_fee_type_test").withUsername("bbc").withPassword("bbc");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired FeeTypeService service;

    private UUID schoolId;
    private UUID receivableId;
    private UUID revenueId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        receivableId = UUID.randomUUID();
        revenueId = UUID.randomUUID();
        jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)", schoolId,
                "F" + schoolId.toString().substring(0, 8), "Fee type test school");
        TenantContext.set(schoolId);
        jdbc.update("""
                INSERT INTO chart_of_account(id,school_id,code,name_fr,name_en,account_type,normal_side,currency)
                VALUES (?,?,'1100','Créances élèves','Student receivable','ASSET','DEBIT','XAF'),
                       (?,?,'4000','Produits de scolarité','Tuition revenue','REVENUE','CREDIT','XAF')
                """, receivableId, schoolId, revenueId, schoolId);
        // These tables belong to BAY-45/BAY-46. Creating a minimal compatible
        // fixture here proves BAY-44 can block deactivation once they arrive.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS fee_plan (
                    id UUID PRIMARY KEY, school_id UUID NOT NULL, name_fr VARCHAR(160), name_en VARCHAR(160),
                    status VARCHAR(12), academic_session_id UUID, class_id UUID
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS fee_plan_line (
                    id UUID PRIMARY KEY, school_id UUID NOT NULL, fee_plan_id UUID NOT NULL,
                    fee_type_revision_id UUID NOT NULL
                )
                """);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void normalizesCodeAndReturnsFieldLevelDuplicateError() {
        FeeTypeView created = service.create(new FeeTypeCreateRequest(" tuition term ", revision(
                "Scolarité", "Tuition", "TUITION", 75000, receivableId, revenueId,
                LocalDate.of(2026, 9, 1), null, null)));

        assertThat(created.code()).isEqualTo("TUITION_TERM");
        assertThat(created.lifecycle()).isEqualTo("DRAFT");
        assertThat(created.currentRevision().defaultAmountMinor()).isEqualTo(75000);

        assertThatThrownBy(() -> service.create(new FeeTypeCreateRequest("tuition-term", revision(
                "Another", "Another", "OTHER", 1, receivableId, revenueId,
                LocalDate.of(2026, 9, 1), null, null))))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException api = (ApiException) error;
                    assertThat(api.getCode()).isEqualTo("FEE_TYPE_CODE_EXISTS");
                    assertThat(api.getFieldErrors()).containsKey("code");
                });
    }

    @Test
    void validatesDatesAndAccountCompatibilityBeforeSave() {
        assertThatThrownBy(() -> service.create(new FeeTypeCreateRequest("BAD_DATES", revision(
                "Bad", "Bad", "TUITION", 100, receivableId, revenueId,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 1), null))))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getFieldErrors()).containsKey("effectiveTo"));

        assertThatThrownBy(() -> service.create(new FeeTypeCreateRequest("BAD_ACCOUNT", revision(
                "Bad", "Bad", "TUITION", 100, revenueId, revenueId,
                LocalDate.of(2026, 9, 1), null, null))))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getFieldErrors()).containsKey("receivableAccountId"));
    }

    @Test
    void activationCreatesImmutableRevisionAndDeactivationListsActivePlanBlocker() {
        FeeTypeView draft = service.create(new FeeTypeCreateRequest("TRANSPORT", revision(
                "Transport", "Transport", "TRANSPORT", 30000, receivableId, revenueId,
                LocalDate.of(2026, 9, 1), null, null)));
        FeeTypeView active = service.activate(draft.id(), new FeeTypeActionRequest(draft.version(), "Reviewed mapping"));
        assertThat(active.lifecycle()).isEqualTo("ACTIVE");
        assertThat(active.currentRevision().revisionStatus()).isEqualTo("ACTIVE");

        FeeTypeView next = service.createRevision(active.id(), new FeeTypeRevisionCreateRequest(
                revision("Transport revised", "Transport revised", "TRANSPORT", 35000, receivableId, revenueId,
                        LocalDate.of(2026, 9, 1), null, active.currentRevision().version()),
                active.version(), "New school-year amount"));
        assertThat(next.revisions()).anyMatch(row -> row.revisionStatus().equals("DRAFT") && row.revisionNo() == 2);
        assertThatThrownBy(() -> service.updateDraft(active.id(), new FeeTypeDraftUpdate(
                active.code(), revision("x", "x", "TRANSPORT", 1, receivableId, revenueId,
                        LocalDate.of(2026, 9, 1), null, 0L), active.version())))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("FEE_TYPE_REVISION_IMMUTABLE"));

        UUID planId = UUID.randomUUID();
        jdbc.update("INSERT INTO fee_plan(id,school_id,name_fr,name_en,status) VALUES (?,?,?,'Transport plan','ACTIVE')",
                planId, schoolId, "Plan transport");
        jdbc.update("INSERT INTO fee_plan_line(id,school_id,fee_plan_id,fee_type_revision_id) VALUES (?,?,?,?)",
                UUID.randomUUID(), schoolId, planId, active.currentRevision().id());
        FeeTypeUsageView usage = service.usage(active.id());
        assertThat(usage.usageCount()).isEqualTo(1);
        assertThatThrownBy(() -> service.deactivate(active.id(), new FeeTypeActionRequest(active.version(), "Retire")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException api = (ApiException) error;
                    assertThat(api.getCode()).isEqualTo("FEE_TYPE_IN_USE");
                    assertThat(api.getBlockers()).anyMatch(blocker -> blocker.entityType().equals("FEE_PLAN")
                            && blocker.entityId().equals(planId.toString())
                            && blocker.label().contains("Transport plan"));
                });

        assertThatThrownBy(() -> jdbc.update("UPDATE fee_type_revision SET name_en='Tampered' WHERE id=?",
                active.currentRevision().id())).hasMessageContaining("immutable");
    }

    @Test
    void legacyPreviewRequiresReviewAndUnresolvedRowsBecomeReconciliationItems() {
        UUID configId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fee_config(id,school_id,level,total,tranches,items)
                VALUES (?,?,? ,?,'[]'::jsonb,?::jsonb)
                """, configId, schoolId, "6eme", 85000,
                "[{\"name\":\"Tuition\",\"amount\":75000},{\"name\":\"Other\",\"amount\":10000}]");

        LegacyPreviewView preview = service.legacyPreview();
        assertThat(preview.candidateCount()).isEqualTo(2);
        assertThat(preview.ambiguousCount()).isEqualTo(1);
        String tuitionKey = configId + ":0";
        String otherKey = configId + ":1";
        LegacyMigrationResult result = service.migrateLegacy(new LegacyMappingRequest(List.of(
                new LegacyMappingRow(tuitionKey, true, null, "TUITION_LEGACY", "Scolarité", "Tuition", "TUITION"),
                new LegacyMappingRow(otherKey, false, null, null, null, null, null)), "Reviewed legacy import"));
        assertThat(result.acceptedCount()).isEqualTo(1);
        assertThat(result.unresolvedCount()).isEqualTo(1);
        assertThat(result.unresolved()).extracting(LegacyFeeCandidate::sourceKey).contains(otherKey);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reconciliation_item WHERE school_id=? AND source_id=?",
                Integer.class, schoolId, otherKey)).isEqualTo(1);
        assertThat(service.list(null, null, null)).extracting(FeeTypeView::code).contains("TUITION_LEGACY");
    }

    @Test
    void tenantIsolationReturnsNotFound() {
        FeeTypeView created = service.create(new FeeTypeCreateRequest("PRIVATE_FEE", revision(
                "Privé", "Private", "OTHER", 100, receivableId, revenueId,
                LocalDate.of(2026, 9, 1), null, null)));
        UUID otherSchool = UUID.randomUUID();
        jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)", otherSchool,
                "O" + otherSchool.toString().substring(0, 8), "Other school");
        TenantContext.set(otherSchool);
        assertThatThrownBy(() -> service.detail(created.id()))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("NOT_FOUND"));
    }

    private FeeTypeRevisionInput revision(String nameFr, String nameEn, String category, long amount,
                                          UUID receivable, UUID revenue, LocalDate from, LocalDate to, Long version) {
        return new FeeTypeRevisionInput(nameFr, nameEn, null, null, category, amount, "xaf", "once",
                true, false, false, 0, receivable, revenue, from, to, version);
    }
}
