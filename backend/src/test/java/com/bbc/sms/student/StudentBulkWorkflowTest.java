package com.bbc.sms.student;

import com.bbc.sms.foundation.enrollment.EnrollmentService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.SetupService;
import com.bbc.sms.student.dto.StudentDtos.*;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;
import jakarta.validation.Validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudentBulkWorkflowTest {
    private final UUID schoolId = UUID.randomUUID();
    private final StudentRepository students = mock(StudentRepository.class);
    private final SchoolClassRepository classes = mock(SchoolClassRepository.class);
    private final TeacherScopeService teacherScope = mock(TeacherScopeService.class);
    private final AuthorizationPolicyService policy = mock(AuthorizationPolicyService.class);
    private final EnrollmentService enrollments = mock(EnrollmentService.class);
    private final StudentService service = new StudentService(students, classes, mock(SetupService.class),
            teacherScope, enrollments, policy, mock(JdbcTemplate.class));

    @AfterEach void clear() { TenantContext.clear(); }

    @Test
    void bulkRemovalHonorsTheSameActionDenialAsSingleRemoval() {
        TenantContext.set(schoolId);
        Student student = new Student();
        student.setId(UUID.randomUUID()); student.setSchoolId(schoolId); student.setActive(true);
        when(students.findByIdAndSchoolId(student.getId(), schoolId)).thenReturn(Optional.of(student));
        doThrow(ApiException.forbidden("Deactivation denied"))
                .when(policy).require(eq("STUDENT_PROFILE_DEACTIVATE"), any());

        BulkDeleteResult result = service.deleteAll(List.of(student.getId(), student.getId()));

        assertThat(result.deleted()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(student.isActive()).isTrue();
        verify(students, never()).save(any());
        verify(policy).require(eq("STUDENT_PROFILE_DEACTIVATE"), any());
    }

    @Test
    void bulkRemovalStillSoftDeletesAnAuthorizedStudentOnlyOnce() {
        TenantContext.set(schoolId);
        Student student = new Student();
        student.setId(UUID.randomUUID()); student.setSchoolId(schoolId); student.setActive(true);
        when(students.findByIdAndSchoolId(student.getId(), schoolId)).thenReturn(Optional.of(student));

        BulkDeleteResult result = service.deleteAll(List.of(student.getId(), student.getId()));

        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(student.isActive()).isFalse();
        verify(students).save(student);
        verify(policy).require(eq("STUDENT_PROFILE_DEACTIVATE"), any());
    }

    @Test
    void importAcceptsOptionalFirstNamesWithoutInventingADashAndRejectsBlankNames() {
        TenantContext.set(schoolId);
        UUID classId = UUID.randomUUID();
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(classId); schoolClass.setSchoolId(schoolId); schoolClass.setName("CE1 A");
        schoolClass.setLevel("primary"); schoolClass.setSubsystem("FR");
        when(classes.findByIdAndSchoolId(classId, schoolId)).thenReturn(Optional.of(schoolClass));
        when(students.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        List<StudentImportRow> rows = List.of(
                mapper.convertValue(Map.of("lastName", "MONONYM", "firstName", ""), StudentImportRow.class),
                mapper.convertValue(Map.of("name", "SINGLE"), StudentImportRow.class),
                mapper.convertValue(Map.of("lastName", "FAMILY", "firstName", "Given"), StudentImportRow.class),
                mapper.convertValue(Map.of("lastName", "", "firstName", ""), StudentImportRow.class),
                mapper.convertValue(Map.of("lastName", "FUTURE", "dob", LocalDate.now().plusDays(1).toString()), StudentImportRow.class));

        StudentImportResult result = service.importForClass(new StudentImportRequest(classId, null, rows));

        assertThat(result.created()).isEqualTo(3);
        assertThat(result.failed()).isEqualTo(2);
        ArgumentCaptor<Student> saved = ArgumentCaptor.forClass(Student.class);
        verify(students, times(3)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues()).extracting(Student::getFirstName).containsExactly("", "", "Given");
        verify(enrollments, times(3)).syncCurrent(any());
    }

    @Test
    void registrationValidationAllowsUnknownBirthDatesButRejectsFutureDates() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(mapper.convertValue(Map.of("lastName", "MONONYM"), StudentUpsert.class))).isEmpty();
            var future = mapper.convertValue(Map.of("lastName", "MONONYM", "dob", LocalDate.now().plusDays(1).toString()), StudentUpsert.class);
            assertThat(validator.validate(future)).extracting(v -> v.getPropertyPath().toString()).containsExactly("dob");
        }
    }

    @Test
    void duplicateLookupAlsoMatchesStudentsWhoseFirstNameIsEmpty() {
        TenantContext.set(schoolId);
        StudentRepository.DuplicateRow existing = mock(StudentRepository.DuplicateRow.class);
        when(existing.getId()).thenReturn(UUID.randomUUID());
        when(existing.getLastName()).thenReturn("MONONYM");
        when(existing.getFirstName()).thenReturn("");
        when(students.findProjectedBySchoolIdAndActiveTrue(schoolId)).thenReturn(List.of(existing));
        var result = service.checkDuplicates("Mononym", null, null, null, null, null);
        assertThat(result.exists()).isTrue();
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().getFirst().sameName()).isTrue();
    }
}
