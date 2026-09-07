package com.bbc.sms.staff;

import com.bbc.sms.identity.AppUser;
import com.bbc.sms.identity.AppUserRepository;
import com.bbc.sms.identity.SchoolRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.mail.MailService;
import com.bbc.sms.staff.dto.StaffDtos.AccountOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffAccountServiceTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final SchoolRepository schools = mock(SchoolRepository.class);
    private final MailService mail = mock(MailService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final StaffAccountService service = new StaffAccountService(users, schools, encoder, mail, jdbc);
    private Employee employee;

    @BeforeEach void prepare() {
        employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setSchoolId(UUID.randomUUID());
        employee.setName("Ngono Test");
        employee.setCode("EMP-TEST");
        employee.setRoles(Set.of("teacher"));
        employee.setLevel("primary");
        when(jdbc.queryForList("SELECT code FROM role", String.class)).thenReturn(List.of("teacher", "secondary_teacher", "principal"));
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(call -> {
            AppUser user = call.getArgument(0);
            if (user.getId() == null) user.setId(UUID.randomUUID());
            return user;
        });
    }

    @Test void createsWithoutEmailOrPhoneAndReturnsWorkingCredentials() {
        var result = service.provisionOrReset(employee, new AccountOptions("Manual.Teacher", false));
        var captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users, atLeastOnce()).saveAndFlush(captor.capture());
        var user = captor.getValue();
        assertThat(result.username()).isEqualTo("manual.teacher");
        assertThat(result.password()).hasSize(12).containsPattern("[A-Za-z]").containsPattern("[2-9]");
        assertThat(encoder.matches(result.password(), user.getPasswordHash())).isTrue();
        assertThat(user.getEmployeeId()).isEqualTo(employee.getId());
        assertThat(user.getSchoolId()).isEqualTo(employee.getSchoolId());
        assertThat(user.getRoleCode()).isEqualTo("teacher");
        assertThat(result.emailRequested()).isFalse();
        assertThat(result.emailSent()).isFalse();
        assertThat(result.toString()).doesNotContain(result.password());
        verifyNoInteractions(mail);
    }

    @Test void cannotIssueCredentialsForArchivedStaff() {
        employee.setActive(false);
        assertThatThrownBy(() -> service.provisionOrReset(employee, AccountOptions.manual()))
                .isInstanceOf(ApiException.class).hasMessageContaining("Réactivez");
        verifyNoInteractions(users, mail);
    }

    @Test void archivalDeactivatesAndRevokesTheLoginWithoutChangingHistory() {
        var user = new AppUser();
        user.setCredentialsVersion(3);
        when(users.findByEmployeeId(employee.getId())).thenReturn(Optional.of(user));
        service.deactivateAccount(employee);
        assertThat(user.isActive()).isFalse();
        assertThat(user.getCredentialsVersion()).isEqualTo(4);
        verify(users).saveAndFlush(user);
        verifyNoInteractions(mail);
    }

    @Test void havingAnEmailDoesNotOptInToSendingAndGeneratedNamesAvoidCollisions() {
        employee.setEmail("qa@example.test");
        when(users.existsBySchoolIdAndUsernameIgnoreCase(employee.getSchoolId(), "ngono.test")).thenReturn(true);
        var result = service.provisionOrReset(employee, null);
        assertThat(result.username()).isEqualTo("ngono.test2");
        assertThat(result.password()).isNotBlank();
        verifyNoInteractions(mail);
    }

    @Test void whatsappOnlyContactStillCreatesARegularUsernameLogin() {
        employee.setPhone("+237 600 000 001");
        var result = service.provisionOrReset(employee, AccountOptions.manual());
        assertThat(result.username()).isEqualTo("ngono.test");
        assertThat(employee.getPhone()).isEqualTo("+237 600 000 001");
        verifyNoInteractions(mail);
    }

    @Test void sendsOnlyWhenRequestedAndReturnsTheSamePasswordForManualSharing() {
        employee.setEmail("qa@example.test");
        when(mail.sendCredentials(eq(employee.getSchoolId()), eq(employee.getName()), eq(employee.getEmail()),
                anyString(), anyString(), isNull())).thenReturn(true);
        var result = service.provisionOrReset(employee, new AccountOptions(null, true));
        assertThat(result.emailRequested()).isTrue();
        assertThat(result.emailSent()).isTrue();
        verify(mail).sendCredentials(employee.getSchoolId(), employee.getName(), employee.getEmail(),
                result.username(), result.password(), null);
    }

    @Test void emailFailureStillReturnsUsableCredentials() {
        employee.setEmail("qa@example.test");
        var result = service.provisionOrReset(employee, new AccountOptions(null, true));
        assertThat(result.hasAccount()).isTrue();
        assertThat(result.emailRequested()).isTrue();
        assertThat(result.emailSent()).isFalse();
        assertThat(result.password()).isNotBlank();
    }

    @Test void emailWithoutAddressAndTakenOrMalformedUsernamesFailBeforePasswordWrites() {
        assertThatThrownBy(() -> service.provisionOrReset(employee, new AccountOptions(null, true)))
                .isInstanceOf(ApiException.class).hasMessageContaining("e-mail");
        when(users.existsBySchoolIdAndUsernameIgnoreCase(employee.getSchoolId(), "taken")).thenReturn(true);
        assertThatThrownBy(() -> service.provisionOrReset(employee, new AccountOptions("taken", false)))
                .isInstanceOf(ApiException.class).hasMessageContaining("déjà utilisé");
        assertThatThrownBy(() -> service.provisionOrReset(employee, new AccountOptions("has spaces", false)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Identifiant");
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(mail);
    }

    @Test void resetKeepsTheUsernameReplacesThePasswordAndClearsTheLock() {
        var user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setSchoolId(employee.getSchoolId());
        user.setEmployeeId(employee.getId());
        user.setUsername("original");
        user.setPasswordHash(encoder.encode("OldPassword7"));
        user.setFailedAttempts(5);
        user.setLockedUntil(OffsetDateTime.now().plusMinutes(10));
        when(users.findByEmployeeId(employee.getId())).thenReturn(Optional.of(user));
        var result = service.provisionOrReset(employee, AccountOptions.manual());
        assertThat(result.username()).isEqualTo("original");
        assertThat(encoder.matches(result.password(), user.getPasswordHash())).isTrue();
        assertThat(encoder.matches("OldPassword7", user.getPasswordHash())).isFalse();
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThatThrownBy(() -> service.provisionOrReset(employee, new AccountOptions("different", false)))
                .isInstanceOf(ApiException.class).hasMessageContaining("conserve");
        verifyNoInteractions(mail);
    }

    @Test void existingEmailOnlyCallersContinueSendingWithoutExposingPasswords() {
        employee.setEmail("qa@example.test");
        var result = service.provisionOrReset(employee);
        assertThat(result.password()).isNull();
        assertThat(result.emailRequested()).isTrue();
        verify(mail).sendCredentials(eq(employee.getSchoolId()), eq(employee.getName()), eq(employee.getEmail()),
                anyString(), anyString(), isNull());
    }
}
