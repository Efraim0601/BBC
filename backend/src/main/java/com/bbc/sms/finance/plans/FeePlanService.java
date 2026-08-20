package com.bbc.sms.finance.plans;

import com.bbc.sms.finance.accounting.ChartOfAccount;
import com.bbc.sms.finance.accounting.ChartOfAccountRepository;
import com.bbc.sms.finance.fees.FeeType;
import com.bbc.sms.finance.fees.FeeTypeRepository;
import com.bbc.sms.finance.fees.FeeTypeRevision;
import com.bbc.sms.finance.fees.FeeTypeRevisionRepository;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.bbc.sms.finance.plans.FeePlanDtos.*;

/** BAY-45 versioned plan lifecycle and student-resolution boundary. */
@Service
public class FeePlanService {
    private static final String XAF = "XAF";
    private static final Set<String> SCOPE_TYPES = Set.of("LEVEL", "CLASS");
    private static final Set<String> MERGE_MODES = Set.of("FILL_MISSING_ONLY", "REPLACE_TARGET_DRAFTS", "CREATE_NEW_VERSION");

    private final FeePlanRepository plans;
    private final FeePlanLineRepository lines;
    private final InstallmentTemplateRepository templates;
    private final InstallmentTemplateLineRepository templateLines;
    private final StudentFeeElectionRepository elections;
    private final StudentFeeOverrideRepository overrides;
    private final FeeTypeRepository feeTypes;
    private final FeeTypeRevisionRepository feeTypeRevisions;
    private final ChartOfAccountRepository accounts;
    private final AcademicSessionRepository sessions;
    private final StudentEnrollmentRepository enrollments;
    private final SchoolClassRepository classes;
    private final StudentRepository students;
    private final AuditService audit;

    @Autowired
    public FeePlanService(FeePlanRepository plans, FeePlanLineRepository lines,
                          InstallmentTemplateRepository templates,
                          InstallmentTemplateLineRepository templateLines,
                          StudentFeeElectionRepository elections,
                          StudentFeeOverrideRepository overrides,
                          FeeTypeRepository feeTypes,
                          FeeTypeRevisionRepository feeTypeRevisions,
                          ChartOfAccountRepository accounts,
                          AcademicSessionRepository sessions,
                          StudentEnrollmentRepository enrollments,
                          SchoolClassRepository classes, StudentRepository students, AuditService audit) {
        this.plans = plans;
        this.lines = lines;
        this.templates = templates;
        this.templateLines = templateLines;
        this.elections = elections;
        this.overrides = overrides;
        this.feeTypes = feeTypes;
        this.feeTypeRevisions = feeTypeRevisions;
        this.accounts = accounts;
        this.sessions = sessions;
        this.enrollments = enrollments;
        this.classes = classes;
        this.students = students;
        this.audit = audit;
    }

    /** Compatibility constructor retained for focused unit tests that predate student search. */
    public FeePlanService(FeePlanRepository plans, FeePlanLineRepository lines,
                          InstallmentTemplateRepository templates,
                          InstallmentTemplateLineRepository templateLines,
                          StudentFeeElectionRepository elections,
                          StudentFeeOverrideRepository overrides,
                          FeeTypeRepository feeTypes,
                          FeeTypeRevisionRepository feeTypeRevisions,
                          ChartOfAccountRepository accounts,
                          AcademicSessionRepository sessions,
                          StudentEnrollmentRepository enrollments,
                          SchoolClassRepository classes, AuditService audit) {
        this(plans, lines, templates, templateLines, elections, overrides, feeTypes,
                feeTypeRevisions, accounts, sessions, enrollments, classes, null, audit);
    }

