package com.bbc.sms.staff;

import com.bbc.sms.hr.DepartmentRepository;
import com.bbc.sms.identity.AppUserRepository;
import com.bbc.sms.platform.mail.MailService;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.SetupService;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffArchivalTest {
    private final EmployeeRepository employees=mock(EmployeeRepository.class);
    private final StaffAccountService accounts=mock(StaffAccountService.class);
    private final StaffService service=new StaffService(employees,mock(DepartmentRepository.class),mock(MailService.class),accounts,
            mock(AppUserRepository.class),mock(SchoolClassRepository.class),mock(SetupService.class),mock(JdbcTemplate.class),
            mock(AuthorizationPolicyService.class),mock(TeacherScopeService.class));
    @AfterEach void clear(){TenantContext.clear();}
    private Employee fixture(){
        var employee=new Employee();employee.setId(UUID.randomUUID());employee.setSchoolId(UUID.randomUUID());
        TenantContext.set(employee.getSchoolId());
        when(employees.findByIdAndSchoolId(employee.getId(),employee.getSchoolId())).thenReturn(Optional.of(employee));
        return employee;
    }
    @Test void individualArchiveAlsoArchivesTheAccount(){
        var employee=fixture();service.delete(employee.getId());
        assertThat(employee.isActive()).isFalse();verify(accounts).deactivateAccount(employee);
        verify(employees,never()).delete(employee);
    }
    @Test void bulkArchiveDeduplicatesAndDoesNotSkipAccountRevocation(){
        var employee=fixture();var missing=UUID.randomUUID();
        var result=service.deleteAll(List.of(employee.getId(),employee.getId(),missing));
        verify(accounts,times(1)).deactivateAccount(employee);
        assertThat(employee.isActive()).isFalse();
    }
}
