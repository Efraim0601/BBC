package com.bbc.sms.finance.fees;

import com.bbc.sms.finance.FeeConfig;
import com.bbc.sms.finance.FeeConfigRepository;
import com.bbc.sms.finance.accounting.ChartOfAccount;
import com.bbc.sms.finance.accounting.ChartOfAccountRepository;
import com.bbc.sms.finance.accounting.ReconciliationItem;
import com.bbc.sms.finance.accounting.ReconciliationItemRepository;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bbc.sms.finance.fees.FeeTypeDtos.*;

/**
 * BAY-44 catalogue lifecycle. Fee types are stable identities; business changes
 * are made through draft revisions and activation, never by rewriting an active
 * snapshot. All reads and writes are explicitly scoped to TenantContext.
 */
@Service
public class FeeTypeService {
    private static final Set<String> CATEGORIES = Set.of(
            "TUITION", "REGISTRATION", "TRANSPORT", "EXAM", "UNIFORM", "OTHER");
    private static final Set<String> FREQUENCIES = Set.of("ONCE", "MONTHLY", "TERM", "ANNUAL");
    private static final Set<String> GENERIC_LEGACY_NAMES = Set.of(
            "FEE", "FEES", "ITEM", "OTHER", "AUTRE", "DIVERS", "MISC", "MISCELLANEOUS", "TOTAL");

