package com.bbc.sms.student;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.dto.StudentDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class StudentService {

    private final StudentRepository repo;

    public StudentService(StudentRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public List<StudentView> list(String className) {
        UUID schoolId = TenantContext.get();
        List<Student> rows = (className == null || className.isBlank())
                ? repo.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId)
                : repo.findBySchoolIdAndClassNameAndActiveTrueOrderByLastNameAsc(schoolId, className);
        return rows.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public StudentView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public StudentView create(StudentUpsert in) {
        UUID schoolId = TenantContext.get();
        Student s = new Student();
        s.setSchoolId(schoolId);
        s.setMatricule(nextMatricule(schoolId));
        s.setPhotoHue(ThreadLocalRandom.current().nextInt(0, 360));
        apply(s, in);
        return toView(repo.save(s));
    }

    @Transactional
    public StudentView update(UUID id, StudentUpsert in) {
        Student s = find(id);
        apply(s, in);
        return toView(repo.save(s));
    }

    @Transactional
    public void delete(UUID id) {
        Student s = find(id);
        s.setActive(false);   // soft delete — keeps financial/academic history intact
        repo.save(s);
    }

    private Student find(UUID id) {
        return repo.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Élève"));
    }

    private void apply(Student s, StudentUpsert in) {
        s.setFirstName(in.firstName());
        s.setLastName(in.lastName());
        s.setSex(in.sex());
        s.setDob(in.dob());
        s.setClassName(in.className());
        s.setSubsystem(in.subsystem());
        s.setLevel(in.level());
        s.setParentName(in.parentName());
        s.setParentPhone(in.parentPhone());
    }

    private String nextMatricule(UUID schoolId) {
        long n = repo.countBySchoolIdAndActiveTrue(schoolId) + 1001;
        String code;
        do {
            code = "BBC-" + n++;
        } while (repo.existsBySchoolIdAndMatricule(schoolId, code));
        return code;
    }

    private StudentView toView(Student s) {
        String name = s.getLastName().toUpperCase() + " " + s.getFirstName();
        return new StudentView(s.getId(), s.getMatricule(), s.getFirstName(), s.getLastName(),
                name, s.getSex(), s.getDob(), s.getClassName(), s.getSubsystem(), s.getLevel(),
                s.getParentName(), s.getParentPhone(), s.getPhotoHue());
    }
}
