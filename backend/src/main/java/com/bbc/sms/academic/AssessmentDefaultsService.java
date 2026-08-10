package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.foundation.session.AcademicReportingPeriodRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Preview-first, transactional generation of one sequence evaluation per assigned subject. */
@Service
public class AssessmentDefaultsService {
    private static final String ONE_SEQUENCE = "ONE_SEQUENCE";
    private static final String ALL_SEQUENCES = "ALL_SEQUENCES";

    private final AcademicReportingPeriodRepository periods;
    private final AcademicAssessmentRepository assessments;
    private final CurriculumQueryService curriculum;
    private final TeacherScopeService teacherScope;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public AssessmentDefaultsService(AcademicReportingPeriodRepository periods,
                                     AcademicAssessmentRepository assessments,
                                     CurriculumQueryService curriculum,
                                     TeacherScopeService teacherScope,
                                     JdbcTemplate jdbc,
                                     AuditService audit) {
        this.periods = periods;
        this.assessments = assessments;
        this.curriculum = curriculum;
        this.teacherScope = teacherScope;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AssessmentDefaultsPreview preview(AssessmentDefaultsPreviewRequest request) {
        ScopeData scope = resolveScope(request);
        return buildPreview(request, scope);
    }

    @Transactional
    public AssessmentDefaultsApplyResponse apply(AssessmentDefaultsPreviewRequest request) {
        return apply(request, null);
    }

    @Transactional
    public AssessmentDefaultsApplyResponse apply(AssessmentDefaultsPreviewRequest request,
                                                 String idempotencyKey) {
        ScopeData scope = resolveScope(request);
        AssessmentDefaultsPreview preview = buildPreview(request, scope);
        if (request.scopeFingerprint() != null
                && !request.scopeFingerprint().isBlank()
                && !request.scopeFingerprint().equals(preview.scopeFingerprint())) {
            throw ApiException.coded(HttpStatus.CONFLICT, "ASSESSMENT_PREVIEW_STALE",
                    "La configuration de la classe ou de la session a changé depuis l’aperçu. Rechargez l’aperçu avant de créer les évaluations.");
        }

        Map<String, String> errors = new LinkedHashMap<>();
        List<AssessmentDefaultsRow> rows = preview.periods().stream()
                .flatMap(p -> p.rows().stream()).toList();
        for (AssessmentDefaultsRow row : rows) {
            if ("EXISTING".equals(row.status())) continue;
            for (String error : row.errors()) {
                String field = errorField(error);
                errors.put("rows." + row.clientRowId() + "." + field, errorMessage(error));
            }
        }
        validateSubmittedRows(request, rows, errors);
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROW_VALIDATION_FAILED",
                    "Corrigez les champs signalés avant de créer les évaluations.", errors,
                    List.of(), List.of(), null, null, null, Map.of());
        }

