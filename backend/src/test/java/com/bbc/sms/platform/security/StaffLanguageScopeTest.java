package com.bbc.sms.platform.security;

import com.bbc.sms.academic.security.AcademicAccessPolicyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffLanguageScopeTest {
    final UUID school=UUID.randomUUID(),user=UUID.randomUUID(),own=UUID.randomUUID(),other=UUID.randomUUID();
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final TeacherScopeService scope=spy(new TeacherScopeService(jdbc,mock(AcademicAccessPolicyService.class)));
    void actor(String role) {
        var p=new AppUserPrincipal(user,school,"test",role,"Test","T");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,List.of()));
    }
    @BeforeEach void setup() {
        TenantContext.set(school); ParcoursContext.set(new ParcoursContext.Scope("primary","FR")); actor("principal");
    }
    @AfterEach void clear() {TenantContext.clear();ParcoursContext.clear();SecurityContextHolder.clearContext();}
    @Test void coordinatorKeepsLanguageBoundary() {
        when(jdbc.queryForObject(contains("count(DISTINCT p.subsystem)"),eq(Integer.class),eq(user),eq(school),eq("primary"))).thenReturn(1);
        assertThat(scope.staffLanguageScope()).isEqualTo(new ParcoursContext.Scope("primary","FR"));
    }
    @Test void directorWithBothLanguagesRetainsCycleWideStaffView() {
        when(jdbc.queryForObject(contains("count(DISTINCT p.subsystem)"),eq(Integer.class),eq(user),eq(school),eq("primary"))).thenReturn(2);
        assertThat(scope.staffEmployeeIds()).isNull();
    }
    @Test void administratorIsNotLanguageRestricted() {
        actor("administrator"); assertThat(scope.staffEmployeeIds()).isNull();verifyNoInteractions(jdbc);
    }
    @Test void detailRejectsOtherLanguageEvenInSameCycle() {
        doReturn(Set.of(own)).when(scope).staffEmployeeIds();
        when(jdbc.query(anyString(),any(ResultSetExtractor.class),eq(other),eq(school))).thenReturn("primary");
        assertThatThrownBy(()->scope.assertEmployee(other)).isInstanceOf(ApiException.class).hasMessageContaining("parcours");
    }
    @Test void detailAcceptsOwnLanguageEmployee() {
        doReturn(Set.of(own)).when(scope).staffEmployeeIds();
        when(jdbc.query(anyString(),any(ResultSetExtractor.class),eq(own),eq(school))).thenReturn("primary");
        assertThatCode(()->scope.assertEmployee(own)).doesNotThrowAnyException();
    }
    @Test void creationCannotUseOtherLanguageOrUnassignedClass() {
        doReturn(new ParcoursContext.Scope("primary","FR")).when(scope).staffLanguageScope();
        when(jdbc.queryForObject(contains("FROM school_class"),eq(Integer.class),eq(school),eq("primary"),eq("FR"),eq("SIL A"))).thenReturn(1);
        assertThatCode(()->scope.assertStaffFormClass("SIL A")).doesNotThrowAnyException();
        assertThatThrownBy(()->scope.assertStaffFormClass("Class 1 A")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->scope.assertStaffFormClass(null)).isInstanceOf(ApiException.class);
    }
}
