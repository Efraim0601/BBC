package com.bbc.sms.parentportal;

import com.bbc.sms.academic.BulletinSnapshotService;
import com.bbc.sms.academic.GradeRepository;
import com.bbc.sms.academic.SubjectRepository;
import com.bbc.sms.classkit.ClassKitService;
import com.bbc.sms.finance.FeeService;
import com.bbc.sms.finance.documents.FinanceDocumentService;
import com.bbc.sms.guardian.GuardianAccessService;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyDecision;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ParentServicePolicyScopeTest {
    private final UUID schoolId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID deniedChild = UUID.randomUUID();
    private final UUID allowedChild = UUID.randomUUID();

    @Test
    void anyChildActionDoesNotStopAtFirstDeniedChild() {
        GuardianAccessService guardianAccess = mock(GuardianAccessService.class);
        AuthorizationPolicyService policy = mock(AuthorizationPolicyService.class);
        FeeService fees = mock(FeeService.class);
        when(guardianAccess.childIds(schoolId, userId)).thenReturn(List.of(deniedChild, allowedChild));
        when(policy.decide(eq("PARENT_FINANCE_VIEW"), any(PolicyResourceContext.class)))
                .thenAnswer(invocation -> {
                    PolicyResourceContext context = invocation.getArgument(1);
                    return context.studentId().equals(allowedChild)
                            ? PolicyDecision.allow("PARENT_FINANCE_VIEW", "ROLE:parent", "LINKED_CHILDREN", 1)
                            : PolicyDecision.deny("PARENT_FINANCE_VIEW", "POLICY_SCOPE_DENIED", "denied", "denied", 1, null);
                });
        when(fees.parentChannels(schoolId)).thenReturn(List.of());

        ParentService service = service(guardianAccess, policy, fees, mock(FinanceDocumentService.class));

        assertThatCode(() -> service.paymentChannels(principal())).doesNotThrowAnyException();
        verify(policy, times(2)).decide(eq("PARENT_FINANCE_VIEW"), any(PolicyResourceContext.class));
        assertThat(fees.parentChannels(schoolId)).isEmpty();
    }

    @Test
    void financeDocumentDownloadEvaluatesTheDocumentChildScope() {
        GuardianAccessService guardianAccess = mock(GuardianAccessService.class);
        AuthorizationPolicyService policy = mock(AuthorizationPolicyService.class);
        FinanceDocumentService documents = mock(FinanceDocumentService.class);
        when(policy.require(eq("PARENT_FINANCE_VIEW"), any(PolicyResourceContext.class)))
                .thenReturn(PolicyDecision.allow("PARENT_FINANCE_VIEW", "ROLE:parent", "LINKED_CHILDREN", 1));
        when(policy.require(eq("PARENT_DOCUMENT_DOWNLOAD"), any(PolicyResourceContext.class)))
                .thenReturn(PolicyDecision.allow("PARENT_DOCUMENT_DOWNLOAD", "ROLE:parent", "LINKED_CHILDREN", 1));

        ParentService service = service(guardianAccess, policy, mock(FeeService.class), documents);
        UUID invoiceId = UUID.randomUUID();
        UUID generatedId = UUID.randomUUID();
        when(documents.parentDocumentId("INVOICE", invoiceId, allowedChild)).thenReturn(generatedId);

        assertThat(service.financeDocumentId(principal(), "INVOICE", invoiceId, allowedChild))
                .isEqualTo(generatedId);
        verify(policy).require(eq("PARENT_FINANCE_VIEW"), any(PolicyResourceContext.class));
        verify(policy).require(eq("PARENT_DOCUMENT_DOWNLOAD"), any(PolicyResourceContext.class));
    }

    @Test
    void childrenDoNotMaterializeFinanceOrAttendanceWhenRelationshipFlagsDenyThem() {
        GuardianAccessService guardianAccess = mock(GuardianAccessService.class);
        AuthorizationPolicyService policy = mock(AuthorizationPolicyService.class);
        FeeService fees = mock(FeeService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StudentRepository students = mock(StudentRepository.class);
        UUID child = UUID.randomUUID();
        Student student = new Student();
        student.setId(child);
        student.setSchoolId(schoolId);
        student.setMatricule("BBC-1001");
        student.setFirstName("Ada");
        student.setLastName("Lovelace");
        student.setClassName("6e A");
        student.setActive(true);
        when(guardianAccess.childIds(schoolId, userId)).thenReturn(List.of(child));
        when(students.findByIdAndSchoolId(child, schoolId)).thenReturn(Optional.of(student));
        when(policy.decide(any(), any(PolicyResourceContext.class))).thenAnswer(invocation -> {
            String action = invocation.getArgument(0);
            return "PARENT_CHILD_SUMMARY_VIEW".equals(action)
                    ? PolicyDecision.allow(action, "ROLE:parent", "LINKED_CHILDREN", 1)
                    : PolicyDecision.deny(action, "POLICY_SCOPE_DENIED", "denied", "denied", 1, null);
        });

        ParentService service = service(jdbc, students, guardianAccess, policy, fees,
                mock(FinanceDocumentService.class));
        var children = service.children(principal());

        assertThat(children).singleElement().satisfies(view -> {
            assertThat(view.financeVisible()).isFalse();
            assertThat(view.attendanceVisible()).isFalse();
            assertThat(view.feeStatus()).isNull();
            assertThat(view.balance()).isZero();
            assertThat(view.attendanceRate()).isZero();
        });
        verifyNoInteractions(fees, jdbc);
    }

    @Test
    void unknownFinanceDocumentTypeFailsBeforePolicyOrDocumentLookup() {
        AuthorizationPolicyService policy = mock(AuthorizationPolicyService.class);
        ParentService service = service(mock(GuardianAccessService.class), policy,
                mock(FeeService.class), mock(FinanceDocumentService.class));

        assertThatThrownBy(() -> service.financeDocumentId(principal(), "PROFILE", UUID.randomUUID(), allowedChild))
                .isInstanceOf(com.bbc.sms.platform.common.ApiException.class);
        verifyNoInteractions(policy);
    }

    private ParentService service(JdbcTemplate jdbc,
                                  StudentRepository students,
                                  GuardianAccessService guardianAccess,
                                  AuthorizationPolicyService policy,
                                  FeeService fees,
                                  FinanceDocumentService documents) {
        return new ParentService(jdbc, students,
                mock(GradeRepository.class), mock(SubjectRepository.class), mock(SuggestionRepository.class),
                mock(ClassKitService.class), fees, guardianAccess, policy,
                mock(BulletinSnapshotService.class), documents,
                mock(com.bbc.sms.discipline.DisciplineRepository.class),
                mock(com.bbc.sms.health.InfirmaryVisitRepository.class),
                mock(com.bbc.sms.events.EventRepository.class),
                mock(com.bbc.sms.messaging.CorrespondenceRepository.class));
    }

    private ParentService service(GuardianAccessService guardianAccess,
                                  AuthorizationPolicyService policy,
                                  FeeService fees,
                                  FinanceDocumentService documents) {
        return new ParentService(mock(JdbcTemplate.class), mock(StudentRepository.class),
                mock(GradeRepository.class), mock(SubjectRepository.class), mock(SuggestionRepository.class),
                mock(ClassKitService.class), fees, guardianAccess, policy,
                mock(BulletinSnapshotService.class), documents,
                mock(com.bbc.sms.discipline.DisciplineRepository.class),
                mock(com.bbc.sms.health.InfirmaryVisitRepository.class),
                mock(com.bbc.sms.events.EventRepository.class),
                mock(com.bbc.sms.messaging.CorrespondenceRepository.class));
    }

    private AppUserPrincipal principal() {
        return new AppUserPrincipal(userId, schoolId, "parent", "parent", "Parent", "P");
    }
}