        List<AssessmentDefaultsRow> proposed = rows.stream()
                .filter(row -> !"EXISTING".equals(row.status())).toList();
        UUID batchId = UUID.randomUUID();
        UUID schoolId = TenantContext.get();
        int requested = proposed.size();
        jdbc.update("""
                INSERT INTO academic_assessment_generation_batch
                    (id,school_id,academic_session_id,class_id,mode,idempotency_key,
                     scope_fingerprint,requested_count,actor_user_id)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, batchId, schoolId, scope.scope().academicSessionId(), scope.scope().classId(),
                normalizeMode(request.mode()), idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim(),
                preview.scopeFingerprint(), requested, currentUserId());

        List<AcademicAssessment> toCreate = new ArrayList<>();
        int existingCount = preview.existingRows();
        int skippedCount = 0;
        for (AssessmentDefaultsRow row : proposed) {
            String code = row.proposedCode().trim().toUpperCase(Locale.ROOT);
            if (assessments.existsScoped(schoolId, row.reportingPeriodId(), scope.scope().classId(), row.subjectCode(), code)) {
                existingCount++;
                continue;
            }
            AcademicAssessment assessment = new AcademicAssessment();
            assessment.setSchoolId(schoolId);
            assessment.setAcademicSessionId(scope.scope().academicSessionId());
            assessment.setReportingPeriodId(row.reportingPeriodId());
            assessment.setClassId(scope.scope().classId());
            assessment.setSubjectCode(row.subjectCode());
            assessment.setCode(code);
            assessment.setLabel(row.proposedLabel().trim());
            assessment.setAssessmentType("SEQUENCE_EVALUATION");
            assessment.setMaxScore(row.maxScore());
            assessment.setWeight(row.weight());
            assessment.setMandatory(row.mandatory());
            assessment.setDisplayOrder(1);
            assessment.setSource("DEFAULT_GENERATED");
            assessment.setGenerationBatchId(batchId);
            toCreate.add(assessment);
        }
        try {
            assessments.saveAllAndFlush(toCreate);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.coded(HttpStatus.CONFLICT, "ASSESSMENT_CODE_ALREADY_EXISTS",
                    "Une évaluation avec ce code existe déjà pour cette matière et cette séquence. Rechargez l’aperçu et choisissez un autre code.");
        }

        int created = toCreate.size();
        jdbc.update("""
                UPDATE academic_assessment_generation_batch
                   SET created_count=?, existing_count=?, skipped_count=?,
                       result_summary=?::jsonb
                 WHERE id=? AND school_id=?
                """, created, existingCount, skippedCount,
                "{\"created\":" + created + ",\"existing\":" + existingCount
                        + ",\"skipped\":" + skippedCount + "}", batchId, schoolId);
        audit.record("ACADEMIC_ASSESSMENTS_GENERATED", "AcademicAssessmentGenerationBatch",
                batchId.toString(), null, Map.of("created", created, "existing", existingCount,
                        "mode", normalizeMode(request.mode()), "classId", scope.scope().classId()), null);
        return new AssessmentDefaultsApplyResponse(preview, batchId, created, existingCount, skippedCount);
    }

    private ScopeData resolveScope(AssessmentDefaultsPreviewRequest request) {
        String mode = normalizeMode(request.mode());
        if (ONE_SEQUENCE.equals(mode) && request.reportingPeriodId() == null) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, "REPORTING_PERIOD_REQUIRED",
                    "Sélectionnez une séquence pour préparer une évaluation.", "reportingPeriodId",
                    "Sélectionnez une séquence.");
        }
        teacherScope.assertClass(request.classId());
        CurriculumQueryService.Scope scope = curriculum.scope(request.academicSessionId(), request.classId());
        List<AcademicReportingPeriod> sessionPeriods = periods.findBySchoolIdAndAcademicSessionIdOrderByDisplayOrder(
                TenantContext.get(), request.academicSessionId());
        List<AcademicReportingPeriod> selected = sessionPeriods.stream()
                .filter(AcademicPeriodRules::isSequence)
                .filter(p -> ALL_SEQUENCES.equals(mode) || p.getId().equals(request.reportingPeriodId()))
                .toList();
        if (selected.isEmpty()) {
            throw ApiException.coded(HttpStatus.NOT_FOUND, "REPORTING_PERIOD_NOT_FOUND",
                    "La séquence sélectionnée est introuvable dans cette session.");
        }
        if (scope.subjects().isEmpty()) {
            throw ApiException.coded(HttpStatus.CONFLICT, "CLASS_CURRICULUM_EMPTY",
                    "Aucune matière n’est affectée à cette classe pour cette session. Configurez d’abord les matières de la classe.");
        }
        return new ScopeData(scope, selected);
    }

    private AssessmentDefaultsPreview buildPreview(AssessmentDefaultsPreviewRequest request,
                                                    ScopeData data) {
        String mode = normalizeMode(request.mode());
        Map<String, AssessmentDefaultsRowInput> inputs = (request.rows() == null ? List.<AssessmentDefaultsRowInput>of() : request.rows())
                .stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(AssessmentDefaultsRowInput::clientRowId, x -> x, (a, b) -> b,
                        LinkedHashMap::new));
        List<AssessmentDefaultsPeriod> periodViews = new ArrayList<>();
        int excluded = 0;
        for (AcademicReportingPeriod period : data.periods()) {
            List<CurriculumQueryService.SubjectRow> subjects = curriculum.applicable(data.scope(), period);
            excluded += data.scope().subjects().size() - subjects.size();
            List<AssessmentDefaultsRow> rows = new ArrayList<>();
            for (CurriculumQueryService.SubjectRow subject : subjects) {
                String clientRowId = period.getCode() + ":" + subject.subjectCode().toUpperCase(Locale.ROOT);
                List<AcademicAssessment> existing = assessments.findScopedForSubject(
                        TenantContext.get(), period.getId(), data.scope().classId(), subject.subjectCode());
                AcademicAssessment current = existing.isEmpty() ? null : existing.get(0);
                AssessmentDefaultsRowInput input = inputs.get(clientRowId);
                String proposedCode = input != null && input.code() != null && !input.code().isBlank()
                        ? input.code() : defaultCode(period, subject);
                String proposedLabel = input != null && input.label() != null && !input.label().isBlank()
                        ? input.label() : defaultLabel(period, subject, data.scope().contentLanguage());
                BigDecimal maxScore = input != null && input.maxScore() != null ? input.maxScore() : subject.maxScore();
                BigDecimal weight = input != null && input.weight() != null ? input.weight() : BigDecimal.ONE;
                boolean mandatory = input != null && input.mandatory() != null ? input.mandatory() : subject.mandatory();
                List<String> errors = current == null ? validate(proposedCode, proposedLabel, maxScore, weight) : List.of();
                rows.add(new AssessmentDefaultsRow(clientRowId, period.getId(), period.getCode(), period.getLabel(),
                        subject.curriculumSubjectId(), subject.subjectCode().toUpperCase(Locale.ROOT),
                        subject.label(data.scope().contentLanguage()), subject.coefficient(), maxScore, weight,
                        mandatory, subject.teacherId(), subject.teacherName(),
                        subject.teacherStatus(), proposedCode, proposedLabel,
                        current == null ? (errors.isEmpty() ? "PROPOSED" : "INVALID") : "EXISTING",
                        errors, current == null ? null : current.getId(), current == null ? 0 : current.getVersion()));
            }
            periodViews.add(new AssessmentDefaultsPeriod(period.getId(), period.getCode(), period.getLabel(), rows));
        }
        int existing = (int) periodViews.stream().flatMap(p -> p.rows().stream()).filter(r -> "EXISTING".equals(r.status())).count();
        int proposed = (int) periodViews.stream().flatMap(p -> p.rows().stream()).filter(r -> !"EXISTING".equals(r.status())).count();
        String fingerprint = fingerprint(data.scope(), data.periods());
        return new AssessmentDefaultsPreview(data.scope().academicSessionId(), data.scope().classId(),
                data.scope().className(), data.scope().subsystem(), data.scope().contentLanguage(), mode,
                fingerprint, periodViews, existing + proposed, proposed, existing, excluded);
    }

    private void validateSubmittedRows(AssessmentDefaultsPreviewRequest request,
                                       List<AssessmentDefaultsRow> expected,
                                       Map<String, String> errors) {
        if (request.rows() == null) return;
        Set<String> allowed = expected.stream().map(AssessmentDefaultsRow::clientRowId).collect(Collectors.toSet());
        for (AssessmentDefaultsRowInput row : request.rows()) {
            if (row == null) continue;
            if (!allowed.contains(row.clientRowId())) {
                errors.put("rows." + row.clientRowId() + ".clientRowId", "Cette ligne n’appartient plus à la classe ou à la session sélectionnée.");
            }
        }
    }

    private static List<String> validate(String code, String label, BigDecimal maxScore, BigDecimal weight) {
        List<String> errors = new ArrayList<>();
        if (code == null || code.isBlank()) errors.add("code:ASSESSMENT_CODE_REQUIRED");
        else if (code.trim().length() > 40) errors.add("code:ASSESSMENT_CODE_TOO_LONG");
        if (label == null || label.isBlank()) errors.add("label:ASSESSMENT_LABEL_REQUIRED");
        else if (label.trim().length() > 160) errors.add("label:ASSESSMENT_LABEL_TOO_LONG");
        if (maxScore == null || maxScore.compareTo(BigDecimal.ZERO) <= 0) errors.add("maxScore:ASSESSMENT_MAX_SCORE_INVALID");
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) errors.add("weight:ASSESSMENT_WEIGHT_INVALID");
        return errors;
    }

    private static String errorField(String error) { return error.substring(0, error.indexOf(':')); }

    private static String errorMessage(String error) {
        return switch (error.substring(error.indexOf(':') + 1)) {
            case "ASSESSMENT_CODE_REQUIRED" -> "Le code est obligatoire.";
            case "ASSESSMENT_CODE_TOO_LONG" -> "Le code ne peut pas dépasser 40 caractères.";
            case "ASSESSMENT_LABEL_REQUIRED" -> "Le nom est obligatoire.";
            case "ASSESSMENT_LABEL_TOO_LONG" -> "Le nom ne peut pas dépasser 160 caractères.";
            case "ASSESSMENT_MAX_SCORE_INVALID" -> "Le barème doit être supérieur à zéro.";
            case "ASSESSMENT_WEIGHT_INVALID" -> "Le poids doit être supérieur à zéro.";
            default -> "Vérifiez cette valeur.";
        };
    }

    private static String defaultCode(AcademicReportingPeriod period, CurriculumQueryService.SubjectRow subject) {
        return (period.getCode() + "_" + subject.subjectCode()).toUpperCase(Locale.ROOT);
    }

    private static String defaultLabel(AcademicReportingPeriod period, CurriculumQueryService.SubjectRow subject,
                                       String language) {
        return "en".equalsIgnoreCase(language)
                ? "Evaluation " + period.getCode() + " · " + subject.label(language)
                : "Évaluation " + period.getCode() + " · " + subject.label(language);
    }

    private static String normalizeMode(String raw) {
        String mode = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!ONE_SEQUENCE.equals(mode) && !ALL_SEQUENCES.equals(mode)) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, "MODE_REQUIRED",
                    "Choisissez une séquence ou toutes les séquences.", "mode",
                    "Choisissez un mode de préparation.");
        }
        return mode;
    }

    private static String fingerprint(CurriculumQueryService.Scope scope,
                                      List<AcademicReportingPeriod> periods) {
        String payload = scope.academicSessionId() + "|" + scope.classId() + "|"
                + scope.subsystem() + "|" + periods.stream().map(p -> p.getId() + ":" + p.getVersion()
                + ":" + p.getStartDate() + ":" + p.getEndDate()).collect(Collectors.joining("|"))
                + "|" + scope.subjects().stream().map(s -> s.curriculumSubjectId() + ":" + s.version()
                + ":" + s.activeFrom() + ":" + s.activeTo()).collect(Collectors.joining("|"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot fingerprint the academic setup scope", ex);
        }
    }

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }

    private record ScopeData(CurriculumQueryService.Scope scope,
                             List<AcademicReportingPeriod> periods) {}
}
