package com.bbc.sms.discipline;

import com.bbc.sms.discipline.dto.DisciplineDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DisciplineService {

    private static final Logger log = LoggerFactory.getLogger(DisciplineService.class);

    private final DisciplineRepository repo;
    private final StudentRepository students;
    private final TeacherScopeService teacherScope;

    public DisciplineService(DisciplineRepository repo, StudentRepository students,
                             TeacherScopeService teacherScope) {
        this.repo = repo;
        this.students = students;
        this.teacherScope = teacherScope;
    }

    @Transactional(readOnly = true)
    public List<IncidentView> list() {
        UUID schoolId = TenantContext.get();
        Map<UUID, Student> byId = new HashMap<>();
        students.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId)
                .forEach(s -> byId.put(s.getId(), s));
        // Un professeur principal ne voit que les incidents de ses classes.
        Set<UUID> allowed = teacherScope.allowedClassIds();
        return repo.findBySchoolIdOrderByIncidentDateDesc(schoolId).stream()
                .filter(i -> allowed == null || inAllowedClass(byId.get(i.getStudentId()), allowed))
                .map(i -> toView(i, byId.get(i.getStudentId())))
                .toList();
    }

    private static boolean inAllowedClass(Student s, Set<UUID> allowed) {
        return s != null && s.getClassId() != null && allowed.contains(s.getClassId());
    }

    /** Resolve a matricule or UUID to a student card for the incident form. */
    @Transactional(readOnly = true)
    public StudentLookup lookup(String ref) {
        return toLookup(resolveStudent(ref));
    }

    @Transactional
    public IncidentView create(IncidentUpsert in) {
        UUID schoolId = TenantContext.get();
        Student student = resolveStudent(in.studentRef());
        DisciplineIncident i = new DisciplineIncident();
        i.setSchoolId(schoolId);
        i.setStudentId(student.getId());
        i.setIncidentDate(in.incidentDate());
        i.setType(in.type());
        i.setDescription(in.description());
        i.setSanction(in.sanction());
        return toView(repo.save(i), student);
    }

    /**
     * Notify the parent of a student. SMS/email delivery is simulated (same approach
     * as Events) until a provider is wired — we still validate the contact and return
     * a clear outcome so the UI is no longer a dead button.
     */
    @Transactional(readOnly = true)
    public NotifyResult notifyParent(NotifyRequest in) {
        Student student = resolveStudent(in.studentRef());
        String channel = in.channel() == null ? "" : in.channel().trim().toLowerCase();
        if (!channel.equals("sms") && !channel.equals("email")) {
            throw ApiException.badRequest("Canal invalide (sms ou email)");
        }
        String recipient = channel.equals("sms")
                ? blankToNull(student.getParentPhone())
                : null;   // no parent e-mail column yet — SMS is the supported channel
        boolean delivered = recipient != null;
        if (delivered) {
            log.info("Discipline notify [{}] → {} for student {} : {}",
                    channel, recipient, student.getMatricule(),
                    in.message() == null ? "" : in.message().substring(0, Math.min(80, in.message().length())));
        } else {
            log.info("Discipline notify [{}] skipped — no parent contact for {}",
                    channel, student.getMatricule());
        }
        String msg = delivered
                ? (channel.equals("sms")
                    ? "SMS enregistré pour envoi vers " + recipient
                    : "E-mail enregistré pour envoi vers " + recipient)
                : (channel.equals("sms")
                    ? "Aucun téléphone parent renseigné pour cet élève"
                    : "Aucun e-mail parent renseigné pour cet élève — utilisez SMS");
        return new NotifyResult(student.getId(), channel, delivered, recipient, msg);
    }

    @Transactional
    public void delete(UUID id) {
        DisciplineIncident i = repo.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Incident"));
        repo.delete(i);
    }

    private Student resolveStudent(String ref) {
        UUID schoolId = TenantContext.get();
        String raw = ref == null ? "" : ref.trim();
        if (raw.isEmpty()) throw ApiException.badRequest("Matricule / ID élève obligatoire");
        try {
            UUID id = UUID.fromString(raw);
            return students.findByIdAndSchoolId(id, schoolId)
                    .orElseThrow(() -> ApiException.notFound("Élève"));
        } catch (IllegalArgumentException ignored) {
            // not a UUID — treat as matricule
        }
        return students.findBySchoolIdAndMatriculeAndActiveTrue(schoolId, raw)
                .orElseThrow(() -> ApiException.notFound("Élève " + raw));
    }

    private StudentLookup toLookup(Student s) {
        String name = s.getLastName().toUpperCase() + " " + s.getFirstName();
        return new StudentLookup(s.getId(), s.getMatricule(), name, s.getClassName(),
                s.getParentName(), s.getParentPhone());
    }

    private IncidentView toView(DisciplineIncident i, Student s) {
        String name = s == null ? "—" : s.getLastName().toUpperCase() + " " + s.getFirstName();
        String className = s == null ? "" : s.getClassName();
        return new IncidentView(i.getId(), i.getStudentId(), name, className,
                i.getIncidentDate(), i.getType(), i.getDescription(), i.getSanction());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
