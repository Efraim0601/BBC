package com.bbc.sms.events;

import com.bbc.sms.events.dto.EventDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.realtime.RealtimeService;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository repo;
    private final StudentRepository students;
    private final RealtimeService realtime;
    private final AuthorizationPolicyService policy;
    private final TeacherScopeService scope;

    public EventService(EventRepository repo, StudentRepository students, RealtimeService realtime,
                        AuthorizationPolicyService policy, TeacherScopeService scope) {
        this.repo = repo;
        this.students = students;
        this.realtime = realtime;
        this.policy = policy;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public List<EventView> list() {
        requireSchool("EVENTS_VIEW");
        Set<String> classes = scope.allowedClassNames();
        return repo.findBySchoolIdOrderByEventDateDesc(TenantContext.get())
                .stream().filter(e -> visible(e, classes)).map(this::toView).toList();
    }

    @Transactional
    public EventView create(EventUpsert in) {
        requireSchool("EVENTS_MANAGE");
        SchoolEvent e = new SchoolEvent();
        e.setSchoolId(TenantContext.get());
        apply(e, in);
        requireEditable(e);
        return toView(repo.save(e));
    }

    @Transactional
    public EventView update(UUID id, EventUpsert in) {
        requireSchool("EVENTS_MANAGE");
        SchoolEvent e = find(id);
        requireEditable(e);
        apply(e, in);
        requireEditable(e);
        return toView(repo.save(e));
    }

    @Transactional
    public void delete(UUID id) {
        requireSchool("EVENTS_MANAGE");
        SchoolEvent e = find(id);
        requireEditable(e);
        repo.delete(e);
    }

    /**
     * Never mark an event delivered without an external delivery provider.
     */
    @Transactional
    public NotifyResult notify(UUID id) {
        requireSchool("EVENTS_MANAGE");
        SchoolEvent e = find(id);
        requireEditable(e);
        throw ApiException.coded(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "NOTIFICATION_PROVIDER_UNAVAILABLE",
                "L'envoi SMS/WhatsApp n'est pas connecté. Aucun message n'a été envoyé. Contactez les parents manuellement.");
    }

    private int countRecipients(SchoolEvent e) {
        List<Student> all = students.findBySchoolIdAndActiveTrueOrderByLastNameAsc(e.getSchoolId());
        if ("classes".equals(e.getAudience()) && e.getTargetClasses() != null && !e.getTargetClasses().isEmpty()) {
            return (int) all.stream().filter(s -> e.getTargetClasses().contains(s.getClassName())).count();
        }
        return all.size();
    }

    private SchoolEvent find(UUID id) {
        return repo.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Événement"));
    }

    private void apply(SchoolEvent e, EventUpsert in) {
        String audience = in.audience() == null ? "all" : in.audience();
        if (!Set.of("all", "classes").contains(audience)) throw ApiException.badRequest("Destinataires invalides");
        if ("classes".equals(audience) && (in.targetClasses() == null || in.targetClasses().isEmpty()))
            throw ApiException.badRequest("Choisissez au moins une classe.");
        e.setTitle(in.title());
        e.setType(in.type());
        e.setEventDate(in.eventDate());
        e.setDescription(in.description());
        e.setAudience(in.audience() == null ? "all" : in.audience());
        e.setTargetClasses(in.targetClasses() == null ? List.of() : in.targetClasses());
    }

    private EventView toView(SchoolEvent e) {
        return new EventView(e.getId(), e.getTitle(), e.getType(), e.getEventDate(),
                e.getDescription(), e.getAudience(), e.getTargetClasses(),
                e.isNotified(), e.getNotifiedAt(), editable(e, scope.allowedClassNames())
                        && policy.canAction("EVENTS_MANAGE"));
    }

    private static boolean visible(SchoolEvent e, Set<String> classes) {
        return classes == null || (!classes.isEmpty() && ("all".equals(e.getAudience())
                || (e.getTargetClasses() != null && e.getTargetClasses().stream().anyMatch(classes::contains))));
    }

    private static boolean editable(SchoolEvent e, Set<String> classes) {
        return classes == null || ("classes".equals(e.getAudience()) && e.getTargetClasses() != null
                && !e.getTargetClasses().isEmpty() && classes.containsAll(e.getTargetClasses()));
    }

    private void requireEditable(SchoolEvent e) {
        if (!editable(e, scope.allowedClassNames()))
            throw ApiException.forbidden("Choisissez uniquement des classes de votre parcours. Un événement école entière relève de l'administrateur.");
    }

    private void requireSchool(String action) {
        policy.require(action, new PolicyResourceContext(TenantContext.get(), null, LocalDate.now(),
                null, null, null, null, null, null, null, null, null));
    }
}
