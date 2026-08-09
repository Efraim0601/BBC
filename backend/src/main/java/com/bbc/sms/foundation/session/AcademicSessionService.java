package com.bbc.sms.foundation.session;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.bbc.sms.foundation.session.SessionDtos.*;

@Service
public class AcademicSessionService {
    private static final List<String> STATES = List.of("DRAFT", "OPEN", "CLOSED", "ARCHIVED");
    private final AcademicSessionRepository sessions;
    private final AcademicTermRepository terms;
    private final AcademicReportingPeriodRepository reportingPeriods;
    private final AuditService audit;

    public AcademicSessionService(AcademicSessionRepository sessions, AcademicTermRepository terms,
                                  AcademicReportingPeriodRepository reportingPeriods, AuditService audit) {
        this.sessions = sessions;
        this.terms = terms;
        this.reportingPeriods = reportingPeriods;
        this.audit = audit;
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
        period.setGradeEntryOpensAt(in.gradeEntryOpensAt());
        period.setGradeEntryClosesAt(in.gradeEntryClosesAt());
        period.setReviewOpensAt(in.reviewOpensAt());
        period.setReviewClosesAt(in.reviewClosesAt());
        period.setValidationOpensAt(in.validationOpensAt());
        period.setValidationClosesAt(in.validationClosesAt());
        period.setBulletinPublishOpensAt(in.bulletinPublishOpensAt());
        period.setBulletinPublishClosesAt(in.bulletinPublishClosesAt());
        period.setCorrectionOpensAt(in.correctionOpensAt());
        period.setCorrectionClosesAt(in.correctionClosesAt());
        period.setCalculationPolicy(in.calculationPolicy() == null || in.calculationPolicy().isBlank()
                ? "DEFAULT" : in.calculationPolicy().trim().toUpperCase(Locale.ROOT));
        period.setStatus(in.status() == null || in.status().isBlank() ? "DRAFT" : normalizePeriodStatus(in.status()));
        validateWindows(period);
        ensureUniquePeriod(session, period);
        period = reportingPeriods.saveAndFlush(period);
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
        AcademicSession session = find(sessionId);
        assertMutable(session);
        ensureStandardTerms(session);
        StandardStructureView preview = standardStructure(session, false);
        for (ReportingPeriodView view : preview.periods()) {
            ReportingPeriodUpsert input = new ReportingPeriodUpsert(view.code(), view.label(), view.periodType(),
                    view.academicTermId(), view.displayOrder(), view.startDate(), view.endDate(),
                    view.gradeEntryOpensAt(), view.gradeEntryClosesAt(), view.reviewOpensAt(), view.reviewClosesAt(),
                    view.validationOpensAt(), view.validationClosesAt(), view.bulletinPublishOpensAt(),
                    view.bulletinPublishClosesAt(), view.correctionOpensAt(), view.correctionClosesAt(),
                    view.calculationPolicy(), view.status(), null);
            UUID id = reportingPeriods.findBySchoolIdAndAcademicSessionIdAndCode(
                    session.getSchoolId(), session.getId(), view.code()).map(AcademicReportingPeriod::getId).orElse(null);
            upsertReportingPeriod(sessionId, id, input);
        }
        audit.record("REPORTING_STRUCTURE_APPLIED", "AcademicSession", sessionId.toString(), null,
                preview.periods().stream().map(ReportingPeriodView::code).toList(), reason);
        return new StandardStructureView(sessionId, reportingPeriods(sessionId), preview.warnings(), true);
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
        s.setGradeEntryOpensAt(in.gradeEntryOpensAt());
        s.setGradeEntryClosesAt(in.gradeEntryClosesAt());
        s.setBulletinPublishOpensAt(in.bulletinPublishOpensAt());
        s.setBulletinPublishClosesAt(in.bulletinPublishClosesAt());
        validateWindow(in.gradeEntryOpensAt(), in.gradeEntryClosesAt(), "saisie des notes");
        validateWindow(in.bulletinPublishOpensAt(), in.bulletinPublishClosesAt(), "publication des bulletins");
    }

    private void apply(AcademicTerm t, TermUpsert in) {
        t.setCode(in.code().trim().toUpperCase(Locale.ROOT));
        t.setLabel(in.label().trim());
        t.setSequenceNo(in.sequenceNo());
        t.setStartDate(in.startDate());
        t.setEndDate(in.endDate());
        t.setGradeEntryOpensAt(in.gradeEntryOpensAt());
        t.setGradeEntryClosesAt(in.gradeEntryClosesAt());
        t.setBulletinPublishOpensAt(in.bulletinPublishOpensAt());
        t.setBulletinPublishClosesAt(in.bulletinPublishClosesAt());
        validateWindow(in.gradeEntryOpensAt(), in.gradeEntryClosesAt(), "saisie des notes");
        validateWindow(in.bulletinPublishOpensAt(), in.bulletinPublishClosesAt(), "publication des bulletins");
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
                s.getBulletinPublishOpensAt(), s.getBulletinPublishClosesAt(), s.getVersion(), rows);
    }

    private TermView termView(AcademicTerm t) {
        return new TermView(t.getId(), t.getCode(), t.getLabel(), t.getSequenceNo(), t.getStartDate(),
                t.getEndDate(), t.getGradeEntryOpensAt(), t.getGradeEntryClosesAt(),
                t.getBulletinPublishOpensAt(), t.getBulletinPublishClosesAt(), t.getVersion());
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
        return new StandardStructureView(session.getId(), result,
                existing.size() > 3 ? List.of("Les périodes existantes au-delà des trois trimestres seront conservées.") : List.of(), applied);
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
        validateWindow(p.getGradeEntryOpensAt(), p.getGradeEntryClosesAt(), "saisie des notes");
        validateWindow(p.getReviewOpensAt(), p.getReviewClosesAt(), "revue");
        validateWindow(p.getValidationOpensAt(), p.getValidationClosesAt(), "validation");
        validateWindow(p.getBulletinPublishOpensAt(), p.getBulletinPublishClosesAt(), "publication");
        validateWindow(p.getCorrectionOpensAt(), p.getCorrectionClosesAt(), "correction");
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

    private ReportingPeriodView reportingPeriodView(AcademicReportingPeriod p) {
        return new ReportingPeriodView(p.getId(), p.getAcademicSessionId(), p.getAcademicTermId(), p.getCode(), p.getLabel(),
                p.getPeriodType(), p.getDisplayOrder(), p.getStartDate(), p.getEndDate(), p.getGradeEntryOpensAt(), p.getGradeEntryClosesAt(),
                p.getReviewOpensAt(), p.getReviewClosesAt(), p.getValidationOpensAt(), p.getValidationClosesAt(),
                p.getBulletinPublishOpensAt(), p.getBulletinPublishClosesAt(), p.getCorrectionOpensAt(), p.getCorrectionClosesAt(),
                p.getCalculationPolicy(), p.getStatus(), p.getVersion());
    }
}
