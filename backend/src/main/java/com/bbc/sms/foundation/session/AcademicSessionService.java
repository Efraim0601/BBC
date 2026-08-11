package com.bbc.sms.foundation.session;

import com.bbc.sms.academic.AcademicPeriodRules;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.bbc.sms.foundation.session.SessionDtos.*;

@Service
public class AcademicSessionService {
    private static final List<String> STATES = List.of("DRAFT", "OPEN", "CLOSED", "ARCHIVED");
    private final AcademicSessionRepository sessions;
    private final AcademicTermRepository terms;
    private final AcademicReportingPeriodRepository reportingPeriods;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final TermManagementWindowService termManagementWindows;

    public AcademicSessionService(AcademicSessionRepository sessions, AcademicTermRepository terms,
                                  AcademicReportingPeriodRepository reportingPeriods, AuditService audit,
                                   JdbcTemplate jdbc, TermManagementWindowService termManagementWindows) {
        this.sessions = sessions;
        this.terms = terms;
        this.reportingPeriods = reportingPeriods;
        this.audit = audit;
        this.jdbc = jdbc;
        this.termManagementWindows = termManagementWindows;
    }

    @Transactional(readOnly = true)
    public List<SessionView> list() {
        UUID schoolId = TenantContext.get();
        return sessions.findBySchoolIdOrderByStartDateDesc(schoolId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public SessionView current() {
        return view(currentEntity());
    }

    @Transactional
    public SessionView create(SessionUpsert in) {
        UUID schoolId = TenantContext.get();
        validateDates(in.startDate(), in.endDate(), "session");
        if (sessions.existsBySchoolIdAndCodeIgnoreCase(schoolId, in.code().trim())) {
            throw ApiException.conflict("Une session utilise déjà ce code");
        }
        AcademicSession s = new AcademicSession();
        s.setSchoolId(schoolId);
        apply(s, in);
        if (in.current() != null && in.current()) sessions.clearCurrent(schoolId, new UUID(0, 0));
        try { s = sessions.saveAndFlush(s); }
        catch (DataIntegrityViolationException ex) { throw ApiException.conflict("Une session courante existe déjà"); }
        audit.record("SESSION_CREATED", "AcademicSession", s.getId().toString(), null, view(s), null);
        return view(s);
    }

    @Transactional
    public SessionView update(UUID id, SessionUpsert in) {
        AcademicSession s = find(id);
        if ("ARCHIVED".equals(s.getStatus())) throw ApiException.conflict("Une session archivée est en lecture seule");
        if (in.version() != null && in.version() != s.getVersion()) throw ApiException.conflict("La session a été modifiée par un autre utilisateur");
        SessionView before = view(s);
        validateDates(in.startDate(), in.endDate(), "session");
        apply(s, in);
        if (s.isCurrent()) sessions.clearCurrent(s.getSchoolId(), s.getId());
        s = sessions.saveAndFlush(s);
        validateTermsInside(s);
        audit.record("SESSION_UPDATED", "AcademicSession", id.toString(), before, view(s), null);
        return view(s);
    }

    @Transactional
    public SessionView changeState(UUID id, SessionStateRequest in) {
        AcademicSession s = find(id);
        String state = normalizeState(in.status());
        if (in.version() != null && in.version() != s.getVersion()) throw ApiException.conflict("Version de session obsolète");
        if ("CLOSED".equals(state) && !s.getEndDate().isBefore(LocalDate.now())) {
            throw ApiException.conflict("La session ne peut être clôturée avant sa date de fin");
        }
        if ("ARCHIVED".equals(state) && !"CLOSED".equals(s.getStatus())) {
            throw ApiException.conflict("Clôturez la session avant de l’archiver");
        }
        String before = s.getStatus();
        s.setStatus(state);
        if ("OPEN".equals(state)) {
            sessions.clearCurrent(s.getSchoolId(), s.getId());
            s.setCurrent(true);
        } else if ("ARCHIVED".equals(state)) {
            s.setCurrent(false);
        }
        s = sessions.saveAndFlush(s);
        audit.record("SESSION_STATE_CHANGED", "AcademicSession", id.toString(), before, state, in.reason());
        return view(s);
    }

    @Transactional
    public TermView addTerm(UUID sessionId, TermUpsert in) {
        AcademicSession s = find(sessionId);
        assertMutable(s);
        validateTerm(s, in.startDate(), in.endDate(), null);
        AcademicTerm t = new AcademicTerm();
        t.setSchoolId(s.getSchoolId());
        t.setAcademicSessionId(s.getId());
        apply(t, in);
        t = terms.saveAndFlush(t);
        audit.record("TERM_CREATED", "AcademicTerm", t.getId().toString(), null, termView(t), null);
        return termView(t);
    }

    @Transactional
    public TermView updateTerm(UUID id, TermUpsert in) {
        AcademicTerm t = terms.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période"));
        AcademicSession s = find(t.getAcademicSessionId());
        assertMutable(s);
        if (in.version() != null && in.version() != t.getVersion()) throw ApiException.conflict("Version de période obsolète");
        validateTerm(s, in.startDate(), in.endDate(), id);
        TermView before = termView(t);
        apply(t, in);
        t = terms.saveAndFlush(t);
        audit.record("TERM_UPDATED", "AcademicTerm", id.toString(), before, termView(t), null);
        return termView(t);
    }

    @Transactional
    public void deleteTerm(UUID id, String reason) {
        AcademicTerm t = terms.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période"));
        assertMutable(find(t.getAcademicSessionId()));
        TermView before = termView(t);
        terms.delete(t);
        audit.record("TERM_DELETED", "AcademicTerm", id.toString(), before, null, reason);
    }

    @Transactional(readOnly = true)
    public List<ReportingPeriodView> reportingPeriods(UUID sessionId) {
        AcademicSession session = find(sessionId);
        return reportingPeriods.findBySchoolIdAndAcademicSessionIdOrderByDisplayOrder(
                session.getSchoolId(), session.getId()).stream().map(this::reportingPeriodView).toList();
    }

    @Transactional
    public ReportingPeriodView upsertReportingPeriod(UUID sessionId, UUID id, ReportingPeriodUpsert in) {
        AcademicSession session = find(sessionId);
        assertMutable(session);
        String type = normalizePeriodType(in.periodType());
        validateReportingPeriodDates(session, type, in.academicTermId(), in.startDate(), in.endDate());
        AcademicReportingPeriod period = id == null
                ? new AcademicReportingPeriod()
                : reportingPeriods.findByIdAndSchoolId(id, TenantContext.get())
                    .orElseThrow(() -> ApiException.notFound("Période de résultat"));
        if (id != null && !session.getId().equals(period.getAcademicSessionId())) {
            throw ApiException.badRequest("La période de résultat n'appartient pas à cette session");
        }
        if (in.version() != null && in.version() != period.getVersion()) {
            throw ApiException.conflict("La période de résultat a été modifiée par un autre utilisateur");
        }
        AcademicReportingPeriod before = period.getId() == null ? null : period;
        period.setSchoolId(session.getSchoolId());
        period.setAcademicSessionId(session.getId());
        period.setAcademicTermId(type.equals("ANNUAL_RESULT") ? null : in.academicTermId());
        period.setCode(in.code().trim().toUpperCase(Locale.ROOT));
        period.setLabel(in.label().trim());
        period.setPeriodType(type);
        period.setDisplayOrder(in.displayOrder());
        period.setStartDate(in.startDate());
        period.setEndDate(in.endDate());
        // V85: action-specific dates are retained for history/rollback but are
        // no longer written by the normal reporting-period editor.
        period.setTimezone(in.timezone() == null || in.timezone().isBlank() ? "Africa/Douala" : in.timezone().trim());
        period.setCalculationPolicy(in.calculationPolicy() == null || in.calculationPolicy().isBlank()
                ? "DEFAULT" : in.calculationPolicy().trim().toUpperCase(Locale.ROOT));
        period.setStatus(in.status() == null || in.status().isBlank() ? "DRAFT" : normalizePeriodStatus(in.status()));
        ensureUniquePeriod(session, period);
        period = reportingPeriods.saveAndFlush(period);
        refreshStructureFingerprint(session.getId());
        audit.record(id == null ? "REPORTING_PERIOD_CREATED" : "REPORTING_PERIOD_UPDATED",
                "AcademicReportingPeriod", period.getId().toString(), before == null ? null : reportingPeriodView(before),
                reportingPeriodView(period), null);
        return reportingPeriodView(period);
    }

    @Transactional
    public void deleteReportingPeriod(UUID id, String reason) {
        AcademicReportingPeriod period = reportingPeriods.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période de résultat"));
        assertMutable(find(period.getAcademicSessionId()));
        if ("PUBLISHED".equals(period.getStatus())) throw ApiException.conflict("Une période publiée ne peut pas être supprimée");
        reportingPeriods.delete(period);
        audit.record("REPORTING_PERIOD_DELETED", "AcademicReportingPeriod", id.toString(), reportingPeriodView(period), null, reason);
    }

    @Transactional(readOnly = true)
    public StandardStructureView previewStandardStructure(UUID sessionId) {
        AcademicSession session = find(sessionId);
        return standardStructure(session, false);
    }

    @Transactional
    public StandardStructureView applyStandardStructure(UUID sessionId, String reason) {
        return applyStandardStructure(sessionId, reason, null);
    }

    @Transactional
    public StandardStructureView applyStandardStructure(UUID sessionId, String reason, String expectedFingerprint) {
        return applyStandardStructure(sessionId, reason, expectedFingerprint, null, null);
    }

    @Transactional
    public StandardStructureView applyStandardStructure(UUID sessionId, String reason, String expectedFingerprint,
                                                         List<ReportingPeriodView> proposedPeriods,
                                                         List<StructureDependencyView> proposedDependencies) {
        return applyStandardStructure(sessionId, reason, expectedFingerprint, proposedPeriods,
                proposedDependencies, null);
    }

    @Transactional
    public StandardStructureView applyStandardStructure(UUID sessionId, String reason, String expectedFingerprint,
                                                         List<ReportingPeriodView> proposedPeriods,
                                                         List<StructureDependencyView> proposedDependencies,
                                                         List<TermManagementWindowProposal> proposedTermWindows) {
        AcademicSession session = find(sessionId);
        assertMutable(session);
        if (reason == null || reason.isBlank()) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "REASON_REQUIRED",
                    "Le motif est obligatoire.", "reason", "Provide a reason before applying the academic structure.");
        }
        StandardStructureView currentStructure = standardStructure(session, false);
        if (expectedFingerprint != null && !expectedFingerprint.isBlank()
                && !expectedFingerprint.equals(currentStructure.fingerprint())) {
            throw ApiException.staleVersion("La structure académique a changé depuis l'aperçu. Rechargez avant de l'appliquer.",
                    0, 0);
        }
        // Do not create the standard terms until the stale-preview guard has
        // passed. Re-read the proposal afterwards so new reporting periods use
        // the actual persisted term IDs rather than preview-only seed IDs.
        ensureStandardTerms(session);
        StandardStructureView preview = standardStructure(session, false);
        List<ReportingPeriodView> requestedPeriods = normalizeProposedPeriods(
                proposedPeriods == null || proposedPeriods.isEmpty() ? preview.periods() : proposedPeriods,
                preview.periods());
        validateProposedStructure(session, requestedPeriods, proposedDependencies);
        List<TermManagementWindowProposal> requestedTermWindows = proposedTermWindows == null || proposedTermWindows.isEmpty()
                ? preview.termManagementWindows() : proposedTermWindows;
        validateProposedTermWindows(session, requestedTermWindows);
        for (ReportingPeriodView view : requestedPeriods) {
            ReportingPeriodUpsert input = new ReportingPeriodUpsert(view.code(), view.label(), view.periodType(),
                    view.academicTermId(), view.displayOrder(), view.startDate(), view.endDate(),
                    view.gradeEntryOpensAt(), view.gradeEntryClosesAt(), view.reviewOpensAt(), view.reviewClosesAt(),
                    view.validationOpensAt(), view.validationClosesAt(), view.bulletinPublishOpensAt(),
                    view.bulletinPublishClosesAt(), view.correctionOpensAt(), view.correctionClosesAt(),
                    view.teacherSubmissionOpensAt(), view.teacherSubmissionClosesAt(), view.timezone(),
                    view.calculationPolicy(), view.status(), null);
            UUID id = reportingPeriods.findBySchoolIdAndAcademicSessionIdAndCode(
                    session.getSchoolId(), session.getId(), view.code()).map(AcademicReportingPeriod::getId).orElse(null);
            upsertReportingPeriod(sessionId, id, input);
        }
        List<ReportingPeriodView> actual = reportingPeriods(sessionId);
        List<StructureDependencyView> requestedDependencies = proposedDependencies == null || proposedDependencies.isEmpty()
                ? standardDependencies(actual) : normalizeDependencies(actual, proposedDependencies);
        replaceStandardDependencies(sessionId, requestedDependencies);
        applyTermManagementWindows(session, requestedTermWindows);
        audit.record("REPORTING_STRUCTURE_APPLIED", "AcademicSession", sessionId.toString(), null,
                Map.of("periods", actual.stream().map(ReportingPeriodView::code).toList(),
                        "termManagementWindows", requestedTermWindows), reason);
        return new StandardStructureView(sessionId, actual, preview.warnings(), true,
                structureFingerprint(actual, requestedTermWindows), requestedDependencies, requestedTermWindows);
    }

