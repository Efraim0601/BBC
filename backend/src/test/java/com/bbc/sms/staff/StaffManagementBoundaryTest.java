package com.bbc.sms.staff;

import com.bbc.sms.hr.DepartmentRepository;
import com.bbc.sms.identity.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.mail.MailService;
import com.bbc.sms.platform.security.*;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.SetupService;
import com.bbc.sms.staff.dto.StaffDtos.*;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffManagementBoundaryTest {
    final EmployeeRepository employees=mock(EmployeeRepository.class);
    final StaffAccountService accounts=mock(StaffAccountService.class);
    final AppUserRepository users=mock(AppUserRepository.class);
    final TeacherScopeService scope=mock(TeacherScopeService.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final AuthorizationPolicyService policy=mock(AuthorizationPolicyService.class);
    final StaffService service=new StaffService(employees,mock(DepartmentRepository.class),mock(MailService.class),accounts,
            users,mock(SchoolClassRepository.class),mock(SetupService.class),jdbc,policy,scope);
    @BeforeEach void setup() {
        TenantContext.set(UUID.randomUUID());
        when(scope.staffLevelScope()).thenReturn("secondary");
        when(jdbc.queryForList("SELECT code FROM role",String.class)).thenReturn(List.of("teacher","secondary_teacher","accountant"));
        doThrow(ApiException.forbidden("Role administration required")).when(policy).require(eq("ROLE_MANAGE"),any());
    }
    @AfterEach void clear(){TenantContext.clear();}
    Employee employee(String level, String role) {
        var e=new Employee();e.setId(UUID.randomUUID());e.setSchoolId(TenantContext.get());e.setLevel(level);e.setRoles(Set.of(role));
        when(employees.findByIdAndSchoolId(e.getId(),e.getSchoolId())).thenReturn(Optional.of(e));return e;
    }
    @Test void cannotResetSharedAccountant() {
        var e=employee(null,"accountant");
        assertThatThrownBy(()->service.resetCredentials(e.getId(),AccountOptions.manual())).isInstanceOf(ApiException.class);
        verifyNoInteractions(accounts);
    }
    @Test void cannotArchivePrivilegedEmployeeEvenInOwnLevel() {
        var e=employee("secondary","accountant");
        assertThatThrownBy(()->service.delete(e.getId())).isInstanceOf(ApiException.class);
        verifyNoInteractions(accounts);verify(employees,never()).save(any());
    }
    @Test void loginRoleCannotBeHiddenBehindTeacherProfile() {
        var e=employee("secondary","secondary_teacher");var login=new AppUser();login.setRoleCode("admin");
        when(users.findByEmployeeId(e.getId())).thenReturn(Optional.of(login));
        assertThatThrownBy(()->service.resetCredentials(e.getId(),AccountOptions.manual())).isInstanceOf(ApiException.class);
        verifyNoInteractions(accounts);
    }
    @Test void creatingGlobalRoleRequiresRoleAdministration() {
        var in=new EmployeeUpsert("QA", "M","Permanent","","",null,"secondary",null,null,0,0,
                Set.of("accountant"),true,AccountOptions.manual());
        assertThatThrownBy(()->service.create(in)).isInstanceOf(ApiException.class);
        verifyNoInteractions(accounts);verify(employees,never()).save(any());
    }
    @Test void additionalRoleCannotBeHiddenBehindPrimaryTeacherLogin() {
        var e=employee("secondary","secondary_teacher");var login=new AppUser();login.setId(UUID.randomUUID());login.setRoleCode("secondary_teacher");
        when(users.findByEmployeeId(e.getId())).thenReturn(Optional.of(login));
        when(jdbc.queryForList(contains("FROM app_user_role"),eq(String.class),eq(login.getId())))
                .thenReturn(List.of("secondary_teacher","accountant"));
        assertThatThrownBy(()->service.resetCredentials(e.getId(),AccountOptions.manual())).isInstanceOf(ApiException.class);
        verifyNoInteractions(accounts);
    }
    @Test void ownTeacherResetStillWorks() {
        var e=employee("secondary","secondary_teacher");service.resetCredentials(e.getId(),AccountOptions.manual());
        verify(accounts).provisionOrReset(e,AccountOptions.manual());
    }
}
