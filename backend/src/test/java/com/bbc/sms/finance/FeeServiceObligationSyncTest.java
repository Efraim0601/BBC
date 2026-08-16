package com.bbc.sms.finance;

import com.bbc.sms.finance.dto.FeeDtos.FeeConfigUpdate;
import com.bbc.sms.finance.dto.FeeDtos.TrancheView;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeeServiceObligationSyncTest {

    private static final UUID SCHOOL = UUID.randomUUID();
    private static final UUID CLASS = UUID.randomUUID();

    @Mock FeeConfigRepository feeConfigs;
    @Mock StudentFeeRepository studentFees;
    @Mock StudentRepository students;
    @Mock PaymentRepository payments;
    @Mock PaymentChannelRepository channels;
    @Mock SchoolClassRepository classes;
    @Mock AuthorizationPolicyService policy;
    @Mock JdbcTemplate jdbc;

    private FeeService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(SCHOOL);
        service = new FeeService(feeConfigs, studentFees, students, payments, channels, classes, policy, jdbc);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void savingAGridCreatesMissingObligationsAndDoesNotInflateFeesForHistoricExcess() {
        Student paidStudent = student("BBC-1", "Paid");
        Student unpaidStudent = student("BBC-2", "Unpaid");

        FeeConfig grid = new FeeConfig();
        grid.setId(UUID.randomUUID());
        grid.setSchoolId(SCHOOL);
        grid.setLevel("secondary");
        grid.setSubsystem("FR");
        grid.setTotal(100_000);
        grid.setTranches(FeeService.toJson(List.of(
                new TrancheView("T1", 45_000, LocalDate.of(2026, 11, 30)),
                new TrancheView("T2", 55_000, LocalDate.of(2026, 12, 31)))));

        StudentFee existing = new StudentFee();
        existing.setSchoolId(SCHOOL);
        existing.setStudentId(paidStudent.getId());
        existing.setTotal(155_000);
        existing.setPaid(155_000);
        existing.setBalance(0);
        existing.setStatus("paid");

        Payment historicExcess = new Payment();
        historicExcess.setSchoolId(SCHOOL);
        historicExcess.setStudentId(paidStudent.getId());
        historicExcess.setAmount(155_000);

        when(feeConfigs.findBySchoolId(SCHOOL)).thenReturn(List.of(grid));
        when(feeConfigs.save(any(FeeConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(students.findBySchoolIdAndActiveTrueOrderByLastNameAsc(SCHOOL))
                .thenReturn(List.of(paidStudent, unpaidStudent));
        when(studentFees.findBySchoolId(SCHOOL)).thenReturn(List.of(existing));
        when(payments.findBySchoolIdOrderByPaidOnDesc(SCHOOL)).thenReturn(List.of(historicExcess));
        when(classes.findBySchoolIdOrderByName(SCHOOL)).thenReturn(List.of());

        service.upsertConfig(new FeeConfigUpdate("secondary", "FR", null, 100_000,
                List.of(new TrancheView("T1", 45_000, LocalDate.of(2026, 11, 30)),
                        new TrancheView("T2", 55_000, LocalDate.of(2026, 12, 31))), null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StudentFee>> rows = ArgumentCaptor.forClass(List.class);
        verify(studentFees).saveAll(rows.capture());
        assertThat(rows.getValue()).hasSize(2);

        StudentFee paid = rows.getValue().stream()
                .filter(row -> row.getStudentId().equals(paidStudent.getId())).findFirst().orElseThrow();
        assertThat(paid.getTotal()).isEqualTo(100_000);
        assertThat(paid.getPaid()).isEqualTo(100_000);
        assertThat(paid.getBalance()).isZero();
        assertThat(paid.getTranchesPaid()).isEqualTo(2);

        StudentFee unpaid = rows.getValue().stream()
                .filter(row -> row.getStudentId().equals(unpaidStudent.getId())).findFirst().orElseThrow();
        assertThat(unpaid.getTotal()).isEqualTo(100_000);
        assertThat(unpaid.getPaid()).isZero();
        assertThat(unpaid.getBalance()).isEqualTo(100_000);
        assertThat(unpaid.getStatus()).isEqualTo("unpaid");
    }

    private Student student(String matricule, String firstName) {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setSchoolId(SCHOOL);
        student.setMatricule(matricule);
        student.setFirstName(firstName);
        student.setLastName("Student");
        student.setClassId(CLASS);
        student.setClassName("6ème A");
        student.setLevel("secondary");
        student.setSubsystem("FR");
        student.setActive(true);
        return student;
    }
}