    @Transactional(readOnly = true)
    public List<PlanView> list(UUID sessionId, String lifecycle) {
        UUID school = tenant();
        List<FeePlan> result = sessionId == null
                ? (lifecycle == null ? plans.findBySchoolIdAndLifecycleOrderByAcademicSessionIdAscLevelAscSubsystemAsc(school, "ACTIVE")
                : plans.findBySchoolIdAndLifecycleOrderByAcademicSessionIdAscLevelAscSubsystemAsc(school, token(lifecycle)))
                : plans.findBySchoolIdAndAcademicSessionIdOrderByLevelAscSubsystemAscSchoolClassIdAscPlanVersionNoDesc(school, sessionId);
        return result.stream().filter(p -> lifecycle == null || token(lifecycle).equals(p.getLifecycle()))
                .map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public PlanView detail(UUID id) { return view(requirePlan(id)); }

    @Transactional
    public PlanView createDraft(PlanCreateRequest in) {
        UUID school = tenant();
        AcademicSession session = sessions.findByIdAndSchoolId(in.academicSessionId(), school)
                .orElseThrow(() -> ApiException.notFound("Session académique"));
        String scope = token(in.scopeType());
        validateDates(in.effectiveFrom(), in.effectiveTo(), session);
        validateScope(scope, in.level(), in.subsystem(), in.schoolClassId());
        FeePlan plan = new FeePlan();
        plan.setSchoolId(school);
        plan.setAcademicSessionId(session.getId());
        plan.setScopeType(scope);
        plan.setLevel(clean(in.level()));
        plan.setSubsystem(clean(in.subsystem()));
        plan.setSchoolClassId(in.schoolClassId());
        plan.setPlanVersionNo(plans.nextVersion(school, session.getId(), scope, clean(in.level()), clean(in.subsystem()), in.schoolClassId()) + 1);
        plan.setLifecycle("DRAFT");
        plan.setEffectiveFrom(in.effectiveFrom());
        plan.setEffectiveTo(in.effectiveTo());
        plan.setCurrency(currency(in.currency()));
        plan.setCreatedBy(currentUserId());
        try {
            plan = plans.saveAndFlush(plan);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.structured(HttpStatus.CONFLICT, "FEE_PLAN_SCOPE_CONFLICT",
                    "Une version de plan existe déjà pour cette portée.", Map.of("scope", "Rechargez la portée sélectionnée."), List.of());
        }
        PlanView result = view(plan);
        audit.record("FEE_PLAN_DRAFT_CREATED", "FEE_PLAN", plan.getId().toString(), null, result, null);
        return result;
    }

    @Transactional
    public PlanView updateDraft(UUID id, PlanUpdateRequest in) {
        FeePlan plan = requirePlanForUpdate(id);
        requireVersion(in.version(), plan.getVersion(), "plan");
        requireDraft(plan);
        AcademicSession session = sessions.findByIdAndSchoolId(plan.getAcademicSessionId(), tenant())
                .orElseThrow(() -> ApiException.notFound("Session académique"));
        validateDates(in.effectiveFrom(), in.effectiveTo(), session);
        validateScope(plan.getScopeType(), in.level(), in.subsystem(), in.schoolClassId());
        PlanView before = view(plan);
        plan.setLevel(clean(in.level()));
        plan.setSubsystem(clean(in.subsystem()));
        plan.setSchoolClassId(in.schoolClassId());
        plan.setEffectiveFrom(in.effectiveFrom());
        plan.setEffectiveTo(in.effectiveTo());
        plan.setCurrency(currency(in.currency()));
        plan.setUpdatedBy(currentUserId());
        PlanView result = view(plans.saveAndFlush(plan));
        audit.record("FEE_PLAN_DRAFT_UPDATED", "FEE_PLAN", id.toString(), before, result, null);
        return result;
    }

    @Transactional
    public PlanView addLine(UUID planId, PlanLineRequest in) {
        FeePlan plan = requirePlanForUpdate(planId);
        requireVersion(in.version(), plan.getVersion(), "plan");
        requireDraft(plan);
        validateLine(plan, in, null);
        FeePlanLine line = new FeePlanLine();
        line.setSchoolId(tenant());
        line.setFeePlanId(planId);
        applyLine(line, in);
        lines.saveAndFlush(line);
        touch(plan);
        audit.record("FEE_PLAN_LINE_ADDED", "FEE_PLAN", planId.toString(), null, view(plan), null);
        return view(plan);
    }

    @Transactional
    public PlanView updateLine(UUID planId, UUID lineId, PlanLineRequest in) {
        FeePlan plan = requirePlanForUpdate(planId);
        requireVersion(in.version(), plan.getVersion(), "plan");
        requireDraft(plan);
        FeePlanLine line = lines.findByIdAndSchoolId(lineId, tenant())
                .filter(l -> planId.equals(l.getFeePlanId()))
                .orElseThrow(() -> ApiException.notFound("Ligne du plan"));
        validateLine(plan, in, lineId);
        applyLine(line, in);
        lines.saveAndFlush(line);
        touch(plan);
        audit.record("FEE_PLAN_LINE_UPDATED", "FEE_PLAN", planId.toString(), null, view(plan), null);
        return view(plan);
    }

    @Transactional
    public PlanView removeLine(UUID planId, UUID lineId, PlanActionRequest in) {
        FeePlan plan = requirePlanForUpdate(planId);
        requireVersion(in.version(), plan.getVersion(), "plan");
        requireDraft(plan);
        FeePlanLine line = lines.findByIdAndSchoolId(lineId, tenant())
                .filter(l -> planId.equals(l.getFeePlanId()))
                .orElseThrow(() -> ApiException.notFound("Ligne du plan"));
        lines.delete(line);
        lines.flush();
        touch(plan);
        audit.record("FEE_PLAN_LINE_REMOVED", "FEE_PLAN", planId.toString(), lineId, view(plan), in.reason());
        return view(plan);
    }

    @Transactional
    public TemplateView createTemplate(TemplateRequest in) {
        UUID school = tenant();
        String code = normalizeCode(in.code());
        if (templates.findBySchoolIdAndCode(school, code).isPresent()) {
            throw ApiException.structured(HttpStatus.CONFLICT, "INSTALLMENT_TEMPLATE_DUPLICATE",
                    "Le code du modèle d'échéancier est déjà utilisé.", Map.of("code", "Choisissez un code unique."), List.of());
        }
        InstallmentTemplate template = new InstallmentTemplate();
        template.setSchoolId(school);
        template.setCode(code);
        template.setNameFr(in.nameFr().trim());
        template.setNameEn(in.nameEn().trim());
        template.setSourceSessionId(in.sourceSessionId() == null ? null : requireSession(in.sourceSessionId()).getId());
        template.setLifecycle("ACTIVE");
        template.setCreatedBy(currentUserId());
        template = templates.saveAndFlush(template);
        saveTemplateLines(template, in.lines());
        TemplateView result = templateView(template);
        audit.record("FEE_PLAN_INSTALLMENT_TEMPLATE_CREATED", "INSTALLMENT_TEMPLATE", template.getId().toString(), null, result, null);
        return result;
    }

    @Transactional
    public TemplateView updateTemplate(UUID id, TemplateRequest in) {
        InstallmentTemplate template = templates.findByIdAndSchoolId(id, tenant())
                .orElseThrow(() -> ApiException.notFound("Modèle d'échéancier"));
        requireVersion(in.version(), template.getVersion(), "modèle d'échéancier");
        String code = normalizeCode(in.code());
        templates.findBySchoolIdAndCode(tenant(), code).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw field("INSTALLMENT_TEMPLATE_DUPLICATE", "Le code du modèle d'échéancier est déjà utilisé.",
                    Map.of("code", "Choisissez un code unique."));
        });
        template.setCode(code);
        template.setNameFr(in.nameFr().trim());
        template.setNameEn(in.nameEn().trim());
        template.setSourceSessionId(in.sourceSessionId() == null ? null : requireSession(in.sourceSessionId()).getId());
        template.setUpdatedBy(currentUserId());
        templateLines.deleteBySchoolIdAndTemplateId(tenant(), id);
        templateLines.flush();
        template = templates.saveAndFlush(template);
        saveTemplateLines(template, in.lines());
        TemplateView result = templateView(template);
        audit.record("FEE_PLAN_INSTALLMENT_TEMPLATE_UPDATED", "INSTALLMENT_TEMPLATE", id.toString(), null, result, null);
        return result;
    }

    @Transactional
    public void deleteTemplate(UUID id, PlanActionRequest request) {
        InstallmentTemplate template = templates.findByIdAndSchoolId(id, tenant())
                .orElseThrow(() -> ApiException.notFound("Modèle d'échéancier"));
        requireVersion(request.version(), template.getVersion(), "modèle d'échéancier");
        long usage = lines.countBySchoolIdAndInstallmentTemplateId(tenant(), id);
        if (usage > 0) {
            throw ApiException.structured(HttpStatus.CONFLICT, "INSTALLMENT_TEMPLATE_IN_USE",
                    "Le modèle est utilisé par des lignes de plans.", Map.of("templateId", "Retirez d'abord ce modèle des brouillons concernés."),
                    List.of(new ApiException.Blocker("INSTALLMENT_TEMPLATE", id.toString(), usage + " ligne(s) de plan", "OPEN_FEE_PLANS")));
        }
        templateLines.deleteBySchoolIdAndTemplateId(tenant(), id);
        templates.delete(template);
        audit.record("FEE_PLAN_INSTALLMENT_TEMPLATE_DELETED", "INSTALLMENT_TEMPLATE", id.toString(), null, null, request.reason());
    }

    @Transactional(readOnly = true)
    public List<TemplateView> listTemplates() {
        return templates.findBySchoolIdOrderByCode(tenant()).stream().map(this::templateView).toList();
    }

    @Transactional(readOnly = true)
    public TemplateView template(UUID id) {
        return templateView(templates.findByIdAndSchoolId(id, tenant())
                .orElseThrow(() -> ApiException.notFound("Modèle d'échéancier")));
    }

    @Transactional(readOnly = true)
    public InstallmentPreview installmentPreview(UUID planId, UUID lineId) {
        FeePlan plan = requirePlan(planId);
        FeePlanLine line = lines.findByIdAndSchoolId(lineId, tenant())
                .filter(value -> planId.equals(value.getFeePlanId()))
                .orElseThrow(() -> ApiException.notFound("Ligne du plan"));
        if (line.getInstallmentTemplateId() == null) {
            return new InstallmentPreview(planId, lineId, line.getAmountMinor(), line.getAmountMinor(), 0,
                    List.of(new InstallmentPreviewLine(1, "Total", "Total", line.getAmountMinor(), plan.getEffectiveFrom(), 0)), List.of());
        }
        InstallmentTemplate template = templates.findByIdAndSchoolId(line.getInstallmentTemplateId(), tenant())
                .orElseThrow(() -> ApiException.notFound("Modèle d'échéancier"));
        List<InstallmentTemplateLine> rows = templateLines.findBySchoolIdAndTemplateIdOrderByLineOrder(tenant(), template.getId());
        List<InstallmentPreviewLine> result = new ArrayList<>();
        long allocated = 0;
        int finalAdjustment = 0;
        for (int index = 0; index < rows.size(); index++) {
            InstallmentTemplateLine row = rows.get(index);
            long amount;
            int adjustment = 0;
            if ("FIXED".equals(row.getAllocationType())) amount = row.getAmountMinor() == null ? 0 : row.getAmountMinor();
            else if (index == rows.size() - 1) {
                amount = line.getAmountMinor() - allocated;
                long proportional = ((long) line.getAmountMinor() * (row.getPercentageBasisPoints() == null ? 0 : row.getPercentageBasisPoints())) / 10000L;
                adjustment = Math.toIntExact(amount - proportional);
                finalAdjustment = adjustment;
            } else amount = ((long) line.getAmountMinor() * (row.getPercentageBasisPoints() == null ? 0 : row.getPercentageBasisPoints())) / 10000L;
            allocated += amount;
            LocalDate dueDate = row.getAbsoluteDueDate();
            if (dueDate == null && "SESSION_START_OFFSET".equals(row.getDueRuleType())) dueDate = plan.getEffectiveFrom().plusDays(row.getDueOffsetDays() == null ? 0 : row.getDueOffsetDays());
            result.add(new InstallmentPreviewLine(row.getLineOrder(), row.getLabelFr(), row.getLabelEn(), amount, dueDate, adjustment));
        }
        List<String> blockers = allocated == line.getAmountMinor() ? List.of() : List.of("Les échéances totalisent " + allocated + " au lieu de " + line.getAmountMinor() + ".");
        return new InstallmentPreview(planId, lineId, line.getAmountMinor(), allocated, finalAdjustment, result, blockers);
    }

    @Transactional(readOnly = true)
    public ActivationPreview activationPreview(UUID id) {
        FeePlan plan = requirePlan(id);
        return activationPreviewFor(plan);
    }

    @Transactional
    public PlanView activate(UUID id, PlanActionRequest in) {
        FeePlan plan = requirePlanForUpdate(id);
        requireVersion(in.version(), plan.getVersion(), "plan");
        requireDraft(plan);
        ActivationPreview preview = activationPreviewFor(plan);
        if (!preview.blockers().isEmpty()) {
            throw ApiException.structured(HttpStatus.CONFLICT, "FEE_PLAN_ACTIVATION_BLOCKED",
                    "Le plan ne peut pas être activé tant que les bloqueurs ne sont pas résolus.",
                    Map.of(), preview.blockers().stream().map(b -> new ApiException.Blocker("FEE_PLAN", id.toString(), b, "OPEN_FEE_PLAN")).toList());
        }
        FeePlan old = plans.findActiveForUpdate(tenant(), plan.getAcademicSessionId(), plan.getScopeType(),
                plan.getLevel(), plan.getSubsystem(), plan.getSchoolClassId()).orElse(null);
        if (old != null && !old.getId().equals(id)) {
            old.setLifecycle("RETIRED");
            old.setRetiredBy(currentUserId());
            old.setRetiredAt(java.time.Instant.now());
            old.setSupersededByPlanId(id);
            plans.saveAndFlush(old);
            audit.record("FEE_PLAN_RETIRED", "FEE_PLAN", old.getId().toString(), null, view(old), "Remplacé par " + id);
        }
        plan.setLifecycle("ACTIVE");
        plan.setActivatedBy(currentUserId());
        plan.setActivatedAt(java.time.Instant.now());
        PlanView result = view(plans.saveAndFlush(plan));
        audit.record("FEE_PLAN_ACTIVATED", "FEE_PLAN", id.toString(), null, result, null);
        return result;
    }

    @Transactional(readOnly = true)
    public CopyPreview copyPreview(CopyPreviewRequest in) {
        FeePlan source = requirePlan(in.sourcePlanId());
        AcademicSession target = requireSession(in.targetSessionId());
        String mode = mergeMode(in.mergeMode());
        UUID classId = in.targetClassId() == null ? source.getSchoolClassId() : in.targetClassId();
        Scope targetScope = scope(source.getScopeType(), source.getLevel(), source.getSubsystem(), classId, target);
        return buildCopyPreview(source, target, targetScope, mode);
    }

    @Transactional(readOnly = true)
    public List<StudentContextView> studentContext(String query, UUID sessionId) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        UUID school = tenant();
        List<StudentContextView> result = new ArrayList<>();
        for (Student student : students.findBySchoolIdAndActiveTrueOrderByLastNameAsc(school)) {
            String name = ((student.getFirstName() == null ? "" : student.getFirstName()) + " "
                    + (student.getLastName() == null ? "" : student.getLastName())).trim();
            if (!needle.isBlank() && !(name.toLowerCase(Locale.ROOT).contains(needle)
                    || (student.getMatricule() != null && student.getMatricule().toLowerCase(Locale.ROOT).contains(needle)))) continue;
            for (StudentEnrollment enrollment : enrollments.findBySchoolIdAndStudentIdOrderByEnrolledOnDescCreatedAtDesc(school, student.getId())) {
                if (!"ACTIVE".equals(enrollment.getStatus()) || (sessionId != null && !sessionId.equals(enrollment.getAcademicSessionId()))) continue;
                AcademicSession session = sessions.findByIdAndSchoolId(enrollment.getAcademicSessionId(), school).orElse(null);
                result.add(new StudentContextView(enrollment.getId(), student.getId(), student.getMatricule(), name,
                        enrollment.getAcademicSessionId(), session == null ? enrollment.getAcademicSessionId().toString() : session.getLabel(),
                        enrollment.getSchoolClassId(), enrollment.getClassNameSnapshot(), enrollment.getLevelSnapshot(),
                        enrollment.getSubsystemSnapshot(), enrollment.getStatus()));
                if (result.size() >= 50) return result;
            }
        }
        return result;
    }

    @Transactional
    public PlanView copy(CopyApplyRequest in) {
        FeePlan source = requirePlan(in.sourcePlanId());
        requireVersion(in.sourceVersion(), source.getVersion(), "plan source");
        AcademicSession target = requireSession(in.targetSessionId());
        String mode = mergeMode(in.mergeMode());
        UUID classId = in.targetClassId() == null ? source.getSchoolClassId() : in.targetClassId();
        Scope targetScope = scope(source.getScopeType(), source.getLevel(), source.getSubsystem(), classId, target);
        CopyPreview preview = buildCopyPreview(source, target, targetScope, mode);
        if (!preview.blockers().isEmpty()) throw blocked("FEE_PLAN_COPY_BLOCKED", preview.blockers(), source.getId());
        FeePlan targetPlan = plans.findVersions(tenant(), target.getId(), targetScope.scopeType(), targetScope.level(),
                        targetScope.subsystem(), targetScope.classId()).stream()
                .filter(p -> "DRAFT".equals(p.getLifecycle())).findFirst().orElse(null);
        if ("CREATE_NEW_VERSION".equals(mode) || targetPlan == null) {
            targetPlan = new FeePlan();
            targetPlan.setSchoolId(tenant());
            targetPlan.setAcademicSessionId(target.getId());
            targetPlan.setScopeType(targetScope.scopeType());
            targetPlan.setLevel(targetScope.level());
            targetPlan.setSubsystem(targetScope.subsystem());
            targetPlan.setSchoolClassId(targetScope.classId());
            targetPlan.setPlanVersionNo(plans.nextVersion(tenant(), target.getId(), targetScope.scopeType(), targetScope.level(), targetScope.subsystem(), targetScope.classId()) + 1);
            targetPlan.setLifecycle("DRAFT");
            targetPlan.setEffectiveFrom(shiftDate(source.getEffectiveFrom(), requireSession(source.getAcademicSessionId()), target));
            targetPlan.setEffectiveTo(source.getEffectiveTo() == null ? null : shiftDate(source.getEffectiveTo(), requireSession(source.getAcademicSessionId()), target));
            targetPlan.setCurrency(source.getCurrency());
            targetPlan.setCreatedBy(currentUserId());
            targetPlan = plans.saveAndFlush(targetPlan);
        } else if ("REPLACE_TARGET_DRAFTS".equals(mode)) {
            lines.deleteAll(lines.findBySchoolIdAndFeePlanIdOrderByLineOrder(tenant(), targetPlan.getId()));
            lines.flush();
        }
        Set<UUID> existingTypes = new HashSet<>(lines.findBySchoolIdAndFeePlanIdOrderByLineOrder(tenant(), targetPlan.getId()).stream()
                .map(FeePlanLine::getFeeTypeId).toList());
        int order = lines.findBySchoolIdAndFeePlanIdOrderByLineOrder(tenant(), targetPlan.getId()).stream()
                .mapToInt(FeePlanLine::getLineOrder).max().orElse(0);
        for (FeePlanLine sourceLine : lines.findBySchoolIdAndFeePlanIdOrderByLineOrder(tenant(), source.getId())) {
            if ("FILL_MISSING_ONLY".equals(mode) && existingTypes.contains(sourceLine.getFeeTypeId())) continue;
            FeeTypeRevision revision = currentRevision(sourceLine.getFeeTypeId());
            FeePlanLine targetLine = new FeePlanLine();
            targetLine.setSchoolId(tenant());
            targetLine.setFeePlanId(targetPlan.getId());
            targetLine.setLineOrder(++order);
            targetLine.setFeeTypeId(sourceLine.getFeeTypeId());
            targetLine.setFeeTypeRevisionId(revision.getId());
            targetLine.setAmountMinor(sourceLine.getAmountMinor());
            targetLine.setCurrency(sourceLine.getCurrency());
            targetLine.setMandatory(sourceLine.isMandatory());
            targetLine.setRefundable(sourceLine.isRefundable());
            targetLine.setPriority(sourceLine.getPriority());
            targetLine.setInstallmentTemplateId(sourceLine.getInstallmentTemplateId());
            targetLine.setProrationPolicy(sourceLine.getProrationPolicy());
            lines.save(targetLine);
        }
        lines.flush();
        touch(targetPlan);
        PlanView result = view(targetPlan);
        audit.record("FEE_PLAN_COPIED", "FEE_PLAN", targetPlan.getId().toString(), null, result, mode);
        return result;
    }

    @Transactional(readOnly = true)
    public ResolutionView resolve(UUID enrollmentId) {
        StudentEnrollment enrollment = requireEnrollment(enrollmentId);
        FeePlan classPlan = active(enrollment, "CLASS", enrollment.getSchoolClassId());
        FeePlan plan = classPlan != null ? classPlan : active(enrollment, "LEVEL", null);
        if (plan == null) return new ResolutionView(enrollmentId, null, "NONE", "NO_ACTIVE_FEE_PLAN", null);
        return new ResolutionView(enrollmentId, plan.getId(), classPlan == null ? "LEVEL_PLAN" : "CLASS_OVERRIDE", null, view(plan));
    }

    @Transactional
    public OverrideView requestOverride(UUID planId, OverrideRequest in) {
        StudentEnrollment enrollment = requireEnrollment(in.enrollmentId());
        FeePlanLine line = requireLine(in.feePlanLineId());
        FeePlan plan = requirePlan(line.getFeePlanId());
        if (!plan.getId().equals(planId) || !plan.getAcademicSessionId().equals(enrollment.getAcademicSessionId())) {
            throw ApiException.notFound("Ligne du plan");
        }
        if (!"ACTIVE".equals(plan.getLifecycle())) throw blocked("FEE_PLAN_OVERRIDE_BLOCKED", List.of("Le plan doit être actif."), plan.getId());
        validateOverride(in, line);
        StudentFeeOverride override = new StudentFeeOverride();
        override.setSchoolId(tenant());
        override.setStudentEnrollmentId(enrollment.getId());
        override.setFeePlanLineId(line.getId());
        override.setOverrideType(token(in.overrideType()));
        override.setAmountMinor(in.amountMinor());
        override.setPercentageBasisPoints(in.percentageBasisPoints());
        override.setReason(in.reason().trim());
        override.setEffectiveFrom(in.effectiveFrom());
        override.setEffectiveTo(in.effectiveTo());
        override.setRequestedBy(currentUserId());
        OverrideView result = overrideView(overrides.saveAndFlush(override));
        audit.record("FEE_PLAN_OVERRIDE_REQUESTED", "STUDENT_FEE_OVERRIDE", override.getId().toString(), null, result, in.reason());
        return result;
    }

    /** Backward-compatible service entry point for callers that already validated the line id. */
    @Transactional
    public OverrideView requestOverride(OverrideRequest in) {
        return requestOverride(requireLine(in.feePlanLineId()).getFeePlanId(), in);
    }

    @Transactional
    public OverrideView decideOverride(UUID id, OverrideDecisionRequest in) {
        StudentFeeOverride override = overrides.findByIdAndSchoolId(id, tenant())
                .orElseThrow(() -> ApiException.notFound("Demande de dérogation"));
        requireVersion(in.version(), override.getVersion(), "demande de dérogation");
        if (!"REQUESTED".equals(override.getStatus())) throw ApiException.conflict("Cette demande a déjà été traitée.");
        override.setStatus(in.approve() ? "APPROVED" : "REJECTED");
        override.setApprovedBy(currentUserId());
        override.setApprovedAt(java.time.Instant.now());
        override.setDecisionReason(in.decisionReason());
        OverrideView result = overrideView(overrides.saveAndFlush(override));
        audit.record(in.approve() ? "FEE_PLAN_OVERRIDE_APPROVED" : "FEE_PLAN_OVERRIDE_REJECTED",
                "STUDENT_FEE_OVERRIDE", id.toString(), null, result, in.decisionReason());
        return result;
    }

    @Transactional(readOnly = true)
    public ImpactPreview impact(UUID planId, UUID enrollmentId, UUID lineId) {
        StudentEnrollment enrollment = requireEnrollment(enrollmentId);
        FeePlanLine line = requireLine(lineId);
        FeePlan plan = requirePlan(line.getFeePlanId());
        if (!plan.getId().equals(planId) || !plan.getAcademicSessionId().equals(enrollment.getAcademicSessionId())) {
            throw ApiException.notFound("Ligne du plan");
        }
        long base = line.getAmountMinor();
        List<StudentFeeOverride> relevant = overrides.findBySchoolIdAndStudentEnrollmentIdOrderByEffectiveFromDescCreatedAtDesc(tenant(), enrollment.getId()).stream()
                .filter(o -> o.getFeePlanLineId().equals(lineId) && "APPROVED".equals(o.getStatus()))
                .filter(o -> !o.getEffectiveFrom().isAfter(LocalDate.now()) && (o.getEffectiveTo() == null || !o.getEffectiveTo().isBefore(LocalDate.now())))
                .toList();
        if (relevant.isEmpty()) return new ImpactPreview(enrollmentId, lineId, base, base, 0, "Aucune dérogation approuvée active.", List.of());
        StudentFeeOverride o = relevant.get(0);
        long adjusted = switch (o.getOverrideType()) {
            case "AMOUNT" -> o.getAmountMinor();
            case "DISCOUNT" -> base - (base * o.getPercentageBasisPoints()) / 10000;
            case "EXEMPTION" -> 0;
            default -> base;
        };
        return new ImpactPreview(enrollmentId, lineId, base, adjusted, adjusted - base, o.getReason(), List.of());
    }

    /** Backward-compatible service entry point retained for existing callers. */
    @Transactional(readOnly = true)
    public ImpactPreview impact(UUID enrollmentId, UUID lineId) {
        return impact(requireLine(lineId).getFeePlanId(), enrollmentId, lineId);
    }

    @Transactional(readOnly = true)
    public List<OverrideView> overrides(UUID planId, UUID enrollmentId) {
        FeePlan plan = requirePlan(planId);
        requireEnrollment(enrollmentId);
        Set<UUID> lineIds = new HashSet<>(lines.findBySchoolIdAndFeePlanIdOrderByLineOrder(tenant(), plan.getId()).stream().map(FeePlanLine::getId).toList());
        return overrides.findBySchoolIdAndStudentEnrollmentIdOrderByEffectiveFromDescCreatedAtDesc(tenant(), enrollmentId).stream()
                .filter(item -> lineIds.contains(item.getFeePlanLineId())).map(this::overrideView).toList();
    }

    /** Backward-compatible unfiltered history lookup retained for existing callers. */
    @Transactional(readOnly = true)
    public List<OverrideView> overrides(UUID enrollmentId) {
        requireEnrollment(enrollmentId);
        return overrides.findBySchoolIdAndStudentEnrollmentIdOrderByEffectiveFromDescCreatedAtDesc(tenant(), enrollmentId)
                .stream().map(this::overrideView).toList();
    }

    @Transactional
    public ElectionView saveElection(UUID lineId, UUID enrollmentId, ElectionRequest in) {
        StudentEnrollment enrollment = requireEnrollment(enrollmentId);
        FeePlanLine line = requireLine(lineId);
        FeePlan plan = requirePlan(line.getFeePlanId());
        if (!plan.getAcademicSessionId().equals(enrollment.getAcademicSessionId())) throw ApiException.notFound("Ligne du plan");
        String status = token(in.status());
        if (!Set.of("PENDING", "ACCEPTED", "DECLINED").contains(status)) throw field("ELECTION_STATUS_INVALID", "Statut d'option invalide.", Map.of("status", "Choisissez une valeur valide."));
        StudentFeeElection election = elections.findBySchoolIdAndStudentEnrollmentIdAndFeePlanLineId(tenant(), enrollmentId, lineId).orElseGet(StudentFeeElection::new);
        if (election.getId() != null) requireVersion(in.version(), election.getVersion(), "option étudiant");
        election.setSchoolId(tenant()); election.setStudentEnrollmentId(enrollmentId); election.setFeePlanLineId(lineId);
        election.setStatus(status); election.setReason(in.reason()); election.setActedBy(currentUserId()); election.setActedAt(java.time.Instant.now());
        ElectionView result = electionView(elections.saveAndFlush(election));
        audit.record("FEE_PLAN_ELECTION_UPDATED", "STUDENT_FEE_ELECTION", result.id().toString(), null, result, in.reason());
        return result;
    }

    @Transactional(readOnly = true)
    public List<ElectionView> elections(UUID planId, UUID enrollmentId) {
        FeePlan plan = requirePlan(planId);
        requireEnrollment(enrollmentId);
        Set<UUID> lineIds = new HashSet<>(lines.findBySchoolIdAndFeePlanIdOrderByLineOrder(tenant(), plan.getId()).stream().map(FeePlanLine::getId).toList());
        return elections.findBySchoolIdAndStudentEnrollmentIdOrderByCreatedAtDesc(tenant(), enrollmentId).stream()
                .filter(item -> lineIds.contains(item.getFeePlanLineId())).map(this::electionView).toList();
    }

    /** Backward-compatible unfiltered election lookup retained for existing callers. */
    @Transactional(readOnly = true)
    public List<ElectionView> elections(UUID enrollmentId) {
        requireEnrollment(enrollmentId);
        return elections.findBySchoolIdAndStudentEnrollmentIdOrderByCreatedAtDesc(tenant(), enrollmentId)
                .stream().map(this::electionView).toList();
    }

    @Transactional(readOnly = true)
    public PlanContext context(UUID sessionId) {
        UUID school = tenant();
        List<Map<String, Object>> sessionViews = sessions.findBySchoolIdOrderByStartDateDesc(school).stream()
                .map(s -> Map.<String, Object>of("id", s.getId(), "code", s.getCode(), "label", s.getLabel(), "startDate", s.getStartDate(), "endDate", s.getEndDate()))
                .toList();
        List<Map<String, Object>> classViews = classes.findBySchoolIdOrderByName(school).stream()
                .map(c -> Map.<String, Object>of("id", c.getId(), "name", c.getName(), "level", c.getLevel(), "subsystem", c.getSubsystem()))
                .toList();
        return new PlanContext(sessionViews, classViews, list(sessionId, null));
    }

    private ActivationPreview activationPreviewFor(FeePlan plan) {
        List<String> missing = new ArrayList<>();
        for (FeePlanLine line : lines.findBySchoolIdAndFeePlanIdOrderByLineOrder(tenant(), plan.getId())) {
            try { validateLine(plan, new PlanLineRequest(line.getFeeTypeId(), line.getFeeTypeRevisionId(), line.getAmountMinor(), line.getCurrency(), line.isMandatory(), line.isRefundable(), line.getPriority(), line.getLineOrder(), line.getInstallmentTemplateId(), plan.getVersion()), line.getId()); }
            catch (ApiException ex) { missing.addAll(ex.getFieldErrors().values()); }
        }
        long affected = enrollmentCount(plan);
        long optional = lines.countBySchoolIdAndFeePlanIdAndMandatoryFalse(tenant(), plan.getId());
        List<String> duplicate = plans.findForScope(tenant(), plan.getAcademicSessionId(), plan.getScopeType(), plan.getLevel(), plan.getSubsystem(), plan.getSchoolClassId(), "ACTIVE")
                .stream().filter(p -> !p.getId().equals(plan.getId())).map(p -> "Plan actif " + p.getPlanVersionNo() + " sera retiré.").toList();
        List<String> blockers = new ArrayList<>(missing);
        if (lines.findBySchoolIdAndFeePlanIdOrderByLineOrder(tenant(), plan.getId()).isEmpty()) blockers.add("Le plan doit contenir au moins une ligne.");
        return new ActivationPreview(plan.getId(), blockers.isEmpty(), affected, optional, missing, duplicate, blockers,
                "Aucun frais posté ne sera modifié; la génération des charges est réservée à BAY-46.");
    }

    private CopyPreview buildCopyPreview(FeePlan source, AcademicSession target, Scope scope, String mode) {
        List<String> changedRevisions = new ArrayList<>();
        List<String> changedAmounts = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        for (FeePlanLine line : lines.findBySchoolIdAndFeePlanIdOrderByLineOrder(tenant(), source.getId())) {
            FeeTypeRevision current = feeTypeRevisions.findTopBySchoolIdAndFeeTypeIdAndRevisionStatusOrderByRevisionNoDesc(tenant(), line.getFeeTypeId(), "ACTIVE").orElse(null);
            if (current == null) blockers.add("Ligne " + line.getLineOrder() + ": aucune révision active.");
            else if (!current.getId().equals(line.getFeeTypeRevisionId())) changedRevisions.add("Ligne " + line.getLineOrder() + ": révision " + current.getRevisionNo());
        }
        List<FeePlan> drafts = plans.findVersions(tenant(), target.getId(), scope.scopeType(), scope.level(), scope.subsystem(), scope.classId()).stream()
                .filter(p -> "DRAFT".equals(p.getLifecycle())).toList();
        FeePlan comparison = drafts.stream().findFirst().orElse(null);
        if (comparison != null) {
            List<FeePlanLine> targetLines = lines.findBySchoolIdAndFeePlanIdOrderByLineOrder(tenant(), comparison.getId());
            for (FeePlanLine sourceLine : lines.findBySchoolIdAndFeePlanIdOrderByLineOrder(tenant(), source.getId())) {
                targetLines.stream().filter(targetLine -> Objects.equals(targetLine.getFeeTypeId(), sourceLine.getFeeTypeId())).findFirst()
                        .ifPresent(targetLine -> {
                            if (targetLine.getAmountMinor() != sourceLine.getAmountMinor()) {
                                changedAmounts.add("Ligne " + sourceLine.getLineOrder() + ": " + targetLine.getAmountMinor() + " → " + sourceLine.getAmountMinor() + " XAF");
                            }
                        });
            }
        }
        List<String> missingClasses = new ArrayList<>();
        if (scope.classId() == null) {
            classes.findBySchoolIdOrderByName(tenant()).stream()
                    .filter(c -> clean(c.getLevel()).equals(scope.level()) && clean(c.getSubsystem()).equals(scope.subsystem()))
                    .filter(c -> plans.findForScope(tenant(), target.getId(), "CLASS", scope.level(), scope.subsystem(), c.getId(), "ACTIVE").isEmpty())
                    .forEach(c -> missingClasses.add(c.getName() + " (aucun plan de classe actif)"));
        }
        List<String> existing = drafts.stream().map(p -> "Brouillon v" + p.getPlanVersionNo()).toList();
        if (!drafts.isEmpty() && "CREATE_NEW_VERSION".equals(mode)) existing = List.of("Brouillon existant conservé: v" + drafts.get(0).getPlanVersionNo());
        String dateShift = "Décalage relatif: " + ChronoUnit.DAYS.between(requireSession(source.getAcademicSessionId()).getStartDate(), target.getStartDate()) + " jours";
        return new CopyPreview(source.getId(), target.getId(), scope.classId(), mode, changedRevisions, missingClasses, changedAmounts, existing, blockers, dateShift);
    }

    private void validateLine(FeePlan plan, PlanLineRequest in, UUID existingLineId) {
        UUID school = tenant();
        FeeType type = feeTypes.findByIdAndSchoolId(in.feeTypeId(), school)
                .orElseThrow(() -> field("FEE_TYPE_NOT_FOUND", "Type de frais introuvable.", Map.of("feeTypeId", "Sélectionnez un type de frais de cette école.")));
        if (!"ACTIVE".equals(type.getLifecycle())) throw field("FEE_TYPE_NOT_ACTIVE", "Le type de frais doit être actif.", Map.of("feeTypeId", "Choisissez un type actif."));
        FeeTypeRevision revision = feeTypeRevisions.findByIdAndSchoolId(in.feeTypeRevisionId(), school)
                .orElseThrow(() -> field("FEE_TYPE_REVISION_NOT_FOUND", "Révision introuvable.", Map.of("feeTypeRevisionId", "Choisissez une révision du catalogue.")));
        if (!revision.getFeeTypeId().equals(type.getId()) || !"ACTIVE".equals(revision.getRevisionStatus())) throw field("FEE_TYPE_REVISION_NOT_ACTIVE", "La révision choisie n'est pas active pour ce type.", Map.of("feeTypeRevisionId", "Choisissez la révision active."));
        if (revision.getEffectiveFrom() != null && revision.getEffectiveFrom().isAfter(plan.getEffectiveFrom())) throw field("FEE_TYPE_REVISION_NOT_EFFECTIVE", "La révision n'est pas effective à la date du plan.", Map.of("feeTypeRevisionId", "Avancez la date ou choisissez une révision effective."));
        if (revision.getEffectiveTo() != null && plan.getEffectiveTo() != null && revision.getEffectiveTo().isBefore(plan.getEffectiveTo())) throw field("FEE_TYPE_REVISION_EXPIRES", "La révision expire avant la fin du plan.", Map.of("feeTypeRevisionId", "Choisissez une révision couvrant toute la période."));
        if (!currency(in.currency()).equals(currency(plan.getCurrency())) || !currency(revision.getDefaultCurrency()).equals(currency(plan.getCurrency()))) throw field("FEE_PLAN_CURRENCY_MISMATCH", "La devise de la ligne doit correspondre au plan.", Map.of("currency", "La devise doit être " + plan.getCurrency() + "."));
        requireAccount(revision.getReceivableAccountId(), "receivableAccountId", "ASSET", revision.getDefaultCurrency());
        requireAccount(revision.getRevenueAccountId(), "revenueAccountId", "REVENUE", revision.getDefaultCurrency());
        if (in.installmentTemplateId() != null) {
            InstallmentTemplate template = templates.findByIdAndSchoolId(in.installmentTemplateId(), school).orElseThrow(() -> ApiException.notFound("Modèle d'échéancier"));
            validateTemplateForAmount(template, in.amountMinor());
        }
        String proration = token(in.prorationPolicy());
        if (proration.isBlank()) proration = "NONE";
        if (!Set.of("NONE", "DAILY", "MONTHLY").contains(proration)) {
            throw field("PRORATION_POLICY_INVALID", "La politique de prorata est invalide.",
                    Map.of("prorationPolicy", "Choisissez NONE, DAILY ou MONTHLY."));
        }
        boolean duplicate = lines.findBySchoolIdAndFeePlanIdOrderByLineOrder(school, plan.getId()).stream()
                .anyMatch(l -> l.getFeeTypeRevisionId().equals(in.feeTypeRevisionId()) && !l.getId().equals(existingLineId));
        if (duplicate) throw field("FEE_PLAN_LINE_DUPLICATE", "La révision est déjà présente dans ce plan.", Map.of("feeTypeRevisionId", "Utilisez une seule ligne par révision."));
    }

    private void requireAccount(UUID id, String field, String expected, String currency) {
        if (id == null) throw field("FEE_TYPE_ACCOUNT_MAPPING_MISSING", "La révision du catalogue n'a pas les comptes requis.", Map.of(field, "Ajoutez un compte " + expected + " avant de l'utiliser."));
        ChartOfAccount account = accounts.findByIdAndSchoolId(id, tenant()).orElse(null);
        if (account == null || !account.isActive() || !account.isPostingAllowed() || !expected.equals(account.getAccountType()) || (account.getCurrency() != null && !currency(account.getCurrency()).equals(currency(currency)))) {
            throw field("FEE_TYPE_ACCOUNT_MAPPING_INVALID", "Le compte lié n'est pas compatible avec la ligne.", Map.of(field, "Choisissez un compte actif de type " + expected + " en " + currency + "."));
        }
    }

    private void validateTemplateForAmount(InstallmentTemplate template, long amount) {
        List<InstallmentTemplateLine> rows = templateLines.findBySchoolIdAndTemplateIdOrderByLineOrder(tenant(), template.getId());
        long fixed = rows.stream().filter(r -> "FIXED".equals(r.getAllocationType())).mapToLong(r -> r.getAmountMinor() == null ? 0 : r.getAmountMinor()).sum();
        int percentage = rows.stream().filter(r -> "PERCENTAGE".equals(r.getAllocationType())).mapToInt(r -> r.getPercentageBasisPoints() == null ? 0 : r.getPercentageBasisPoints()).sum();
        if (rows.isEmpty() || (fixed != amount && percentage != 10000)) throw field("INSTALLMENT_TOTAL_MISMATCH", "Les échéances ne totalisent pas exactement la ligne.", Map.of("installments", "Montant fixe: " + fixed + "; pourcentage: " + percentage + "/10000; total attendu: " + amount + ". La dernière échéance reçoit l'ajustement d'arrondi."));
    }

    private void saveTemplateLines(InstallmentTemplate template, List<TemplateLineRequest> input) {
        if (input == null || input.isEmpty()) throw field("INSTALLMENT_TEMPLATE_EMPTY", "Un modèle doit avoir au moins une échéance.", Map.of("lines", "Ajoutez une échéance."));
        Set<Integer> orders = new HashSet<>();
        int percentage = 0; long fixed = 0;
        for (TemplateLineRequest in : input) {
            if (!orders.add(in.lineOrder())) throw field("INSTALLMENT_LINE_ORDER_DUPLICATE", "Deux échéances ont le même ordre.", Map.of("lines", "Réordonnez les échéances."));
            String allocation = token(in.allocationType());
            if ("FIXED".equals(allocation)) { if (in.amountMinor() == null || in.amountMinor() < 0 || in.percentageBasisPoints() != null) throw field("INSTALLMENT_ALLOCATION_INVALID", "Allocation fixe invalide.", Map.of("lines", "Un montant fixe est requis.")); fixed += in.amountMinor(); }
            else if ("PERCENTAGE".equals(allocation)) { if (in.percentageBasisPoints() == null || in.percentageBasisPoints() < 0 || in.percentageBasisPoints() > 10000 || in.amountMinor() != null) throw field("INSTALLMENT_ALLOCATION_INVALID", "Allocation en pourcentage invalide.", Map.of("lines", "Un pourcentage de 0 à 10000 est requis.")); percentage += in.percentageBasisPoints(); }
            else throw field("INSTALLMENT_ALLOCATION_INVALID", "Type d'allocation invalide.", Map.of("allocationType", "FIXED ou PERCENTAGE."));
            validateDueRule(in);
            InstallmentTemplateLine row = new InstallmentTemplateLine();
            row.setSchoolId(tenant()); row.setTemplateId(template.getId()); row.setLineOrder(in.lineOrder()); row.setLabelFr(in.labelFr().trim()); row.setLabelEn(in.labelEn().trim()); row.setAllocationType(allocation); row.setAmountMinor(in.amountMinor()); row.setPercentageBasisPoints(in.percentageBasisPoints()); row.setDueRuleType(token(in.dueRuleType())); row.setAbsoluteDueDate(in.absoluteDueDate()); row.setDueOffsetDays(in.dueOffsetDays()); row.setAcademicTermId(in.academicTermId());
            templateLines.save(row);
        }
        if (fixed > 0 && percentage > 0) throw field("INSTALLMENT_ALLOCATION_MIXED", "Un modèle ne peut pas mélanger les montants fixes et les pourcentages.", Map.of("lines", "Utilisez une seule méthode d'allocation."));
        if (fixed == 0 && percentage != 10000) throw field("INSTALLMENT_PERCENTAGES_MISMATCH", "Les pourcentages doivent totaliser 10000 points de base.", Map.of("lines", "Ajustez le total à 10000."));
        templateLines.flush();
    }

    private void validateDueRule(TemplateLineRequest in) {
        String due = token(in.dueRuleType());
        if ("ABSOLUTE_DATE".equals(due) && in.absoluteDueDate() == null) throw field("INSTALLMENT_DUE_RULE_INVALID", "Une date absolue est requise.", Map.of("absoluteDueDate", "Renseignez la date."));
        if (!"ABSOLUTE_DATE".equals(due) && in.dueOffsetDays() == null) throw field("INSTALLMENT_DUE_RULE_INVALID", "Un décalage est requis.", Map.of("dueOffsetDays", "Renseignez le nombre de jours."));
        if (!Set.of("ABSOLUTE_DATE", "SESSION_START_OFFSET", "TERM_START_OFFSET", "TERM_END_OFFSET").contains(due)) throw field("INSTALLMENT_DUE_RULE_INVALID", "Règle d'échéance invalide.", Map.of("dueRuleType", "Choisissez une règle reconnue."));
    }

    private long enrollmentCount(FeePlan plan) {
        if ("CLASS".equals(plan.getScopeType())) return enrollments.findBySchoolIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByClassNameSnapshotAsc(tenant(), plan.getAcademicSessionId(), plan.getSchoolClassId(), "ACTIVE").size();
        return enrollments.findBySchoolIdAndAcademicSessionIdAndLevelSnapshotAndSubsystemSnapshotAndStatusOrderByClassNameSnapshotAsc(tenant(), plan.getAcademicSessionId(), plan.getLevel(), plan.getSubsystem(), "ACTIVE").size();
    }

    private FeePlan active(StudentEnrollment enrollment, String type, UUID classId) {
        return plans.findForScope(tenant(), enrollment.getAcademicSessionId(), type, clean(enrollment.getLevelSnapshot()), clean(enrollment.getSubsystemSnapshot()), classId, "ACTIVE").stream().findFirst().orElse(null);
    }

    private PlanView view(FeePlan plan) {
        List<PlanLineView> lineViews = lines.findBySchoolIdAndFeePlanIdOrderByLineOrder(tenant(), plan.getId()).stream().map(l -> new PlanLineView(l.getId(), l.getFeeTypeId(), l.getFeeTypeRevisionId(), l.getAmountMinor(), l.getCurrency(), l.isMandatory(), l.isRefundable(), l.getPriority(), l.getLineOrder(), l.getInstallmentTemplateId(), l.getProrationPolicy(), l.getVersion())).toList();
        return new PlanView(plan.getId(), plan.getAcademicSessionId(), plan.getScopeType(), plan.getLevel(), plan.getSubsystem(), plan.getSchoolClassId(), plan.getPlanVersionNo(), plan.getLifecycle(), plan.getEffectiveFrom(), plan.getEffectiveTo(), plan.getCurrency(), "CLASS".equals(plan.getScopeType()) ? "CLASS_OVERRIDE" : "LEVEL_PLAN", effectiveStatus(plan), plan.getVersion(), lineViews.stream().mapToLong(PlanLineView::amountMinor).sum(), lineViews.stream().filter(l -> !l.mandatory()).count(), lineViews);
    }

    private TemplateView templateView(InstallmentTemplate template) {
        List<TemplateLineView> rows = templateLines.findBySchoolIdAndTemplateIdOrderByLineOrder(tenant(), template.getId()).stream().map(l -> new TemplateLineView(l.getId(), l.getLineOrder(), l.getLabelFr(), l.getLabelEn(), l.getAllocationType(), l.getAmountMinor(), l.getPercentageBasisPoints(), l.getDueRuleType(), l.getAbsoluteDueDate(), l.getDueOffsetDays(), l.getAcademicTermId(), l.getVersion())).toList();
        return new TemplateView(template.getId(), template.getCode(), template.getNameFr(), template.getNameEn(), template.getLifecycle(), template.getSourceSessionId(), template.getVersion(), rows);
    }

    private OverrideView overrideView(StudentFeeOverride o) { return new OverrideView(o.getId(), o.getStudentEnrollmentId(), o.getFeePlanLineId(), o.getOverrideType(), o.getAmountMinor(), o.getPercentageBasisPoints(), o.getReason(), o.getStatus(), o.getEffectiveFrom(), o.getEffectiveTo(), o.getVersion()); }
    private ElectionView electionView(StudentFeeElection e) { return new ElectionView(e.getId(), e.getStudentEnrollmentId(), e.getFeePlanLineId(), e.getStatus(), e.getReason(), e.getVersion()); }

    private void applyLine(FeePlanLine line, PlanLineRequest in) { line.setFeeTypeId(in.feeTypeId()); line.setFeeTypeRevisionId(in.feeTypeRevisionId()); line.setAmountMinor(in.amountMinor()); line.setCurrency(currency(in.currency())); line.setMandatory(in.mandatory()); line.setRefundable(in.refundable()); line.setPriority(in.priority()); line.setLineOrder(in.lineOrder()); line.setInstallmentTemplateId(in.installmentTemplateId()); line.setProrationPolicy(in.prorationPolicy() == null || in.prorationPolicy().isBlank() ? "NONE" : token(in.prorationPolicy())); }
    private void touch(FeePlan p) { p.setUpdatedBy(currentUserId()); plans.saveAndFlush(p); }
    private FeePlan requirePlan(UUID id) { return plans.findByIdAndSchoolId(id, tenant()).orElseThrow(() -> ApiException.notFound("Plan de frais")); }
    private FeePlan requirePlanForUpdate(UUID id) { return plans.findByIdAndSchoolId(id, tenant()).orElseThrow(() -> ApiException.notFound("Plan de frais")); }
    private FeePlanLine requireLine(UUID id) { return lines.findByIdAndSchoolId(id, tenant()).orElseThrow(() -> ApiException.notFound("Ligne du plan")); }
    private StudentEnrollment requireEnrollment(UUID id) { return enrollments.findByIdAndSchoolId(id, tenant()).orElseThrow(() -> ApiException.notFound("Inscription")); }
    private AcademicSession requireSession(UUID id) { return sessions.findByIdAndSchoolId(id, tenant()).orElseThrow(() -> ApiException.notFound("Session académique")); }
    private FeeTypeRevision currentRevision(UUID feeTypeId) { return feeTypeRevisions.findTopBySchoolIdAndFeeTypeIdAndRevisionStatusOrderByRevisionNoDesc(tenant(), feeTypeId, "ACTIVE").orElseThrow(() -> ApiException.conflict("Aucune révision active pour le type de frais.")); }
    private void requireDraft(FeePlan p) { if (!"DRAFT".equals(p.getLifecycle())) throw ApiException.structured(HttpStatus.CONFLICT, "FEE_PLAN_IMMUTABLE", "Un plan actif ou retiré est immuable; créez une nouvelle version.", Map.of("lifecycle", "Créez un nouveau brouillon."), List.of(new ApiException.Blocker("FEE_PLAN", p.getId().toString(), "Plan v" + p.getPlanVersionNo(), "CREATE_FEE_PLAN_VERSION"))); }
    private void requireVersion(Long supplied, long current, String label) { if (supplied == null || supplied != current) throw ApiException.structured(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "Le " + label + " a changé ailleurs. Rechargez-le avant de réessayer.", Map.of("version", "La version affichée n'est plus actuelle."), List.of()); }
    private void validateScope(String type, String level, String subsystem, UUID classId) { if (!SCOPE_TYPES.contains(token(type))) throw field("FEE_PLAN_SCOPE_INVALID", "Portée de plan invalide.", Map.of("scopeType", "Choisissez LEVEL ou CLASS.")); if ("CLASS".equals(token(type))) { SchoolClass c = classes.findByIdAndSchoolId(classId, tenant()).orElseThrow(() -> field("FEE_PLAN_CLASS_NOT_FOUND", "Classe introuvable.", Map.of("schoolClassId", "Choisissez une classe de cette école."))); if (!clean(c.getLevel()).equals(clean(level)) || !clean(c.getSubsystem()).equals(clean(subsystem))) throw field("FEE_PLAN_CLASS_SCOPE_MISMATCH", "La classe ne correspond pas au niveau et au sous-système.", Map.of("schoolClassId", "Actualisez la portée depuis la classe.")); } }
    private void validateDates(LocalDate from, LocalDate to, AcademicSession session) { if (to != null && from.isAfter(to)) throw field("FEE_PLAN_DATES_INVALID", "Les dates du plan sont invalides.", Map.of("effectiveTo", "La fin doit être postérieure au début.")); if (from.isBefore(session.getStartDate()) || (to != null && to.isAfter(session.getEndDate()))) throw field("FEE_PLAN_DATES_OUTSIDE_SESSION", "Les dates doivent rester dans la session.", Map.of("effectiveFrom", "Utilisez les dates de la session sélectionnée.")); }
    private void validateOverride(OverrideRequest in, FeePlanLine line) { String type = token(in.overrideType()); if (!Set.of("AMOUNT", "DISCOUNT", "EXEMPTION").contains(type)) throw field("OVERRIDE_TYPE_INVALID", "Type de dérogation invalide.", Map.of("overrideType", "Choisissez AMOUNT, DISCOUNT ou EXEMPTION.")); if (in.effectiveTo() != null && in.effectiveFrom().isAfter(in.effectiveTo())) throw field("OVERRIDE_DATES_INVALID", "Dates de dérogation invalides.", Map.of("effectiveTo", "La fin doit suivre le début.")); if ("AMOUNT".equals(type) && (in.amountMinor() == null || in.amountMinor() < 0)) throw field("OVERRIDE_AMOUNT_INVALID", "Montant de dérogation invalide.", Map.of("amountMinor", "Renseignez un montant entier positif ou nul.")); if ("DISCOUNT".equals(type) && (in.percentageBasisPoints() == null || in.percentageBasisPoints() < 0 || in.percentageBasisPoints() > 10000)) throw field("OVERRIDE_PERCENTAGE_INVALID", "Pourcentage de dérogation invalide.", Map.of("percentageBasisPoints", "Utilisez 0 à 10000 points de base.")); if ("EXEMPTION".equals(type) && (in.amountMinor() != null || in.percentageBasisPoints() != null)) throw field("OVERRIDE_VALUE_NOT_ALLOWED", "Une exonération n'a pas de valeur numérique.", Map.of("amountMinor", "Laissez le montant vide.")); }
    private Scope scope(String type, String level, String subsystem, UUID classId, AcademicSession target) { validateScope(type, level, subsystem, classId); return new Scope(token(type), clean(level), clean(subsystem), classId); }
    private record Scope(String scopeType, String level, String subsystem, UUID classId) {}
    private UUID tenant() { return TenantContext.get(); }
    private String token(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String currency(String value) { return value == null || value.isBlank() ? XAF : value.trim().toUpperCase(Locale.ROOT); }
    private String normalizeCode(String value) { String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT); if (!code.matches("[A-Z0-9_]{1,64}")) throw field("INSTALLMENT_TEMPLATE_CODE_INVALID", "Le code du modèle est invalide.", Map.of("code", "Utilisez uniquement A-Z, 0-9 et _.")); return code; }
    private String effectiveStatus(FeePlan p) { LocalDate today = LocalDate.now(); if (p.getEffectiveFrom().isAfter(today)) return "FUTURE"; if (p.getEffectiveTo() != null && p.getEffectiveTo().isBefore(today)) return "EXPIRED"; return "EFFECTIVE"; }
    private String mergeMode(String value) { String mode = token(value); if (!MERGE_MODES.contains(mode)) throw field("MERGE_MODE_INVALID", "Mode de fusion invalide.", Map.of("mergeMode", "Choisissez un mode de fusion.")); return mode; }
    private LocalDate shiftDate(LocalDate date, AcademicSession source, AcademicSession target) { return date.plusDays(ChronoUnit.DAYS.between(source.getStartDate(), target.getStartDate())); }
    private ApiException field(String code, String message, Map<String, String> fields) { return ApiException.structured(HttpStatus.BAD_REQUEST, code, message, fields, List.of()); }
    private ApiException blocked(String code, List<String> blockers, UUID id) { return ApiException.structured(HttpStatus.CONFLICT, code, "Cette action est bloquée par des dépendances.", Map.of(), blockers.stream().map(b -> new ApiException.Blocker("FEE_PLAN", id.toString(), b, "OPEN_FEE_PLAN")).toList()); }
    private UUID currentUserId() { var auth = SecurityContextHolder.getContext().getAuthentication(); return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null; }
}
