package com.bbc.sms.events;

import com.bbc.sms.events.dto.EventDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.realtime.RealtimeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository repo;
    private final StudentRepository students;
    private final RealtimeService realtime;

    public EventService(EventRepository repo, StudentRepository students, RealtimeService realtime) {
        this.repo = repo;
        this.students = students;
        this.realtime = realtime;
    }

    @Transactional(readOnly = true)
    public List<EventView> list() {
        return repo.findBySchoolIdOrderByEventDateDesc(TenantContext.get())
                .stream().map(this::toView).toList();
    }

    @Transactional
    public EventView create(EventUpsert in) {
        SchoolEvent e = new SchoolEvent();
        e.setSchoolId(TenantContext.get());
        apply(e, in);
        return toView(repo.save(e));
    }

    @Transactional
    public EventView update(UUID id, EventUpsert in) {
        SchoolEvent e = find(id);
        apply(e, in);
        return toView(repo.save(e));
    }

    @Transactional
    public void delete(UUID id) {
        repo.delete(find(id));
    }

    /**
     * Notify parents of the targeted students. SMS/WhatsApp delivery is simulated
     * (no provider wired yet) — we mark the event notified, count recipients, and
     * push a realtime "events" signal so dashboards/portals refresh.
     */
    @Transactional
    public NotifyResult notify(UUID id) {
        SchoolEvent e = find(id);
        int count = countRecipients(e);
        e.setNotified(true);
        e.setNotifiedAt(LocalDate.now());
        repo.save(e);
        realtime.broadcast(e.getSchoolId(), "events", toView(e));
        return new NotifyResult(e.getId(), count);
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
                e.isNotified(), e.getNotifiedAt());
    }
}