    private final FeeTypeRepository feeTypes;
    private final FeeTypeRevisionRepository revisions;
    private final ChartOfAccountRepository accounts;
    private final FeeConfigRepository legacyConfigs;
    private final ReconciliationItemRepository reconciliation;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public FeeTypeService(FeeTypeRepository feeTypes, FeeTypeRevisionRepository revisions,
                          ChartOfAccountRepository accounts, FeeConfigRepository legacyConfigs,
                          ReconciliationItemRepository reconciliation, JdbcTemplate jdbc,
                          AuditService audit) {
        this.feeTypes = feeTypes;
        this.revisions = revisions;
        this.accounts = accounts;
        this.legacyConfigs = legacyConfigs;
        this.reconciliation = reconciliation;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<FeeTypeView> list(String query, String lifecycle, String category) {
        UUID schoolId = TenantContext.get();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String wantedLifecycle = normalizeOptionalToken(lifecycle);
        String wantedCategory = normalizeOptionalToken(category);
        return feeTypes.findBySchoolIdOrderByCodeAsc(schoolId).stream()
                .filter(type -> wantedLifecycle == null || wantedLifecycle.equals(type.getLifecycle()))
                .filter(type -> wantedCategory == null || latestCategory(type).equals(wantedCategory))
                .filter(type -> needle.isBlank() || type.getCode().toLowerCase(Locale.ROOT).contains(needle)
                        || latestName(type, true).toLowerCase(Locale.ROOT).contains(needle)
                        || latestName(type, false).toLowerCase(Locale.ROOT).contains(needle))
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public FeeTypeView detail(UUID id) {
        return view(require(id));
    }

    @Transactional
    public FeeTypeView create(FeeTypeCreateRequest request) {
        UUID schoolId = TenantContext.get();
        String code = normalizeCode(request.code());
        NormalizedRevision normalized = validate(request.revision(), false);
        ensureUniqueCode(schoolId, code, null);

        FeeType type = new FeeType();
        type.setSchoolId(schoolId);
        type.setCode(code);
        type.setLifecycle("DRAFT");
        type.setCreatedBy(currentUserId());
        type.setUpdatedBy(currentUserId());
        try {
            type = feeTypes.saveAndFlush(type);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateCode(ex);
        }

        FeeTypeRevision revision = new FeeTypeRevision();
        revision.setSchoolId(schoolId);
        revision.setFeeTypeId(type.getId());
        revision.setRevisionNo(1);
        revision.setRevisionStatus("DRAFT");
        revision.setCreatedBy(currentUserId());
        apply(revision, normalized);
        revisions.saveAndFlush(revision);

        FeeTypeView result = view(type);
        audit.record("FEE_TYPE_CREATED", "FeeType", type.getId().toString(), null, result, null);
        return result;
    }

    @Transactional
    public FeeTypeView updateDraft(UUID id, FeeTypeDraftUpdate request) {
        UUID schoolId = TenantContext.get();
        FeeType type = require(id);
        requireTypeVersion(request.typeVersion(), type);
        if (!"DRAFT".equals(type.getLifecycle())) {
            throw ApiException.structured(HttpStatus.CONFLICT, "FEE_TYPE_REVISION_IMMUTABLE",
                    "Ce type de frais est déjà activé. Créez une nouvelle révision pour le modifier.",
                    Map.of("typeVersion", "Utilisez « Nouvelle révision » pour modifier un type activé."),
                    List.of(new ApiException.Blocker("FEE_TYPE", id.toString(), type.getCode(), "CREATE_FEE_TYPE_REVISION")));
        }
        FeeTypeRevision draft = revisions.findTopBySchoolIdAndFeeTypeIdAndRevisionStatusOrderByRevisionNoDesc(
                        schoolId, id, "DRAFT")
                .orElseThrow(() -> ApiException.structured(HttpStatus.CONFLICT, "FEE_TYPE_DRAFT_MISSING",
                        "Aucune révision brouillon n'est disponible pour ce type de frais.", Map.of(), List.of()));
        requireRevisionVersion(request.revision().version(), draft);
        String code = normalizeCode(request.code());
        NormalizedRevision normalized = validate(request.revision(), false);
        ensureUniqueCode(schoolId, code, id);

        FeeTypeView before = view(type);
        type.setCode(code);
        type.setUpdatedBy(currentUserId());
        apply(draft, normalized);
        try {
            feeTypes.saveAndFlush(type);
            revisions.saveAndFlush(draft);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateCode(ex);
        }
        FeeTypeView result = view(type);
        audit.record("FEE_TYPE_DRAFT_UPDATED", "FeeType", id.toString(), before, result, null);
        return result;
    }

    @Transactional
    public FeeTypeView createRevision(UUID id, FeeTypeRevisionCreateRequest request) {
        UUID schoolId = TenantContext.get();
        FeeType type = require(id);
        requireTypeVersion(request.typeVersion(), type);
        if (revisions.findTopBySchoolIdAndFeeTypeIdAndRevisionStatusOrderByRevisionNoDesc(
                schoolId, id, "DRAFT").isPresent()) {
            throw ApiException.structured(HttpStatus.CONFLICT, "FEE_TYPE_DRAFT_EXISTS",
                    "Une révision brouillon existe déjà pour ce type de frais.", Map.of(),
                    List.of(new ApiException.Blocker("FEE_TYPE", id.toString(), type.getCode(), "OPEN_FEE_TYPE")));
        }
        FeeTypeRevision base = revisions.findTopBySchoolIdAndFeeTypeIdAndRevisionStatusOrderByRevisionNoDesc(
                        schoolId, id, "ACTIVE")
                .orElseThrow(() -> ApiException.structured(HttpStatus.CONFLICT, "FEE_TYPE_ACTIVE_REVISION_MISSING",
                        "Activez d'abord la première révision avant d'en créer une nouvelle.", Map.of(), List.of()));
        requireRevisionVersion(request.revision().version(), base);
        NormalizedRevision normalized = validate(request.revision(), false);
        FeeTypeRevision revision = copy(base);
        revision.setId(null);
        revision.setRevisionNo(revisions.findBySchoolIdAndFeeTypeIdOrderByRevisionNoDesc(schoolId, id).stream()
                .mapToInt(FeeTypeRevision::getRevisionNo).max().orElse(0) + 1);
        revision.setRevisionStatus("DRAFT");
        revision.setCreatedBy(currentUserId());
        revision.setActivatedBy(null);
        revision.setActivatedAt(null);
        revision.setVersion(0);
        apply(revision, normalized);

        FeeTypeView before = view(type);
        revisions.saveAndFlush(revision);
        type.setUpdatedBy(currentUserId());
        feeTypes.saveAndFlush(type);
        FeeTypeView result = view(type);
        audit.record("FEE_TYPE_REVISION_CREATED", "FeeType", id.toString(), before, result, request.reason());
        return result;
    }

    @Transactional
    public FeeTypeView activate(UUID id, FeeTypeActionRequest request) {
        UUID schoolId = TenantContext.get();
        FeeType type = require(id);
        requireTypeVersion(request.typeVersion(), type);
        FeeTypeRevision draft = revisions.findTopBySchoolIdAndFeeTypeIdAndRevisionStatusOrderByRevisionNoDesc(
                        schoolId, id, "DRAFT")
                .orElseThrow(() -> ApiException.structured(HttpStatus.CONFLICT, "FEE_TYPE_DRAFT_MISSING",
                        "Créez ou complétez une révision brouillon avant activation.", Map.of(), List.of()));
        NormalizedRevision normalized = validate(toInput(draft), true);
        FeeTypeRevision active = revisions.findTopBySchoolIdAndFeeTypeIdAndRevisionStatusOrderByRevisionNoDesc(
                schoolId, id, "ACTIVE").orElse(null);
        FeeTypeView before = view(type);
        if (active != null) {
            active.setRevisionStatus("SUPERSEDED");
            revisions.saveAndFlush(active);
        }
        apply(draft, normalized);
        draft.setRevisionStatus("ACTIVE");
        draft.setActivatedBy(currentUserId());
        draft.setActivatedAt(Instant.now());
        revisions.saveAndFlush(draft);

        type.setLifecycle("ACTIVE");
        type.setCurrentRevisionNo(draft.getRevisionNo());
        type.setActivatedBy(currentUserId());
        type.setActivatedAt(Instant.now());
        type.setUpdatedBy(currentUserId());
        type.setDeactivatedBy(null);
        type.setDeactivatedAt(null);
        type.setDeactivationReason(null);
        feeTypes.saveAndFlush(type);
        FeeTypeView result = view(type);
        audit.record("FEE_TYPE_REVISION_ACTIVATED", "FeeType", id.toString(), before, result, request.reason());
        return result;
    }

    @Transactional
    public FeeTypeView deactivate(UUID id, FeeTypeActionRequest request) {
        FeeType type = require(id);
        requireTypeVersion(request.typeVersion(), type);
        if ("INACTIVE".equals(type.getLifecycle())) return view(type);
        if (!"ACTIVE".equals(type.getLifecycle())) {
            throw ApiException.structured(HttpStatus.CONFLICT, "FEE_TYPE_NOT_ACTIVE",
                    "Un type de frais brouillon doit être complété ou abandonné avant sa désactivation.", Map.of(), List.of());
        }
        FeeTypeUsageView usage = usage(id, true);
        if (!usage.dependencies().isEmpty()) {
            List<ApiException.Blocker> blockers = usage.dependencies().stream()
                    .map(d -> new ApiException.Blocker(d.entityType(), d.entityId(), dependencyLabel(d),
                            d.entityType().equals("FEE_PLAN") ? "OPEN_FEE_PLAN" : "OPEN_CHARGE"))
                    .toList();
            throw ApiException.structured(HttpStatus.CONFLICT, "FEE_TYPE_IN_USE",
                    "Ce type de frais est encore utilisé par des plans ou charges actifs.", Map.of(), blockers);
        }
        FeeTypeView before = view(type);
        type.setLifecycle("INACTIVE");
        type.setDeactivatedBy(currentUserId());
        type.setDeactivatedAt(Instant.now());
        type.setDeactivationReason(blankToNull(request.reason()));
        type.setUpdatedBy(currentUserId());
        feeTypes.saveAndFlush(type);
        FeeTypeView result = view(type);
        audit.record("FEE_TYPE_DEACTIVATED", "FeeType", id.toString(), before, result, request.reason());
        return result;
    }

    @Transactional(readOnly = true)
    public FeeTypeUsageView usage(UUID id) {
        FeeType type = require(id);
        return usage(type, false);
    }

    @Transactional(readOnly = true)
    public FeeTypeUsageView usage(UUID id, boolean activeOnly) {
        return usage(require(id), activeOnly);
    }

    @Transactional(readOnly = true)
    public LegacyPreviewView legacyPreview() {
        List<LegacyFeeCandidate> candidates = extractLegacyCandidates();
        int ambiguous = (int) candidates.stream().filter(LegacyFeeCandidate::ambiguous).count();
        return new LegacyPreviewView(candidates, candidates.size(), ambiguous, candidates.size(), Instant.now());
    }

    @Transactional
    public LegacyMigrationResult migrateLegacy(LegacyMappingRequest request) {
        UUID schoolId = TenantContext.get();
        List<LegacyFeeCandidate> candidates = extractLegacyCandidates();
        Map<String, LegacyFeeCandidate> byKey = candidates.stream()
                .collect(Collectors.toMap(LegacyFeeCandidate::sourceKey, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
        Map<String, LegacyMappingRow> mappings = request.rows().stream()
                .collect(Collectors.toMap(LegacyMappingRow::sourceKey, Function.identity(), (a, b) -> b));
        List<LegacyFeeCandidate> unresolved = new ArrayList<>();
        Map<UUID, FeeType> mapped = new LinkedHashMap<>();
        int accepted = 0;
        for (LegacyFeeCandidate candidate : candidates) {
            LegacyMappingRow row = mappings.get(candidate.sourceKey());
            if (row == null || !Boolean.TRUE.equals(row.accept())) {
                unresolved.add(candidate);
                recordLegacyException(candidate, "LEGACY_MAPPING_REVIEW_REQUIRED: mapping not explicitly approved");
                continue;
            }
            if (candidate.ambiguous() && row.feeTypeId() == null
                    && (row.code() == null || row.code().isBlank())) {
                unresolved.add(candidate);
                recordLegacyException(candidate, "LEGACY_MAPPING_AMBIGUOUS: choose an existing fee type or enter an explicit code");
                continue;
            }
            FeeType type;
            if (row.feeTypeId() != null) {
                type = require(row.feeTypeId());
            } else {
                String code = normalizeCode(blankToNull(row.code()) == null ? candidate.suggestedCode() : row.code());
                type = feeTypes.findBySchoolIdAndCode(schoolId, code).orElseGet(() -> createLegacyDraft(candidate, row, code));
            }
            mapped.put(type.getId(), type);
            accepted++;
        }
        LegacyMigrationResult result = new LegacyMigrationResult(accepted, unresolved.size(),
                mapped.values().stream().map(this::view).toList(), unresolved, Instant.now());
        audit.record("FEE_TYPE_LEGACY_MIGRATION_REVIEWED", "FeeTypeLegacyImport", schoolId.toString(),
                null, Map.of("acceptedCount", accepted, "unresolvedCount", unresolved.size()), request.reason());
        return result;
    }

    @Transactional(readOnly = true)
    public FeeTypeComparison compare(UUID id, int leftRevision, int rightRevision) {
        UUID schoolId = TenantContext.get();
        FeeType type = require(id);
        FeeTypeRevision left = revisions.findBySchoolIdAndFeeTypeIdAndRevisionNo(schoolId, id, leftRevision)
                .orElseThrow(() -> ApiException.notFound("Révision " + leftRevision));
        FeeTypeRevision right = revisions.findBySchoolIdAndFeeTypeIdAndRevisionNo(schoolId, id, rightRevision)
                .orElseThrow(() -> ApiException.notFound("Révision " + rightRevision));
        List<ComparisonField> differences = new ArrayList<>();
        compare(differences, "nameFr", left.getNameFr(), right.getNameFr());
        compare(differences, "nameEn", left.getNameEn(), right.getNameEn());
        compare(differences, "category", left.getCategory(), right.getCategory());
        compare(differences, "defaultAmountMinor", left.getDefaultAmountMinor(), right.getDefaultAmountMinor());
        compare(differences, "defaultCurrency", left.getDefaultCurrency(), right.getDefaultCurrency());
        compare(differences, "frequency", left.getFrequency(), right.getFrequency());
        compare(differences, "mandatory", left.isMandatory(), right.isMandatory());
        compare(differences, "refundable", left.isRefundable(), right.isRefundable());
        compare(differences, "taxable", left.isTaxable(), right.isTaxable());
        compare(differences, "taxBasisPoints", left.getTaxBasisPoints(), right.getTaxBasisPoints());
        compare(differences, "receivableAccountId", left.getReceivableAccountId(), right.getReceivableAccountId());
        compare(differences, "revenueAccountId", left.getRevenueAccountId(), right.getRevenueAccountId());
        compare(differences, "effectiveFrom", left.getEffectiveFrom(), right.getEffectiveFrom());
        compare(differences, "effectiveTo", left.getEffectiveTo(), right.getEffectiveTo());
        return new FeeTypeComparison(type.getId(), type.getCode(), leftRevision, rightRevision, differences);
    }

    private FeeTypeUsageView usage(FeeType type, boolean activeOnly) {
        List<FeeTypeDependency> dependencies = new ArrayList<>();
        dependencies.addAll(planDependencies(type, activeOnly));
        dependencies.addAll(chargeDependencies(type, activeOnly));
        return new FeeTypeUsageView(type.getId(), type.getCode(), dependencies.size(), dependencies);
    }

    private List<FeeTypeDependency> planDependencies(FeeType type, boolean activeOnly) {
        Set<String> lineColumns = columns("fee_plan_line");
        Set<String> planColumns = columns("fee_plan");
        if (lineColumns.isEmpty() || planColumns.isEmpty() || !lineColumns.contains("school_id")
                || !planColumns.contains("school_id")) return List.of();
        String linePlanId = first(lineColumns, "fee_plan_id", "plan_id");
        String feePredicate;
        List<Object> args = new ArrayList<>();
        if (lineColumns.contains("fee_type_revision_id")) {
            feePredicate = "l.fee_type_revision_id IN (SELECT r.id FROM fee_type_revision r WHERE r.school_id=? AND r.fee_type_id=?)";
            args.add(type.getSchoolId());
            args.add(type.getId());
        } else if (lineColumns.contains("fee_type_id")) {
            feePredicate = "l.fee_type_id=?";
            args.add(type.getId());
        } else if (lineColumns.contains("fee_type_code")) {
            feePredicate = "upper(l.fee_type_code)=?";
            args.add(type.getCode());
        } else return List.of();
        if (linePlanId == null) return List.of();

        String statusPredicate = activeOnly && planColumns.contains("status")
                ? " AND p.status='ACTIVE'" : "";
        if (activeOnly && !planColumns.contains("status")) return List.of();
        String label = expression(planColumns, "name_en", "name_fr", "CAST(p.id AS text)", "p");
        String sessionId = first(planColumns, "academic_session_id", "session_id");
        String classId = planColumns.contains("class_id") ? "CAST(p.class_id AS text)" : "NULL";
        String sessionExpr = sessionId == null ? "NULL" : "CAST(p." + sessionId + " AS text)";
        String sessionLabel = sessionId == null ? "NULL" : sessionLabelExpression(sessionId);
        String classLabel = planColumns.contains("class_id") ? "c.name" : "NULL";
        String joinClass = planColumns.contains("class_id")
                ? " LEFT JOIN school_class c ON c.school_id=p.school_id AND c.id=p.class_id" : "";
        String joinSession = sessionId == null ? "" : " LEFT JOIN academic_session s ON s.school_id=p.school_id AND s.id=p." + sessionId;
        String sql = "SELECT CAST(p.id AS text), " + label + ", " + sessionExpr + ", " + sessionLabel + ", "
                + classId + ", " + classLabel + ", "
                + (planColumns.contains("status") ? "p.status" : "NULL")
                + " FROM fee_plan_line l JOIN fee_plan p ON p.school_id=l.school_id AND p.id=l." + linePlanId
                + joinClass + joinSession
                + " WHERE l.school_id=? AND p.school_id=? AND " + feePredicate + statusPredicate;
        List<Object> ordered = new ArrayList<>();
        ordered.add(type.getSchoolId());
        ordered.add(type.getSchoolId());
        ordered.addAll(args);
        return jdbc.query(sql, (rs, rowNum) -> new FeeTypeDependency(
                "FEE_PLAN", rs.getString(1), nullToFallback(rs.getString(2), rs.getString(1)),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), "Active fee plan uses " + type.getCode()), ordered.toArray());
    }

    private List<FeeTypeDependency> chargeDependencies(FeeType type, boolean activeOnly) {
        Set<String> chargeColumns = columns("student_charge");
        if (chargeColumns.isEmpty() || !chargeColumns.contains("school_id")) return List.of();
        String feePredicate;
        List<Object> args = new ArrayList<>();
        if (chargeColumns.contains("fee_type_revision_id")) {
            feePredicate = "c.fee_type_revision_id IN (SELECT r.id FROM fee_type_revision r WHERE r.school_id=? AND r.fee_type_id=?)";
            args.add(type.getSchoolId());
            args.add(type.getId());
        } else if (chargeColumns.contains("fee_type_id")) {
            feePredicate = "c.fee_type_id=?";
            args.add(type.getId());
        } else if (chargeColumns.contains("fee_type_code")) {
            feePredicate = "upper(c.fee_type_code)=?";
            args.add(type.getCode());
        } else return List.of();
        if (activeOnly && !chargeColumns.contains("status")) return List.of();
        String statusPredicate = activeOnly ? " AND c.status NOT IN ('REVERSED','VOID','CANCELLED')" : "";
        String sessionId = first(chargeColumns, "academic_session_id", "session_id");
        String classId = chargeColumns.contains("class_id") ? "CAST(c.class_id AS text)" : "NULL";
        String sessionExpr = sessionId == null ? "NULL" : "CAST(c." + sessionId + " AS text)";
        String joinSession = sessionId == null ? "" : " LEFT JOIN academic_session s ON s.school_id=c.school_id AND s.id=c." + sessionId;
        String sessionLabel = sessionId == null ? "NULL" : "COALESCE(s.label, s.code, CAST(c." + sessionId + " AS text))";
        String classLabel = chargeColumns.contains("class_id") ? "cl.name" : "NULL";
        String joinClass = chargeColumns.contains("class_id")
                ? " LEFT JOIN school_class cl ON cl.school_id=c.school_id AND cl.id=c.class_id" : "";
        String label = expression(chargeColumns, "description", "label", "CAST(c.id AS text)", "c");
        String sql = "SELECT CAST(c.id AS text), " + label + ", " + sessionExpr + ", " + sessionLabel + ", "
                + classId + ", " + classLabel + ", "
                + (chargeColumns.contains("status") ? "c.status" : "NULL")
                + " FROM student_charge c" + joinClass + joinSession
                + " WHERE c.school_id=? AND " + feePredicate + statusPredicate;
        List<Object> ordered = new ArrayList<>();
        ordered.add(type.getSchoolId());
        ordered.addAll(args);
        return jdbc.query(sql, (rs, rowNum) -> new FeeTypeDependency(
                "STUDENT_CHARGE", rs.getString(1), nullToFallback(rs.getString(2), rs.getString(1)),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), "Charge uses " + type.getCode()), ordered.toArray());
    }

    private List<LegacyFeeCandidate> extractLegacyCandidates() {
        UUID schoolId = TenantContext.get();
        Set<String> existingCodes = feeTypes.findBySchoolIdOrderByCodeAsc(schoolId).stream()
                .map(FeeType::getCode).collect(Collectors.toCollection(HashSet::new));
        Set<String> assignedCodes = new HashSet<>();
        List<LegacyFeeCandidate> candidates = new ArrayList<>();
        for (FeeConfig config : legacyConfigs.findBySchoolId(schoolId)) {
            List<Map<String, Object>> items = config.getItems() == null ? List.of() : config.getItems();
            for (int index = 0; index < items.size(); index++) {
                Map<String, Object> item = items.get(index) == null ? Map.of() : items.get(index);
                String rawName = firstString(item, "name", "label", "nameFr", "nameEn", "code");
                String normalizedName = rawName == null ? "" : rawName.trim();
                String code = suggestedCode(normalizedName, index + 1);
                boolean collision = existingCodes.contains(code) || !assignedCodes.add(code);
                if (collision) code = uniqueSuggestedCode(code, existingCodes, assignedCodes);
                String reason = null;
                boolean ambiguous = false;
                if (normalizedName.isBlank()) {
                    ambiguous = true;
                    reason = "Le libellé legacy est vide.";
                } else if (GENERIC_LEGACY_NAMES.contains(normalizedName.toUpperCase(Locale.ROOT))) {
                    ambiguous = true;
                    reason = "Le libellé legacy est trop générique; choisissez un type précis.";
                } else if (collision) {
                    ambiguous = true;
                    reason = "Plusieurs libellés legacy produisent un code similaire; vérifiez le mapping.";
                }
                String sourceKey = config.getId() + ":" + index;
                String name = normalizedName.isBlank() ? "Legacy fee item " + (index + 1) : normalizedName;
                candidates.add(new LegacyFeeCandidate(sourceKey, config.getId().toString(), config.getLevel(),
                        config.getClassId() == null ? null : config.getClassId().toString(), normalizedName, code,
                        name, name, amount(item), "XAF", category(item), ambiguous, reason));
            }
        }
        return candidates;
    }

    private FeeType createLegacyDraft(LegacyFeeCandidate candidate, LegacyMappingRow row, String code) {
        UUID schoolId = TenantContext.get();
        FeeType type = new FeeType();
        type.setSchoolId(schoolId);
        type.setCode(code);
        type.setLifecycle("DRAFT");
        type.setCreatedBy(currentUserId());
        type.setUpdatedBy(currentUserId());
        type = feeTypes.saveAndFlush(type);
        FeeTypeRevision revision = new FeeTypeRevision();
        revision.setSchoolId(schoolId);
        revision.setFeeTypeId(type.getId());
        revision.setRevisionNo(1);
        revision.setRevisionStatus("DRAFT");
        revision.setCreatedBy(currentUserId());
        revision.setNameFr(blankToNull(row.nameFr()) == null ? candidate.suggestedNameFr() : row.nameFr().trim());
        revision.setNameEn(blankToNull(row.nameEn()) == null ? candidate.suggestedNameEn() : row.nameEn().trim());
        revision.setCategory(normalizeCategory(row.category() == null ? candidate.category() : row.category()));
        revision.setDefaultAmountMinor(candidate.amountMinor());
        revision.setDefaultCurrency(candidate.currency());
        revision.setFrequency("ONCE");
        revision.setMandatory(true);
        revision.setRefundable(false);
        revision.setTaxable(false);
        revision.setTaxBasisPoints(0);
        revisions.saveAndFlush(revision);
        audit.record("FEE_TYPE_LEGACY_DRAFT_CREATED", "FeeType", type.getId().toString(), null,
                Map.of("sourceKey", candidate.sourceKey(), "code", code), "Approved legacy mapping");
        return type;
    }

    private void recordLegacyException(LegacyFeeCandidate candidate, String reason) {
        UUID schoolId = TenantContext.get();
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM reconciliation_item
                 WHERE school_id=? AND source_type='LEGACY_FEE_ITEM' AND source_id=?
                   AND state NOT IN ('MATCHED','IGNORED')
                """, Integer.class, schoolId, candidate.sourceKey());
        if (count != null && count > 0) return;
        ReconciliationItem item = new ReconciliationItem();
        item.setSchoolId(schoolId);
        item.setSourceType("LEGACY_FEE_ITEM");
        item.setSourceId(candidate.sourceKey());
        item.setExpectedAmount(candidate.amountMinor());
        item.setPostedAmount(0);
        item.setCurrency(candidate.currency());
        item.setState("MISSING");
        item.setReason(reason + " / " + (candidate.rawName() == null ? "(empty name)" : candidate.rawName()));
        reconciliation.saveAndFlush(item);
    }

    private FeeType require(UUID id) {
        return feeTypes.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Type de frais"));
    }

    private void requireTypeVersion(Long supplied, FeeType type) {
        if (supplied == null || supplied.longValue() != type.getVersion()) {
            throw ApiException.structured(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Ce type de frais a changé ailleurs. Rechargez-le avant de réessayer.",
                    Map.of("typeVersion", "La version affichée n'est plus actuelle."),
                    List.of(new ApiException.Blocker("FEE_TYPE", type.getId().toString(), type.getCode(), "RELOAD_FEE_TYPE")));
        }
    }

    private void requireRevisionVersion(Long supplied, FeeTypeRevision revision) {
        if (supplied != null && supplied.longValue() != revision.getVersion()) {
            throw ApiException.structured(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Cette révision a changé ailleurs. Rechargez-la avant de réessayer.",
                    Map.of("version", "La version affichée n'est plus actuelle."),
                    List.of(new ApiException.Blocker("FEE_TYPE_REVISION", revision.getId().toString(),
                            "Révision " + revision.getRevisionNo(), "RELOAD_FEE_TYPE")));
        }
    }

    private void ensureUniqueCode(UUID schoolId, String code, UUID currentId) {
        FeeType existing = feeTypes.findBySchoolIdAndCode(schoolId, code).orElse(null);
        if (existing != null && !Objects.equals(existing.getId(), currentId)) {
            throw ApiException.structured(HttpStatus.CONFLICT, "FEE_TYPE_CODE_EXISTS",
                    "Ce code de type de frais existe déjà dans cet établissement.",
                    Map.of("code", "Choisissez un code unique; les espaces et tirets deviennent des underscores."), List.of());
        }
    }

    private ApiException duplicateCode(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause() == null ? "" : String.valueOf(ex.getMostSpecificCause().getMessage());
        if (detail.contains("uq_fee_type_school_code") || detail.toLowerCase(Locale.ROOT).contains("fee_type")) {
            return ApiException.structured(HttpStatus.CONFLICT, "FEE_TYPE_CODE_EXISTS",
                    "Ce code de type de frais existe déjà dans cet établissement.",
                    Map.of("code", "Choisissez un code unique."), List.of());
        }
        return ApiException.badRequest("Le type de frais ne peut pas être enregistré; vérifiez ses comptes et ses dates.");
    }

    private NormalizedRevision validate(FeeTypeRevisionInput input, boolean activating) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (input == null) {
            throw ApiException.structured(HttpStatus.BAD_REQUEST, "FEE_TYPE_VALIDATION_ERROR",
                    "La révision du type de frais est obligatoire.", Map.of("revision", "Complétez la révision."), List.of());
        }
        String nameFr = trim(input.nameFr());
        String nameEn = trim(input.nameEn());
        if (nameFr == null) errors.put("nameFr", "Le nom français est obligatoire.");
        if (nameEn == null) errors.put("nameEn", "Le nom anglais est obligatoire.");
        String category = normalizeCategory(input.category());
        if (!CATEGORIES.contains(category)) errors.put("category", "Choisissez une catégorie reconnue.");
        long amount = input.defaultAmountMinor() == null ? -1 : input.defaultAmountMinor();
        if (amount < 0) errors.put("defaultAmountMinor", "Le montant doit être un entier positif ou nul.");
        String currency = normalizeCurrency(input.defaultCurrency());
        if (!currency.matches("[A-Z]{3}")) errors.put("defaultCurrency", "Utilisez un code ISO à trois lettres, par exemple XAF.");
        String frequency = normalizeToken(input.frequency());
        if (!FREQUENCIES.contains(frequency)) errors.put("frequency", "Choisissez une fréquence valide.");
        int tax = input.taxBasisPoints() == null ? 0 : input.taxBasisPoints();
        if (tax < 0 || tax > 10000) errors.put("taxBasisPoints", "La taxe doit être comprise entre 0 et 10000 points de base.");
        if (input.effectiveFrom() != null && input.effectiveTo() != null && input.effectiveTo().isBefore(input.effectiveFrom())) {
            errors.put("effectiveTo", "La date de fin ne peut pas précéder la date de début.");
        }
        if (activating && input.effectiveFrom() == null) errors.put("effectiveFrom", "Une date d'effet est obligatoire pour activer.");
        validateAccount(input.receivableAccountId(), currency, "receivableAccountId", "ASSET", errors);
        validateAccount(input.revenueAccountId(), currency, "revenueAccountId", "REVENUE", errors);
        if (input.receivableAccountId() != null && input.receivableAccountId().equals(input.revenueAccountId())) {
            errors.put("revenueAccountId", "Le compte de produit doit être différent du compte de créance.");
        }
        if (activating) {
            if (input.receivableAccountId() == null) errors.put("receivableAccountId", "Choisissez le compte de créance.");
            if (input.revenueAccountId() == null) errors.put("revenueAccountId", "Choisissez le compte de produit.");
        }
        if (!errors.isEmpty()) {
            throw ApiException.structured(HttpStatus.BAD_REQUEST, "FEE_TYPE_VALIDATION_ERROR",
                    activating ? "Le type de frais ne peut pas être activé; corrigez les champs signalés."
                            : "Corrigez les champs signalés avant d'enregistrer la révision.", errors, List.of());
        }
        return new NormalizedRevision(nameFr, nameEn, trim(input.descriptionFr()), trim(input.descriptionEn()), category,
                amount, currency, frequency, input.mandatory() == null || input.mandatory(),
                Boolean.TRUE.equals(input.refundable()), Boolean.TRUE.equals(input.taxable()), tax,
                input.receivableAccountId(), input.revenueAccountId(), input.effectiveFrom(), input.effectiveTo());
    }

    private void validateAccount(UUID accountId, String currency, String field, String expectedType,
                                 Map<String, String> errors) {
        if (accountId == null) return;
        ChartOfAccount account = accounts.findByIdAndSchoolId(accountId, TenantContext.get()).orElse(null);
        if (account == null) {
            errors.put(field, "Choisissez un compte appartenant à cet établissement.");
            return;
        }
        if (!expectedType.equals(account.getAccountType())) {
            errors.put(field, "Choisissez un compte de type " + expectedType + ".");
        } else if (!account.isActive() || !account.isPostingAllowed()) {
            errors.put(field, "Ce compte est désactivé ou non autorisé pour les écritures.");
        } else if (account.getCurrency() != null && !currency.equals(account.getCurrency())) {
            errors.put(field, "La devise du compte doit être compatible avec " + currency + ".");
        }
    }

    private FeeTypeView view(FeeType type) {
        List<FeeTypeRevision> rows = revisions.findBySchoolIdAndFeeTypeIdOrderByRevisionNoDesc(type.getSchoolId(), type.getId());
        List<FeeTypeRevisionView> revisionViews = rows.stream().map(this::revisionView).toList();
        FeeTypeRevision current = type.getCurrentRevisionNo() == null ? rows.stream().findFirst().orElse(null)
                : rows.stream().filter(r -> r.getRevisionNo() == type.getCurrentRevisionNo()).findFirst()
                .orElse(rows.stream().findFirst().orElse(null));
        FeeTypeUsageView usage = usage(type, false);
        return new FeeTypeView(type.getId(), type.getCode(), type.getLifecycle(), type.getCurrentRevisionNo(),
                current == null ? null : revisionView(current), revisionViews, usage.usageCount(),
                typeEffectiveStatus(type, current), type.getVersion(), type.getCreatedAt(), type.getActivatedAt(),
                type.getDeactivatedAt(), type.getDeactivationReason());
    }

    private FeeTypeRevisionView revisionView(FeeTypeRevision row) {
        return new FeeTypeRevisionView(row.getId(), row.getRevisionNo(), row.getRevisionStatus(), row.getNameFr(), row.getNameEn(),
                row.getDescriptionFr(), row.getDescriptionEn(), row.getCategory(), row.getDefaultAmountMinor(),
                row.getDefaultCurrency(), row.getFrequency(), row.isMandatory(), row.isRefundable(), row.isTaxable(),
                row.getTaxBasisPoints(), row.getReceivableAccountId(), accountRef(row.getReceivableAccountId(), row.getDefaultCurrency(), "ASSET"),
                row.getRevenueAccountId(), accountRef(row.getRevenueAccountId(), row.getDefaultCurrency(), "REVENUE"),
                row.getEffectiveFrom(), row.getEffectiveTo(), revisionEffectiveStatus(row), row.getActivatedAt(), row.getVersion());
    }

    private AccountRef accountRef(UUID id, String currency, String expectedType) {
        if (id == null) return null;
        ChartOfAccount account = accounts.findByIdAndSchoolId(id, TenantContext.get()).orElse(null);
        if (account == null) return new AccountRef(id, "?", "Compte introuvable", "Account not found", null, null,
                false, false, false, "Ce compte n'appartient pas à cet établissement.");
        boolean compatible = expectedType.equals(account.getAccountType()) && account.isActive() && account.isPostingAllowed()
                && (account.getCurrency() == null || account.getCurrency().equals(currency));
        String message = compatible ? null : "Compte incompatible: type, état ou devise à vérifier.";
        return new AccountRef(account.getId(), account.getCode(), account.getNameFr(), account.getNameEn(), account.getAccountType(),
                account.getCurrency(), account.isActive(), account.isPostingAllowed(), compatible, message);
    }

    private String typeEffectiveStatus(FeeType type, FeeTypeRevision current) {
        if ("DRAFT".equals(type.getLifecycle())) return "DRAFT";
        if ("INACTIVE".equals(type.getLifecycle())) return "INACTIVE";
        return current == null ? "NEEDS_REVISION" : revisionEffectiveStatus(current);
    }

    private String revisionEffectiveStatus(FeeTypeRevision row) {
        if (!"ACTIVE".equals(row.getRevisionStatus())) return row.getRevisionStatus();
        LocalDate today = LocalDate.now();
        if (row.getEffectiveFrom() == null) return "NEEDS_DATES";
        if (today.isBefore(row.getEffectiveFrom())) return "NOT_YET_EFFECTIVE";
        if (row.getEffectiveTo() != null && today.isAfter(row.getEffectiveTo())) return "EXPIRED";
        return "EFFECTIVE";
    }

    private String latestCategory(FeeType type) {
        return revisions.findTopBySchoolIdAndFeeTypeIdAndRevisionStatusOrderByRevisionNoDesc(
                type.getSchoolId(), type.getId(), "ACTIVE")
                .or(() -> revisions.findTopBySchoolIdAndFeeTypeIdAndRevisionStatusOrderByRevisionNoDesc(
                        type.getSchoolId(), type.getId(), "DRAFT"))
                .map(FeeTypeRevision::getCategory).orElse("");
    }

    private String latestName(FeeType type, boolean french) {
        return revisions.findBySchoolIdAndFeeTypeIdOrderByRevisionNoDesc(type.getSchoolId(), type.getId()).stream()
                .findFirst().map(r -> french ? r.getNameFr() : r.getNameEn()).orElse("");
    }

    private static FeeTypeRevision copy(FeeTypeRevision base) {
        FeeTypeRevision row = new FeeTypeRevision();
        row.setSchoolId(base.getSchoolId());
        row.setFeeTypeId(base.getFeeTypeId());
        row.setNameFr(base.getNameFr());
        row.setNameEn(base.getNameEn());
        row.setDescriptionFr(base.getDescriptionFr());
        row.setDescriptionEn(base.getDescriptionEn());
        row.setCategory(base.getCategory());
        row.setDefaultAmountMinor(base.getDefaultAmountMinor());
        row.setDefaultCurrency(base.getDefaultCurrency());
        row.setFrequency(base.getFrequency());
        row.setMandatory(base.isMandatory());
        row.setRefundable(base.isRefundable());
        row.setTaxable(base.isTaxable());
        row.setTaxBasisPoints(base.getTaxBasisPoints());
        row.setReceivableAccountId(base.getReceivableAccountId());
        row.setRevenueAccountId(base.getRevenueAccountId());
        row.setEffectiveFrom(base.getEffectiveFrom());
        row.setEffectiveTo(base.getEffectiveTo());
        return row;
    }

    private static FeeTypeRevisionInput toInput(FeeTypeRevision row) {
        return new FeeTypeRevisionInput(row.getNameFr(), row.getNameEn(), row.getDescriptionFr(), row.getDescriptionEn(),
                row.getCategory(), row.getDefaultAmountMinor(), row.getDefaultCurrency(), row.getFrequency(), row.isMandatory(),
                row.isRefundable(), row.isTaxable(), row.getTaxBasisPoints(), row.getReceivableAccountId(), row.getRevenueAccountId(),
                row.getEffectiveFrom(), row.getEffectiveTo(), row.getVersion());
    }

    private static void apply(FeeTypeRevision row, NormalizedRevision value) {
        row.setNameFr(value.nameFr());
        row.setNameEn(value.nameEn());
        row.setDescriptionFr(value.descriptionFr());
        row.setDescriptionEn(value.descriptionEn());
        row.setCategory(value.category());
        row.setDefaultAmountMinor(value.amountMinor());
        row.setDefaultCurrency(value.currency());
        row.setFrequency(value.frequency());
        row.setMandatory(value.mandatory());
        row.setRefundable(value.refundable());
        row.setTaxable(value.taxable());
        row.setTaxBasisPoints(value.taxBasisPoints());
        row.setReceivableAccountId(value.receivableAccountId());
        row.setRevenueAccountId(value.revenueAccountId());
        row.setEffectiveFrom(value.effectiveFrom());
        row.setEffectiveTo(value.effectiveTo());
    }

    private static String dependencyLabel(FeeTypeDependency d) {
        StringBuilder label = new StringBuilder(d.label() == null ? d.entityId() : d.label());
        if (d.sessionLabel() != null) label.append(" · session ").append(d.sessionLabel());
        if (d.classLabel() != null) label.append(" · classe ").append(d.classLabel());
        return label.toString();
    }

    private Set<String> columns(String table) {
        if (!tableExists(table)) return Set.of();
        return jdbc.query("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=?",
                (rs, rowNum) -> rs.getString(1), table).stream().collect(Collectors.toSet());
    }

    private boolean tableExists(String table) {
        try {
            return !jdbc.query("SELECT to_regclass(?)", (rs, rowNum) -> rs.getString(1), "public." + table).isEmpty()
                    && jdbc.query("SELECT to_regclass(?)", (rs, rowNum) -> rs.getString(1), "public." + table).getFirst() != null;
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private static String first(Set<String> columns, String... choices) {
        return Arrays.stream(choices).filter(columns::contains).findFirst().orElse(null);
    }

    private static String expression(Set<String> columns, String first, String second, String fallback, String alias) {
        if (columns.contains(first) && columns.contains(second)) return "COALESCE(NULLIF(" + alias + "." + first + ",''), NULLIF(" + alias + "." + second + ",''), " + fallback + ")";
        if (columns.contains(first)) return "COALESCE(NULLIF(" + alias + "." + first + ",''), " + fallback + ")";
        if (columns.contains(second)) return "COALESCE(NULLIF(" + alias + "." + second + ",''), " + fallback + ")";
        return fallback;
    }

    private static String sessionLabelExpression(String sessionColumn) {
        return "COALESCE(s.label, s.code, CAST(p." + sessionColumn + " AS text))";
    }

    private static String nullToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String normalizeCode(String value) {
        String normalized = value == null ? "" : Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]+", "_").replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank() || normalized.length() > 64 || !normalized.matches("[A-Z0-9_]{1,64}")) {
            throw ApiException.structured(HttpStatus.BAD_REQUEST, "INVALID_FEE_TYPE_CODE",
                    "Le code doit contenir 1 à 64 lettres majuscules, chiffres ou underscores.",
                    Map.of("code", "Exemple : TUITION ou TRANSPORT_TERM."), List.of());
        }
        return normalized;
    }

    private static String suggestedCode(String value, int index) {
        String normalized = value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) normalized = "LEGACY_ITEM_" + index;
        return normalized.substring(0, Math.min(normalized.length(), 56));
    }

    private static String uniqueSuggestedCode(String base, Set<String> existing, Set<String> assigned) {
        String prefix = base.substring(0, Math.min(base.length(), 56));
        int n = 2;
        String candidate = prefix;
        while (existing.contains(candidate) || assigned.contains(candidate)) {
            String suffix = "_" + n++;
            candidate = prefix.substring(0, Math.min(prefix.length(), 64 - suffix.length())) + suffix;
        }
        assigned.add(candidate);
        return candidate;
    }

    private static String normalizeCategory(String value) {
        String category = normalizeToken(value);
        return category.isBlank() ? "OTHER" : category;
    }

    private static String category(Map<String, Object> item) {
        String value = firstString(item, "category", "type");
        String token = normalizeCategory(value);
        return CATEGORIES.contains(token) ? token : "OTHER";
    }

    private static String normalizeCurrency(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String normalizeOptionalToken(String value) {
        String token = normalizeToken(value);
        return token.isBlank() ? null : token;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToNull(String value) { return trim(value); }

    private static String firstString(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return null;
    }

    private static long amount(Map<String, Object> item) {
        Object value = item.get("amount");
        if (value == null) value = item.get("defaultAmount");
        if (value instanceof Number n) return Math.max(0, n.longValue());
        try { return Math.max(0, Math.round(Double.parseDouble(String.valueOf(value)))); }
        catch (Exception ignored) { return 0; }
    }

    private static void compare(List<ComparisonField> out, String field, Object left, Object right) {
        if (!Objects.equals(left, right)) out.add(new ComparisonField(field, String.valueOf(left), String.valueOf(right)));
    }

    private static UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private record NormalizedRevision(String nameFr, String nameEn, String descriptionFr, String descriptionEn,
                                      String category, long amountMinor, String currency, String frequency,
                                      boolean mandatory, boolean refundable, boolean taxable, int taxBasisPoints,
                                      UUID receivableAccountId, UUID revenueAccountId,
                                      LocalDate effectiveFrom, LocalDate effectiveTo) {}
}
