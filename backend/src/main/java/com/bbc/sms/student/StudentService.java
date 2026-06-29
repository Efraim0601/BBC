package com.bbc.sms.student;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.ParcoursContext.Scope;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.dto.StudentDtos.*;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class StudentService {

    private final StudentRepository repo;
    private final SchoolClassRepository classes;

    public StudentService(StudentRepository repo, SchoolClassRepository classes) {
        this.repo = repo;
        this.classes = classes;
    }

    @Transactional(readOnly = true)
    public List<StudentView> list(String className) {
        UUID schoolId = TenantContext.get();
        List<Student> rows = (className == null || className.isBlank())
                ? repo.findBySchoolIdAndActiveTrueOrderByLastNameAsc(schoolId)
                : repo.findBySchoolIdAndClassNameAndActiveTrueOrderByLastNameAsc(schoolId, className);
        Scope scope = ParcoursContext.get();
        return rows.stream()
                .filter(s -> inScope(scope, s.getLevel(), s.getSubsystem()))
                .map(this::toView).toList();
    }

    /** Keep a row when no parcours scope is active, or when its level+subsystem match it. */
    public static boolean inScope(Scope scope, String level, String subsystem) {
        if (scope == null) return true;
        return scope.level().equalsIgnoreCase(level == null ? "" : level)
                && scope.subsystem().equalsIgnoreCase(subsystem == null ? "" : subsystem);
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
        UUID schoolId = TenantContext.get();
        s.setFirstName(in.firstName());
        s.setLastName(in.lastName());
        s.setSex(blankToNull(in.sex()));   // "" would violate CHECK (sex IN ('M','F'))
        s.setDob(in.dob());

        if (in.classId() != null) {
            // Authoritative path: bind a real class and copy its name/subsystem/level so
            // free typing can never create phantom classes (review issue #1).
            SchoolClass cls = classes.findByIdAndSchoolId(in.classId(), schoolId)
                    .orElseThrow(() -> ApiException.badRequest("Classe inconnue"));
            s.setClassId(cls.getId());
            s.setClassName(cls.getName());
            s.setSubsystem(cls.getSubsystem());
            s.setLevel(cls.getLevel());
        } else {
            // Unassigned student (no class yet) — keep any legacy/manual hints.
            s.setClassId(null);
            s.setClassName(blankToNull(in.className()));
            s.setSubsystem(blankToNull(in.subsystem()));
            s.setLevel(blankToNull(in.level()));
        }

        s.setParentName(in.parentName());
        s.setParentPhone(in.parentPhone());
    }

    /** Turn empty/blank input into null so optional, CHECK-constrained columns stay valid. */
    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
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
                name, s.getSex(), s.getDob(), s.getClassId(), s.getClassName(), s.getSubsystem(), s.getLevel(),
                s.getParentName(), s.getParentPhone(), s.getPhotoHue());
    }
}
