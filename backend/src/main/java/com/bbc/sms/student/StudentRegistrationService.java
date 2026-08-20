package com.bbc.sms.student;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.guardian.GuardianDtos.*;
import com.bbc.sms.guardian.GuardianService;
import com.bbc.sms.student.dto.StudentDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class StudentRegistrationService {
    private final StudentService students; private final GuardianService guardians; private final AuditService audit;
    public StudentRegistrationService(StudentService students,GuardianService guardians,AuditService audit){this.students=students;this.guardians=guardians;this.audit=audit;}

    public record RegistrationRequest(@NotNull @Valid StudentUpsert student,@NotEmpty List<@Valid GuardianInput> guardians){}
    public record RegistrationView(StudentView student,List<GuardianRelationshipView> guardians,String message){}

    @Transactional
    public RegistrationView register(RegistrationRequest req){
        StudentView student=students.create(req.student()); List<GuardianRelationshipView> links=new ArrayList<>();
        for(GuardianInput g:req.guardians())links.add(guardians.add(student.id(),g));
        RegistrationView out=new RegistrationView(student,links,"Inscription complète créée");
        audit.record("STUDENT_REGISTERED_WITH_FAMILY","Student",student.id().toString(),null,Map.of("student",student,"guardianCount",links.size()),"Inscription élève et famille");
        return out;
    }
}
