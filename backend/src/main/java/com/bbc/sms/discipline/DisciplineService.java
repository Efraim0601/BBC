package com.bbc.sms.discipline;

import com.bbc.sms.discipline.dto.DisciplineDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DisciplineService {

    private final DisciplineRepository repo;
    private final StudentRepository students;

    public DisciplineService(DisciplineRepository repo, StudentRepository students) {
        this.repo = repo;
        this.students = students;
    }

    @Transactional(readOnly = true)
    public List<IncidentView> list() {
        UUID schoolId = TenantContext.get();
        Map<UUID, Student> byId = new HashMap<>();
        students.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId)
                .forEach(s -> byId.put(s.getId(), s));
        return repo.findBySchoolIdOrderByIncidentDateDesc(schoolId).stream()
                .map(i -> toView(i, byId.get(i.getStudentId())))
                .toList();
    }

    @Transactional
    public IncidentView create(IncidentUpsert in) {
        UUID schoolId = TenantContext.get();
        Student student = students.findByIdAndSchoolId(in.studentId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Élève"));
        DisciplineIncident i = new DisciplineIncident();
        i.setSchoolId(schoolId);
        i.setStudentId(in.studentId());
        i.setIncidentDate(in.incidentDate());
        i.setType(in.type());
        i.setDescription(in.description());
        i.setSanction(in.sanction());
        return toView(repo.save(i), student);
    }

    @Transactional
    public void delete(UUID id) {
        DisciplineIncident i = repo.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Incident"));
        repo.delete(i);
    }

    private IncidentView toView(DisciplineIncident i, Student s) {
        String name = s == null ? "—" : s.getLastName().toUpperCase() + " " + s.getFirstName();
        String className = s == null ? "" : s.getClassName();
        return new IncidentView(i.getId(), i.getStudentId(), name, className,
                i.getIncidentDate(), i.getType(), i.getDescription(), i.getSanction());
    }
}