    @Transactional(readOnly = true)
    public List<StructureDependencyView> dependencies(UUID sessionId) {
        find(sessionId);
        return jdbc.query("""
            SELECT d.parent_period_id, p.code parent_code, d.child_period_id, c.code child_code,
                   d.weight, d.optional, d.display_order
              FROM academic_reporting_period_dependency d
              JOIN academic_reporting_period p ON p.id=d.parent_period_id
              JOIN academic_reporting_period c ON c.id=d.child_period_id
             WHERE d.school_id=? AND d.academic_session_id=?
             ORDER BY p.display_order, d.display_order
            """, (rs, rowNum) -> new StructureDependencyView(
                rs.getObject("parent_period_id", UUID.class), rs.getString("parent_code"),
                rs.getObject("child_period_id", UUID.class), rs.getString("child_code"),
                rs.getBigDecimal("weight"), rs.getBoolean("optional"), rs.getInt("display_order")),
                TenantContext.get(), sessionId);
    }

    @Transactional(readOnly = true)
    public SessionReadinessView readiness(UUID sessionId) {
        AcademicSession session = find(sessionId);
        List<ReportingPeriodView> periods = reportingPeriods(sessionId);
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        List<ReadinessSectionView> sections = new ArrayList<>();
        List<ReadinessIssueView> structureIssues = new ArrayList<>();
        if (periods.isEmpty()) {
            blockers.add("REPORTING_STRUCTURE_MISSING");
            actions.add("Appliquez la structure standard S1–S6, T1–T3 et Annuel.");
            structureIssues.add(new ReadinessIssueView("REPORTING_STRUCTURE_MISSING", "BLOCKER",
                    "Reporting structure is missing", "Apply the standard reporting structure before opening grade entry.",
                    "standard-structure", 1));
        }
        for (String code : List.of("S1", "S2", "S3", "S4", "S5", "S6", "T1_RESULT", "T2_RESULT", "T3_RESULT", "ANNUAL")) {
            if (periods.stream().noneMatch(p -> code.equals(p.code()))) {
                blockers.add("PERIOD_MISSING:" + code);
                structureIssues.add(new ReadinessIssueView("PERIOD_MISSING", "BLOCKER", "Missing period " + code,
                        "Add this reporting milestone to the session structure.", "standard-structure", 1));
            }
        }
        sections.add(new ReadinessSectionView("STRUCTURE", "Reporting structure", structureIssues.isEmpty() ? "READY" : "BLOCKED",
                structureIssues.isEmpty(), structureIssues));
        List<ReadinessIssueView> accessIssues = new ArrayList<>();
        boolean scheduled = false;
        List<AcademicTerm> accessTerms = terms.findBySchoolIdAndAcademicSessionIdOrderBySequenceNo(
                session.getSchoolId(), sessionId);
        for (AcademicTerm term : accessTerms) {
            if (term.isManagementWindowLimited()
                    && term.getManagementOpensAt() == null && term.getManagementClosesAt() == null) {
                blockers.add("TERM_ACCESS_INVALID:" + term.getCode());
                accessIssues.add(new ReadinessIssueView("TERM_ACCESS_INVALID", "BLOCKER",
                        "Trimester access limit is invalid", "Add an opening or closing date for " + term.getCode() + ".",
                        "term-management-windows", 1));
            }
            if (term.isManagementWindowLimited()
                    && term.getManagementOpensAt() != null && term.getManagementClosesAt() != null
                    && !term.getManagementClosesAt().isAfter(term.getManagementOpensAt())) {
                blockers.add("TERM_ACCESS_INVALID:" + term.getCode());
                accessIssues.add(new ReadinessIssueView("TERM_ACCESS_INVALID", "BLOCKER",
                        "Trimester access limit is invalid", "The closing date must be after the opening date for " + term.getCode() + ".",
                        "term-management-windows", 1));
            }
            if (term.isManagementWindowLimited() && term.getManagementOpensAt() != null
                    && termManagementWindows.now().isBefore(term.getManagementOpensAt())) scheduled = true;
        }
        for (ReportingPeriodView p : periods) {
            try {
                termManagementWindows.resolveForPeriod(p.id());
            } catch (ApiException ex) {
                if ("TERM_MAPPING_MISSING".equals(ex.getCode())) {
                    blockers.add("TERM_MAPPING_MISSING:" + p.code());
                    accessIssues.add(new ReadinessIssueView("TERM_MAPPING_MISSING", "BLOCKER",
                            "Reporting milestone is not linked to a trimester", ex.getMessage(),
                            "academic-configuration-wizard", 1));
                } else {
                    throw ex;
                }
            }
        }
        String accessStatus = accessIssues.isEmpty() ? (scheduled ? "SCHEDULED" : "READY") : "BLOCKED";
        sections.add(new ReadinessSectionView("TERM_ACCESS", "Trimester access", accessStatus,
                accessIssues.isEmpty(), accessIssues));

        List<ReadinessIssueView> curriculumIssues = new ArrayList<>();
        List<ReadinessClassRow> activeClassesWithoutCurriculum = jdbc.query("""
                SELECT c.id, c.name
                  FROM school_class c
                 WHERE c.school_id=?
                   AND EXISTS (
                       SELECT 1
                         FROM student_enrollment se
                        WHERE se.school_id=c.school_id
                          AND se.academic_session_id=?
                          AND se.school_class_id=c.id
                          AND se.status='ACTIVE'
                   )
                   AND NOT EXISTS (
                       SELECT 1
                         FROM academic_curriculum_subject cur
                        WHERE cur.school_id=c.school_id
                          AND cur.academic_session_id=?
                          AND cur.class_id=c.id
                   )
                 ORDER BY c.name
                """, (rs, rowNum) -> new ReadinessClassRow(
                rs.getObject("id", UUID.class), rs.getString("name")),
                TenantContext.get(), sessionId, sessionId);
        for (ReadinessClassRow row : activeClassesWithoutCurriculum) {
            if (!blockers.contains("CURRICULUM_MISSING")) blockers.add("CURRICULUM_MISSING");
            curriculumIssues.add(new ReadinessIssueView("CURRICULUM_MISSING", "BLOCKER", "Class-subject curriculum missing",
                    "Configure subjects for the actively enrolled class before entering grades or generating reports.",
                    "class-subjects", 1, row.className(), row.classId(), null, null,
                    "Aucune matière n'est configurée pour la classe " + row.className()
                            + " qui compte des élèves actifs. Configurez ses matières dans Paramètres → Scolarité → Matières par classe avant la saisie des notes ou les bulletins.",
                    "No class-subject curriculum is configured for " + row.className()
                            + ", which has active enrollments. Configure its subjects in Settings → Academics → Class subjects before grade entry or report cards."));
        }
        List<ReadinessCurriculumRow> missingAssignments = jdbc.query("""
                SELECT cur.class_id, c.name AS class_name, c.level, cur.subject_id,
                       s.code AS subject_code,
                       COALESCE(s.label->>'fr', s.label->>'en', s.code) AS subject_label,
                       EXISTS (
                           SELECT 1
                             FROM student_enrollment se
                            WHERE se.school_id=cur.school_id
                              AND se.academic_session_id=cur.academic_session_id
                              AND se.school_class_id=cur.class_id
                              AND se.status='ACTIVE'
                       ) AS has_active_enrollment
                  FROM academic_curriculum_subject cur
                  JOIN school_class c ON c.id=cur.class_id AND c.school_id=cur.school_id
                  JOIN subject s ON s.id=cur.subject_id AND s.school_id=cur.school_id
                 WHERE cur.school_id=?
                   AND cur.academic_session_id=?
                   AND (
                       (lower(c.level)='secondary' AND NOT EXISTS (
                           SELECT 1
                             FROM academic_class_subject_teacher ast
                            WHERE ast.school_id=cur.school_id
                              AND ast.academic_session_id=cur.academic_session_id
                              AND ast.class_id=cur.class_id
                              AND ast.subject_id=cur.subject_id
                              AND ast.role='RESPONSIBLE'
                              AND ast.active=true
                              AND EXISTS (
                                  SELECT 1 FROM employee e
                                   WHERE e.id=ast.employee_id
                                     AND e.school_id=cur.school_id
                                     AND e.active=true
                              )
                       ))
                       OR (lower(c.level)<>'secondary' AND NOT EXISTS (
                           SELECT 1
                             FROM class_teacher_assignment a
                            WHERE a.school_id=cur.school_id
                              AND a.academic_session_id=cur.academic_session_id
                              AND a.class_id=cur.class_id
                              AND a.role='HOMEROOM'
                              AND a.status='ACTIVE'
                              AND EXISTS (
                                  SELECT 1 FROM employee e
                                   WHERE e.id=a.employee_id
                                     AND e.school_id=cur.school_id
                                     AND e.active=true
                              )
                       ))
                   )
                 ORDER BY c.name, cur.display_order, s.code
                """, (rs, rowNum) -> new ReadinessCurriculumRow(
                rs.getObject("class_id", UUID.class), rs.getString("class_name"), rs.getString("level"),
                rs.getObject("subject_id", UUID.class), rs.getString("subject_code"),
                rs.getString("subject_label"), rs.getBoolean("has_active_enrollment")),
                TenantContext.get(), sessionId);
        for (ReadinessCurriculumRow row : missingAssignments) {
            if (!warnings.contains("CURRICULUM_ASSIGNMENT_MISSING")) warnings.add("CURRICULUM_ASSIGNMENT_MISSING");
            boolean secondary = "secondary".equalsIgnoreCase(row.level());
            String roleFr = secondary ? "enseignant responsable" : "titulaire";
            String roleEn = secondary ? "responsible teacher" : "homeroom teacher";
            String messageFr = row.hasActiveEnrollment()
                    ? "Aucun " + roleFr + " actif n'est affecté à " + row.subjectLabel() + " pour la classe " + row.className()
                        + ". Configurez cette affectation dans Paramètres → Scolarité → Matières par classe avant l'envoi des notes."
                    : "Aucun " + roleFr + " actif n'est affecté à " + row.subjectLabel() + " pour la classe " + row.className()
                        + ". Cette classe n'a actuellement aucun élève actif : cela n'empêche pas la préparation de la session, mais l'affectation devra être configurée avant son utilisation.";
            String messageEn = row.hasActiveEnrollment()
                    ? "No active " + roleEn + " is assigned to " + row.subjectLabel() + " for " + row.className()
                        + ". Configure this assignment in Settings → Academics → Class subjects before sending grades."
                    : "No active " + roleEn + " is assigned to " + row.subjectLabel() + " for " + row.className()
                        + ". This class has no active enrollments, so it does not block session readiness; configure the assignment before the class is used.";
            curriculumIssues.add(new ReadinessIssueView("CURRICULUM_ASSIGNMENT_MISSING", "WARNING",
                    "Teacher assignment needs repair", "The class subject has no active authoritative teacher assignment.",
                    "class-subjects", 1, row.className() + " · " + row.subjectCode(), row.classId(),
                    row.subjectId(), row.subjectCode(), messageFr, messageEn));
        }
        boolean curriculumBlocked = curriculumIssues.stream().anyMatch(issue -> "BLOCKER".equals(issue.severity()));
        sections.add(new ReadinessSectionView("CURRICULUM", "Curriculum and assignments",
                curriculumBlocked ? "BLOCKED" : curriculumIssues.isEmpty() ? "READY" : "WARNING",
                !curriculumBlocked, curriculumIssues));
        if ("DRAFT".equals(session.getStatus())) {
            actions.add("Ouvrez la session après validation de la structure et des droits d'accès.");
        } else if ("OPEN".equals(session.getStatus()) && blockers.isEmpty()) {
            if (!warnings.isEmpty()) {
                actions.add("La session est prête ; vérifiez les avertissements de configuration des classes avant les soumissions.");
            }
            actions.add("Les opérations restent soumises à vos droits, à l'état de la session et aux prérequis du dossier.");
        }
        String phase = blockers.isEmpty() ? ("OPEN".equals(session.getStatus()) ? "READY" : "CONFIGURED") : "BLOCKED";
        String next = blockers.isEmpty() ? actions.stream().findFirst().orElse("Aucune action requise")
                : actions.stream().findFirst().orElse("Corrigez les blocages affichés");
        return new SessionReadinessView(sessionId, session.getStatus(), phase, blockers.isEmpty(), next, blockers, warnings, actions, sections);
    }

