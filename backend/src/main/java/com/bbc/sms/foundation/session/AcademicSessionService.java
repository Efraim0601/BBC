package com.bbc.sms.foundation.session;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.bbc.sms.foundation.session.SessionDtos.*;

@Service
public class AcademicSessionService {
    private static final List<String> STATES = List.of("DRAFT", "OPEN", "CLOSED", "ARCHIVED");
    private final AcademicSessionRepository sessions;
    private final AcademicTermRepository terms;
    private final AuditService audit;

    public AcademicSessionService(AcademicSessionRepository sessions, AcademicTermRepository terms,
                                  AuditService audit) {
        this.sessions = sessions;
        this.terms = terms;
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
}