    private void validateProposedStructure(AcademicSession session, List<ReportingPeriodView> periods,
                                           List<StructureDependencyView> dependencies) {
        if (periods == null || periods.isEmpty()) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "STRUCTURE_PERIODS_REQUIRED",
                    "La structure doit contenir au moins une période.", "periods", "Provide at least one reporting period.");
        }
        Set<String> codes = new HashSet<>();
        for (ReportingPeriodView period : periods) {
            if (period == null || period.code() == null || period.code().isBlank()) {
                throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "PERIOD_CODE_REQUIRED",
                        "Le code de période est obligatoire.", "periods", "Every reporting period needs a code.");
            }
            String code = period.code().trim().toUpperCase(Locale.ROOT);
            if (!codes.add(code)) {
                throw ApiException.field(org.springframework.http.HttpStatus.CONFLICT, "PERIOD_CODE_DUPLICATE",
                        "Le code de période est dupliqué : " + code + ".", "periods", "Duplicate reporting period code: " + code + ".");
            }
            if (period.startDate() == null || period.endDate() == null || period.startDate().isAfter(period.endDate())
                    || period.startDate().isBefore(session.getStartDate()) || period.endDate().isAfter(session.getEndDate())) {
                throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "PERIOD_DATE_OUTSIDE_SESSION",
                        "Les dates de chaque période doivent rester dans la session.", "periods", "Reporting period dates must remain inside the academic session.");
            }
            if (!Set.of("SEQUENCE", "TERM_RESULT", "ANNUAL_RESULT").contains(period.periodType())) {
                throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "PERIOD_TYPE_INVALID",
                        "Le type de période est invalide.", "periodType", "Use SEQUENCE, TERM_RESULT, or ANNUAL_RESULT.");
            }
            if (!AcademicPeriodRules.SEQUENCE.equalsIgnoreCase(period.periodType())
                    && (period.gradeEntryOpensAt() != null || period.gradeEntryClosesAt() != null
                    || period.teacherSubmissionOpensAt() != null || period.teacherSubmissionClosesAt() != null)) {
                throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "RAW_WINDOW_NOT_APPLICABLE",
                        "Les fenÃªtres de saisie et de soumission ne s'appliquent qu'aux sÃ©quences.",
                        "gradeEntryOpensAt", "Les rÃ©sultats calculÃ©s n'acceptent pas de notes brutes.");
            }
            if (period.academicTermId() != null) {
                Integer termCount = jdbc.queryForObject("SELECT count(*) FROM academic_term WHERE id=? AND school_id=? AND academic_session_id=?",
                        Integer.class, period.academicTermId(), session.getSchoolId(), session.getId());
                if (termCount == null || termCount == 0) {
                    throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "PERIOD_TERM_INVALID",
                            "La période référence un trimestre qui n'appartient pas à cette session.", "academicTermId", "The period term must belong to this session.");
                }
            }
            validateWindow(period.gradeEntryOpensAt(), period.gradeEntryClosesAt(), "saisie des notes");
            validateWindow(period.teacherSubmissionOpensAt(), period.teacherSubmissionClosesAt(), "soumission des enseignants");
            validateWindow(period.reviewOpensAt(), period.reviewClosesAt(), "révision");
            validateWindow(period.validationOpensAt(), period.validationClosesAt(), "validation");
            validateWindow(period.bulletinPublishOpensAt(), period.bulletinPublishClosesAt(), "publication");
            validateWindow(period.correctionOpensAt(), period.correctionClosesAt(), "correction");
        }
        if (dependencies == null || dependencies.isEmpty()) return;
        Set<String> edges = new HashSet<>();
        validateDependencyGraph(periods, dependencies);
        for (StructureDependencyView dependency : dependencies) {
            String parent = dependency.parentCode() == null ? "" : dependency.parentCode().trim().toUpperCase(Locale.ROOT);
            String child = dependency.childCode() == null ? "" : dependency.childCode().trim().toUpperCase(Locale.ROOT);
            if (!codes.contains(parent) || !codes.contains(child)) {
                throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "DEPENDENCY_PERIOD_UNKNOWN",
                        "La dépendance référence une période absente de la proposition.", "dependencies", "Every dependency must reference proposed period codes.");
            }
            if (parent.equals(child)) {
                throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "DEPENDENCY_SELF_REFERENCE",
                        "Une période ne peut pas dépendre d'elle-même.", "dependencies", "A period cannot depend on itself.");
            }
            if (dependency.weight() == null || dependency.weight().signum() <= 0) {
                throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "DEPENDENCY_WEIGHT_INVALID",
                        "Le poids de dépendance doit être positif.", "weight", "Dependency weight must be positive.");
            }
            if (!edges.add(parent + "->" + child)) {
                throw ApiException.field(org.springframework.http.HttpStatus.CONFLICT, "DEPENDENCY_DUPLICATE",
                        "La dépendance est dupliquée.", "dependencies", "Duplicate reporting dependency.");
            }
        }
    }

    private static void validateDependencyGraph(List<ReportingPeriodView> periods,
                                                List<StructureDependencyView> dependencies) {
        Map<String, String> types = new LinkedHashMap<>();
        periods.forEach(p -> types.put(p.code().trim().toUpperCase(Locale.ROOT), p.periodType()));
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (StructureDependencyView dependency : dependencies) {
            String parent = dependency.parentCode() == null ? "" : dependency.parentCode().trim().toUpperCase(Locale.ROOT);
            String child = dependency.childCode() == null ? "" : dependency.childCode().trim().toUpperCase(Locale.ROOT);
            String parentType = types.get(parent), childType = types.get(child);
            if (AcademicPeriodRules.SEQUENCE.equalsIgnoreCase(parentType)
                    || (AcademicPeriodRules.TERM_RESULT.equalsIgnoreCase(parentType)
                    && !AcademicPeriodRules.SEQUENCE.equalsIgnoreCase(childType))
                    || (AcademicPeriodRules.ANNUAL_RESULT.equalsIgnoreCase(parentType)
                    && !AcademicPeriodRules.TERM_RESULT.equalsIgnoreCase(childType))) {
                throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "DEPENDENCY_TYPE_INVALID",
                        "Les rÃ©sultats calculÃ©s doivent dÃ©pendre du niveau de rÃ©sultat prÃ©cÃ©dent.", "dependencies",
                        "Use sequence children for trimester results and trimester children for the annual result.");
            }
            graph.computeIfAbsent(parent, ignored -> new HashSet<>()).add(child);
        }
        Set<String> visiting = new HashSet<>(), visited = new HashSet<>();
        for (String node : graph.keySet()) if (hasCycle(node, graph, visiting, visited)) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "DEPENDENCY_CYCLE",
                    "Les dÃ©pendances de rÃ©sultats ne peuvent pas former de cycle.", "dependencies",
                    "Reporting period dependencies must be acyclic.");
        }
    }

    private static boolean hasCycle(String node, Map<String, Set<String>> graph,
                                    Set<String> visiting, Set<String> visited) {
        if (visiting.contains(node)) return true;
        if (visited.contains(node)) return false;
        visiting.add(node);
        for (String child : graph.getOrDefault(node, Set.of())) {
            if (hasCycle(child, graph, visiting, visited)) return true;
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }

    private List<StructureDependencyView> normalizeDependencies(List<ReportingPeriodView> actual,
                                                                List<StructureDependencyView> proposed) {
        Map<String, ReportingPeriodView> byCode = new LinkedHashMap<>();
        actual.forEach(period -> byCode.put(period.code().toUpperCase(Locale.ROOT), period));
        return proposed.stream().map(dependency -> {
            ReportingPeriodView parent = byCode.get(dependency.parentCode().toUpperCase(Locale.ROOT));
            ReportingPeriodView child = byCode.get(dependency.childCode().toUpperCase(Locale.ROOT));
            if (parent == null || child == null) {
                throw ApiException.badRequest("La dépendance ne correspond pas à la structure enregistrée.");
            }
            return new StructureDependencyView(parent.id(), parent.code(), child.id(), child.code(),
                    dependency.weight(), dependency.optional(), dependency.displayOrder());
        }).toList();
    }

    private void ensureStandardTerms(AcademicSession session) {
        List<AcademicTerm> existing = terms.findBySchoolIdAndAcademicSessionIdOrderBySequenceNo(
                session.getSchoolId(), session.getId());
        long totalDays = ChronoUnit.DAYS.between(session.getStartDate(), session.getEndDate()) + 1;
        for (int i = 1; i <= 3; i++) {
            final int termSequence = i;
            if (existing.stream().anyMatch(t -> t.getSequenceNo() == termSequence)) continue;
            AcademicTerm term = new AcademicTerm();
            term.setSchoolId(session.getSchoolId());
            term.setAcademicSessionId(session.getId());
            term.setCode("T" + i);
            term.setLabel("Trimestre " + i);
            term.setSequenceNo(i);
            term.setStartDate(splitStart(session.getStartDate(), totalDays, i));
            term.setEndDate(splitEnd(session.getStartDate(), totalDays, i));
            terms.saveAndFlush(term);
        }
    }

    public AcademicSession currentEntity() {
        return sessions.findBySchoolIdAndCurrentTrue(TenantContext.get())
                .orElseThrow(() -> ApiException.badRequest("Aucune session académique courante n’est configurée"));
    }

    public AcademicSession find(UUID id) {
        return sessions.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Session académique"));
    }

    private void apply(AcademicSession s, SessionUpsert in) {
        s.setCode(in.code().trim().toUpperCase(Locale.ROOT));
        s.setLabel(in.label().trim());
        s.setStartDate(in.startDate());
        s.setEndDate(in.endDate());
        s.setStatus(in.status() == null || in.status().isBlank() ? s.getStatus() : normalizeState(in.status()));
        if (in.current() != null) s.setCurrent(in.current());
        s.setTimezone(in.timezone() == null || in.timezone().isBlank() ? "Africa/Douala" : in.timezone().trim());
    }

    private void apply(AcademicTerm t, TermUpsert in) {
        t.setCode(in.code().trim().toUpperCase(Locale.ROOT));
        t.setLabel(in.label().trim());
        t.setSequenceNo(in.sequenceNo());
        t.setStartDate(in.startDate());
        t.setEndDate(in.endDate());
        t.setTimezone(in.timezone() == null || in.timezone().isBlank() ? "Africa/Douala" : in.timezone().trim());
    }

    private void validateTerm(AcademicSession s, LocalDate start, LocalDate end, UUID ignoreId) {
        validateDates(start, end, "période");
        if (start.isBefore(s.getStartDate()) || end.isAfter(s.getEndDate())) {
            throw ApiException.badRequest("Les dates de la période doivent rester dans la session");
        }
        boolean overlap = terms.findBySchoolIdAndAcademicSessionIdOrderBySequenceNo(s.getSchoolId(), s.getId()).stream()
                .filter(t -> ignoreId == null || !ignoreId.equals(t.getId()))
                .anyMatch(t -> !end.isBefore(t.getStartDate()) && !start.isAfter(t.getEndDate()));
        if (overlap) throw ApiException.conflict("Les périodes académiques ne peuvent pas se chevaucher");
    }

    private void validateTermsInside(AcademicSession s) {
        for (AcademicTerm t : terms.findBySchoolIdAndAcademicSessionIdOrderBySequenceNo(s.getSchoolId(), s.getId())) {
            if (t.getStartDate().isBefore(s.getStartDate()) || t.getEndDate().isAfter(s.getEndDate())) {
                throw ApiException.conflict("Une période existante sort des nouvelles dates de session");
            }
        }
    }

    private static void validateDates(LocalDate start, LocalDate end, String label) {
        if (start == null || end == null || start.isAfter(end)) throw ApiException.badRequest("Dates de " + label + " invalides");
    }

    private static void validateWindow(java.time.Instant start, java.time.Instant end, String label) {
        if (start != null && end != null && start.isAfter(end)) throw ApiException.badRequest("Fenêtre de " + label + " invalide");
    }

    private static String normalizeState(String value) {
        String state = value.trim().toUpperCase(Locale.ROOT);
        if (!STATES.contains(state)) throw ApiException.badRequest("Statut de session invalide");
        return state;
    }

    private static void assertMutable(AcademicSession s) {
        if (List.of("CLOSED", "ARCHIVED").contains(s.getStatus())) throw ApiException.conflict("Cette session est en lecture seule");
    }

    private SessionView view(AcademicSession s) {
        List<TermView> rows = terms.findBySchoolIdAndAcademicSessionIdOrderBySequenceNo(s.getSchoolId(), s.getId())
                .stream().map(this::termView).toList();
        return new SessionView(s.getId(), s.getCode(), s.getLabel(), s.getStartDate(), s.getEndDate(),
                s.getStatus(), s.isCurrent(), s.getGradeEntryOpensAt(), s.getGradeEntryClosesAt(),
                s.getBulletinPublishOpensAt(), s.getBulletinPublishClosesAt(), s.getTeacherSubmissionOpensAt(),
                s.getTeacherSubmissionClosesAt(), s.getTimezone(), s.getVersion(), rows);
    }

    private TermView termView(AcademicTerm t) {
        return new TermView(t.getId(), t.getCode(), t.getLabel(), t.getSequenceNo(), t.getStartDate(),
                t.getEndDate(), t.getGradeEntryOpensAt(), t.getGradeEntryClosesAt(),
                t.getBulletinPublishOpensAt(), t.getBulletinPublishClosesAt(), t.getTeacherSubmissionOpensAt(),
                t.getTeacherSubmissionClosesAt(), t.getTimezone(), t.getVersion());
    }

    private StandardStructureView standardStructure(AcademicSession session, boolean applied) {
        List<AcademicTerm> existing = terms.findBySchoolIdAndAcademicSessionIdOrderBySequenceNo(
                session.getSchoolId(), session.getId());
        List<TermSeed> termSeeds = new ArrayList<>();
        long totalDays = ChronoUnit.DAYS.between(session.getStartDate(), session.getEndDate()) + 1;
        if (totalDays < 9) throw ApiException.badRequest("La session est trop courte pour générer trois trimestres");
        for (int i = 1; i <= 3; i++) {
            final int termSequence = i;
            AcademicTerm found = existing.stream().filter(t -> t.getSequenceNo() == termSequence).findFirst().orElse(null);
            LocalDate start = found == null ? splitStart(session.getStartDate(), totalDays, i) : found.getStartDate();
            LocalDate end = found == null ? splitEnd(session.getStartDate(), totalDays, i) : found.getEndDate();
            UUID id = found == null ? UUID.randomUUID() : found.getId();
            termSeeds.add(new TermSeed(id, i, "T" + i, "Trimestre " + i, start, end));
        }
        List<ReportingPeriodView> result = new ArrayList<>();
        int order = 1;
        for (TermSeed term : termSeeds) {
            long termDays = ChronoUnit.DAYS.between(term.start(), term.end()) + 1;
            LocalDate secondStart = term.start().plusDays(termDays / 2);
            result.add(previewPeriod(session, term, "S" + ((term.sequence() - 1) * 2 + 1),
                    "Séquence " + ((term.sequence() - 1) * 2 + 1), "SEQUENCE", order++, term.start(), secondStart.minusDays(1)));
            result.add(previewPeriod(session, term, "S" + (term.sequence() * 2),
                    "Séquence " + (term.sequence() * 2), "SEQUENCE", order++, secondStart, term.end()));
            result.add(previewPeriod(session, term, "T" + term.sequence() + "_RESULT",
                    "Résultat Trimestre " + term.sequence(), "TERM_RESULT", order++, term.start(), term.end()));
        }
        result.add(new ReportingPeriodView(UUID.randomUUID(), session.getId(), null, "ANNUAL", "Résultat annuel",
                "ANNUAL_RESULT", order, session.getStartDate(), session.getEndDate(), null, null, null, null,
                null, null, null, null, null, null, "ANNUAL_T1_T2_T3_EQUAL", "DRAFT", 0));
        List<TermManagementWindowProposal> termWindows = termWindowProposals(existing, termSeeds);
        return new StandardStructureView(session.getId(), result,
                existing.size() > 3 ? List.of("Les périodes existantes au-delà des trois trimestres seront conservées.") : List.of(),
                applied, structureFingerprint(result, termWindows), standardDependencies(result), termWindows);
    }

    private List<StructureDependencyView> standardDependencies(List<ReportingPeriodView> periods) {
        java.util.Map<String, ReportingPeriodView> byCode = new LinkedHashMap<>();
        periods.forEach(p -> byCode.put(p.code(), p));
        List<StructureDependencyView> result = new ArrayList<>();
        addDependency(result, byCode, "T1_RESULT", "S1", "0.5", false, 1);
        addDependency(result, byCode, "T1_RESULT", "S2", "0.5", false, 2);
        addDependency(result, byCode, "T2_RESULT", "S3", "0.5", false, 1);
        addDependency(result, byCode, "T2_RESULT", "S4", "0.5", false, 2);
        addDependency(result, byCode, "T3_RESULT", "S5", "0.5", false, 1);
        addDependency(result, byCode, "T3_RESULT", "S6", "0.5", false, 2);
        addDependency(result, byCode, "ANNUAL", "T1_RESULT", "0.3333333333", false, 1);
        addDependency(result, byCode, "ANNUAL", "T2_RESULT", "0.3333333333", false, 2);
        addDependency(result, byCode, "ANNUAL", "T3_RESULT", "0.3333333333", false, 3);
        return result;
    }

    private List<ReportingPeriodView> normalizeProposedPeriods(List<ReportingPeriodView> proposed,
                                                               List<ReportingPeriodView> persistedPreview) {
        Map<String, ReportingPeriodView> standardByCode = persistedPreview.stream()
                .collect(java.util.stream.Collectors.toMap(p -> p.code().toUpperCase(Locale.ROOT), p -> p,
                        (left, right) -> left, LinkedHashMap::new));
        return proposed.stream().map(period -> {
            ReportingPeriodView canonical = period.code() == null ? null
                    : standardByCode.get(period.code().trim().toUpperCase(Locale.ROOT));
            UUID academicTermId = period.academicTermId();
            if (canonical != null && !"ANNUAL_RESULT".equalsIgnoreCase(period.periodType())
                    && canonical.academicTermId() != null) {
                // A preview made before the first standard-structure apply uses
                // seed UUIDs. Replace only those standard rows with the newly
                // persisted term ID; custom period mappings remain untouched.
                academicTermId = canonical.academicTermId();
            } else if ("ANNUAL_RESULT".equalsIgnoreCase(period.periodType())) {
                academicTermId = null;
            }
            return new ReportingPeriodView(period.id(), period.academicSessionId(), academicTermId,
                    period.code(), period.label(), period.periodType(), period.displayOrder(),
                    period.startDate(), period.endDate(), period.gradeEntryOpensAt(), period.gradeEntryClosesAt(),
                    period.reviewOpensAt(), period.reviewClosesAt(), period.validationOpensAt(), period.validationClosesAt(),
                    period.bulletinPublishOpensAt(), period.bulletinPublishClosesAt(), period.correctionOpensAt(),
                    period.correctionClosesAt(), period.teacherSubmissionOpensAt(), period.teacherSubmissionClosesAt(),
                    period.timezone(), period.calculationPolicy(), period.status(), period.version());
        }).toList();
    }

    private void replaceStandardDependencies(UUID sessionId, List<StructureDependencyView> dependencyRows) {
        jdbc.update("DELETE FROM academic_reporting_period_dependency WHERE school_id=? AND academic_session_id=?",
                TenantContext.get(), sessionId);
        for (StructureDependencyView dependency : dependencyRows) {
            jdbc.update("""
                    INSERT INTO academic_reporting_period_dependency
                        (id,school_id,academic_session_id,parent_period_id,child_period_id,weight,optional,display_order)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), TenantContext.get(), sessionId, dependency.parentPeriodId(),
                    dependency.childPeriodId(), dependency.weight(), dependency.optional(), dependency.displayOrder());
        }
    }

    private static void addDependency(List<StructureDependencyView> out,
                                      java.util.Map<String, ReportingPeriodView> byCode,
                                      String parent, String child, String weight,
                                      boolean optional, int order) {
        ReportingPeriodView p = byCode.get(parent), c = byCode.get(child);
        if (p != null && c != null) {
            out.add(new StructureDependencyView(p.id(), p.code(), c.id(), c.code(),
                    new java.math.BigDecimal(weight), optional, order));
        }
    }

    private String structureFingerprint(List<ReportingPeriodView> periods) {
        return structureFingerprint(periods, List.of());
    }

    private String structureFingerprint(List<ReportingPeriodView> periods,
                                        List<TermManagementWindowProposal> termWindows) {
        String payload = periods.stream()
                .sorted(java.util.Comparator.comparingInt(ReportingPeriodView::displayOrder))
                .map(p -> String.join("|", p.code(), p.periodType(),
                        String.valueOf(p.displayOrder()), String.valueOf(p.startDate()), String.valueOf(p.endDate())))
                .reduce((a, b) -> a + "\n" + b).orElse("")
                + "\nTERM_WINDOWS\n"
                + termWindows.stream().sorted(java.util.Comparator.comparingInt(TermManagementWindowProposal::sequenceNo))
                .map(w -> String.join("|", String.valueOf(w.sequenceNo()), w.code(), String.valueOf(w.limited()),
                        String.valueOf(w.opensAt()), String.valueOf(w.closesAt()), String.valueOf(w.timezone())))
                .reduce((a, b) -> a + "\n" + b).orElse("");
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot fingerprint reporting structure", ex);
        }
    }

    private List<TermManagementWindowProposal> termWindowProposals(List<AcademicTerm> existing,
                                                                   List<TermSeed> seeds) {
        return seeds.stream().map(seed -> {
            AcademicTerm term = existing.stream().filter(t -> t.getSequenceNo() == seed.sequence()).findFirst().orElse(null);
            return new TermManagementWindowProposal(seed.sequence(), seed.code(),
                    term != null && term.isManagementWindowLimited(),
                    term == null ? null : term.getManagementOpensAt(),
                    term == null ? null : term.getManagementClosesAt(),
                    term == null ? "Africa/Douala" : term.getTimezone(),
                    term == null ? 0L : term.getVersion());
        }).toList();
    }

    private void validateProposedTermWindows(AcademicSession session,
                                             List<TermManagementWindowProposal> proposals) {
        if (proposals == null) return;
        Set<Integer> sequences = new HashSet<>();
        for (TermManagementWindowProposal proposal : proposals) {
            if (proposal == null || proposal.code() == null || proposal.code().isBlank()
                    || proposal.sequenceNo() < 1 || !sequences.add(proposal.sequenceNo())) {
                throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "TERM_WINDOW_MAPPING_INVALID", "Les limites d'accès doivent contenir un trimestre unique par numéro.",
                        "termManagementWindows", "Provide one valid proposal for each trimester.");
            }
            AcademicTerm term = terms.findBySchoolIdAndAcademicSessionIdOrderBySequenceNo(
                            session.getSchoolId(), session.getId()).stream()
                    .filter(t -> t.getSequenceNo() == proposal.sequenceNo()
                            && t.getCode().equalsIgnoreCase(proposal.code()))
                    .findFirst().orElse(null);
            if (term == null) {
                throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT, "TERM_MAPPING_MISSING",
                        "Le trimestre " + proposal.code() + " n'existe pas dans cette session. Corrigez l'assistant de configuration académique.");
            }
            TermManagementWindowUpsert input = new TermManagementWindowUpsert(proposal.limited(), proposal.opensAt(),
                    proposal.closesAt(), proposal.version() == null ? term.getVersion() : proposal.version());
            validateTermManagementWindowInput(input);
        }
    }

    private void applyTermManagementWindows(AcademicSession session,
                                            List<TermManagementWindowProposal> proposals) {
        if (proposals == null) return;
        List<AcademicTerm> current = terms.findBySchoolIdAndAcademicSessionIdOrderBySequenceNo(
                session.getSchoolId(), session.getId());
        for (TermManagementWindowProposal proposal : proposals) {
            AcademicTerm term = current.stream().filter(t -> t.getSequenceNo() == proposal.sequenceNo()
                    && t.getCode().equalsIgnoreCase(proposal.code())).findFirst()
                    .orElseThrow(() -> ApiException.coded(org.springframework.http.HttpStatus.CONFLICT,
                            "TERM_MAPPING_MISSING", "Le trimestre " + proposal.code() + " n'est pas disponible dans la session cible."));
            Long version = proposal.version() == null ? term.getVersion() : proposal.version();
            termManagementWindows.update(session.getId(), term.getId(),
                    new TermManagementWindowUpsert(proposal.limited(), proposal.opensAt(), proposal.closesAt(), version));
        }
    }

    private static void validateTermManagementWindowInput(TermManagementWindowUpsert input) {
        if (!input.limited() && (input.opensAt() != null || input.closesAt() != null)) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "TERM_WINDOW_DATES_NOT_ALLOWED",
                    "Désactivez la limite ou retirez les dates avant d'enregistrer.", "limited",
                    "Aucune date n'est permise lorsque la limite est désactivée.");
        }
        if (input.limited() && input.opensAt() == null && input.closesAt() == null) {
            throw ApiException.fields(org.springframework.http.HttpStatus.BAD_REQUEST, "TERM_WINDOW_ENDPOINT_REQUIRED",
                    "Indiquez une date d'ouverture, une date de fermeture, ou les deux.",
                    Map.of("opensAt", "Indiquez une date d'ouverture, une date de fermeture, ou les deux.",
                            "closesAt", "Indiquez une date d'ouverture, une date de fermeture, ou les deux."));
        }
        if (input.opensAt() != null && input.closesAt() != null && !input.closesAt().isAfter(input.opensAt())) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "TERM_WINDOW_RANGE_INVALID",
                    "La fermeture doit être postérieure à l'ouverture.", "closesAt",
                    "La fermeture doit être postérieure à l'ouverture.");
        }
    }

    private void refreshStructureFingerprint(UUID sessionId) {
        List<ReportingPeriodView> current = reportingPeriods(sessionId);
        List<AcademicTerm> currentTerms = terms.findBySchoolIdAndAcademicSessionIdOrderBySequenceNo(
                TenantContext.get(), sessionId);
        List<TermSeed> seeds = currentTerms.stream()
                .map(t -> new TermSeed(t.getId(), t.getSequenceNo(), t.getCode(), t.getLabel(), t.getStartDate(), t.getEndDate()))
                .toList();
        String fingerprint = structureFingerprint(current, termWindowProposals(currentTerms, seeds));
        reportingPeriods.findBySchoolIdAndAcademicSessionIdOrderByDisplayOrder(TenantContext.get(), sessionId)
                .forEach(p -> p.setStructureFingerprint(fingerprint));
        reportingPeriods.flush();
    }

    private ReportingPeriodView previewPeriod(AcademicSession session, TermSeed term, String code, String label,
                                               String type, int order, LocalDate start, LocalDate end) {
        AcademicReportingPeriod existing = reportingPeriods.findBySchoolIdAndAcademicSessionIdAndCode(
                session.getSchoolId(), session.getId(), code).orElse(null);
        return new ReportingPeriodView(existing == null ? UUID.randomUUID() : existing.getId(), session.getId(), term.id(),
                code, label, type, order, start, end,
                existing == null ? null : existing.getGradeEntryOpensAt(), existing == null ? null : existing.getGradeEntryClosesAt(),
                existing == null ? null : existing.getReviewOpensAt(), existing == null ? null : existing.getReviewClosesAt(),
                 existing == null ? null : existing.getValidationOpensAt(), existing == null ? null : existing.getValidationClosesAt(),
                 existing == null ? null : existing.getBulletinPublishOpensAt(), existing == null ? null : existing.getBulletinPublishClosesAt(),
                 existing == null ? null : existing.getCorrectionOpensAt(), existing == null ? null : existing.getCorrectionClosesAt(),
                 existing == null ? null : existing.getTeacherSubmissionOpensAt(), existing == null ? null : existing.getTeacherSubmissionClosesAt(),
                 existing == null ? "Africa/Douala" : existing.getTimezone(),
                 existing == null ? "DEFAULT" : existing.getCalculationPolicy(), existing == null ? "DRAFT" : existing.getStatus(),
                existing == null ? 0 : existing.getVersion());
    }

    private record TermSeed(UUID id, int sequence, String code, String label, LocalDate start, LocalDate end) {}

    private static LocalDate splitStart(LocalDate start, long totalDays, int sequence) {
        return start.plusDays((totalDays * (sequence - 1)) / 3);
    }

    private static LocalDate splitEnd(LocalDate start, long totalDays, int sequence) {
        return start.plusDays((totalDays * sequence) / 3 - 1);
    }

    private void validateReportingPeriodDates(AcademicSession session, String type, UUID termId,
                                              LocalDate start, LocalDate end) {
        validateDates(start, end, "période de résultat");
        if ("ANNUAL_RESULT".equals(type)) {
            if (start.isBefore(session.getStartDate()) || end.isAfter(session.getEndDate()))
                throw ApiException.badRequest("Le résultat annuel doit rester dans les dates de la session");
            return;
        }
        if (termId == null) throw ApiException.badRequest("Un résultat de séquence/trimestre doit appartenir à un trimestre");
        AcademicTerm term = terms.findByIdAndSchoolId(termId, session.getSchoolId())
                .orElseThrow(() -> ApiException.notFound("Trimestre"));
        if (!session.getId().equals(term.getAcademicSessionId())) throw ApiException.badRequest("Le trimestre n'appartient pas à cette session");
        if (start.isBefore(term.getStartDate()) || end.isAfter(term.getEndDate()))
            throw ApiException.badRequest("La période de résultat doit rester dans son trimestre");
    }

    private void ensureUniquePeriod(AcademicSession session, AcademicReportingPeriod period) {
        for (AcademicReportingPeriod other : reportingPeriods.findBySchoolIdAndAcademicSessionIdOrderByDisplayOrder(
                session.getSchoolId(), session.getId())) {
            if (other.getId().equals(period.getId())) continue;
            if (other.getCode().equalsIgnoreCase(period.getCode())) throw ApiException.conflict("Ce code de période existe déjà dans la session");
            if (other.getDisplayOrder() == period.getDisplayOrder()) throw ApiException.conflict("Cet ordre d'affichage est déjà utilisé");
            if ("SEQUENCE".equals(period.getPeriodType()) && "SEQUENCE".equals(other.getPeriodType())
                    && period.getAcademicTermId().equals(other.getAcademicTermId())
                    && !period.getEndDate().isBefore(other.getStartDate()) && !period.getStartDate().isAfter(other.getEndDate()))
                throw ApiException.conflict("Deux séquences du même trimestre se chevauchent");
        }
    }

    private static void validateWindows(AcademicReportingPeriod p) {
        if (!AcademicPeriodRules.SEQUENCE.equalsIgnoreCase(p.getPeriodType())
                && (p.getGradeEntryOpensAt() != null || p.getGradeEntryClosesAt() != null
                || p.getTeacherSubmissionOpensAt() != null || p.getTeacherSubmissionClosesAt() != null)) {
            throw ApiException.badRequest("Les fenÃªtres de saisie et de soumission ne s'appliquent qu'aux sÃ©quences");
        }
        validateWindow(p.getGradeEntryOpensAt(), p.getGradeEntryClosesAt(), "saisie des notes");
        validateWindow(p.getReviewOpensAt(), p.getReviewClosesAt(), "revue");
        validateWindow(p.getValidationOpensAt(), p.getValidationClosesAt(), "validation");
        validateWindow(p.getBulletinPublishOpensAt(), p.getBulletinPublishClosesAt(), "publication");
        validateWindow(p.getCorrectionOpensAt(), p.getCorrectionClosesAt(), "correction");
        validateWindow(p.getTeacherSubmissionOpensAt(), p.getTeacherSubmissionClosesAt(), "soumission des enseignants");
        java.time.Instant previousClose = null;
        String previousLabel = null;
        java.util.List<java.util.Map.Entry<String, java.time.Instant[]>> phases = java.util.List.of(
                java.util.Map.entry("saisie des notes", new java.time.Instant[]{p.getGradeEntryOpensAt(), p.getGradeEntryClosesAt()}),
                java.util.Map.entry("soumission des enseignants", new java.time.Instant[]{p.getTeacherSubmissionOpensAt(), p.getTeacherSubmissionClosesAt()}),
                java.util.Map.entry("revue", new java.time.Instant[]{p.getReviewOpensAt(), p.getReviewClosesAt()}),
                java.util.Map.entry("validation", new java.time.Instant[]{p.getValidationOpensAt(), p.getValidationClosesAt()}),
                java.util.Map.entry("publication", new java.time.Instant[]{p.getBulletinPublishOpensAt(), p.getBulletinPublishClosesAt()}),
                java.util.Map.entry("correction", new java.time.Instant[]{p.getCorrectionOpensAt(), p.getCorrectionClosesAt()}));
        for (var phase : phases) {
            if (phase.getValue()[0] == null || phase.getValue()[1] == null) continue;
            if (previousClose != null && !phase.getValue()[0].isAfter(previousClose)) {
                throw ApiException.badRequest("La fenêtre " + phase.getKey() + " doit commencer après la fermeture de " + previousLabel);
            }
            previousClose = phase.getValue()[1];
            previousLabel = phase.getKey();
        }
    }

    private static String normalizePeriodType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("SEQUENCE", "TERM_RESULT", "ANNUAL_RESULT").contains(type)) throw ApiException.badRequest("Type de période de résultat invalide");
        return type;
    }

    private static String normalizePeriodStatus(String value) {
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "OPEN", "CLOSED", "PUBLISHED", "ARCHIVED").contains(status)) throw ApiException.badRequest("Statut de période de résultat invalide");
        return status;
    }

    private record ReadinessClassRow(UUID classId, String className) {}

    private record ReadinessCurriculumRow(UUID classId, String className, String level,
                                          UUID subjectId, String subjectCode, String subjectLabel,
                                          boolean hasActiveEnrollment) {}

    private ReportingPeriodView reportingPeriodView(AcademicReportingPeriod p) {
        return new ReportingPeriodView(p.getId(), p.getAcademicSessionId(), p.getAcademicTermId(), p.getCode(), p.getLabel(),
                p.getPeriodType(), p.getDisplayOrder(), p.getStartDate(), p.getEndDate(), p.getGradeEntryOpensAt(), p.getGradeEntryClosesAt(),
                p.getReviewOpensAt(), p.getReviewClosesAt(), p.getValidationOpensAt(), p.getValidationClosesAt(),
                p.getBulletinPublishOpensAt(), p.getBulletinPublishClosesAt(), p.getCorrectionOpensAt(), p.getCorrectionClosesAt(),
                p.getTeacherSubmissionOpensAt(), p.getTeacherSubmissionClosesAt(), p.getTimezone(),
                p.getCalculationPolicy(), p.getStatus(), p.getVersion());
    }
}
