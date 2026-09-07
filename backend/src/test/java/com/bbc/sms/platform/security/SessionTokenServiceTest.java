package com.bbc.sms.platform.security;

import com.bbc.sms.identity.AppUser;
import com.bbc.sms.identity.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionTokenServiceTest {
    private final JwtService jwt = new JwtService("Y2hhbmdlLW1lLXRoaXMtaXMtYS1kZXYtb25seS1zZWNyZXQtMzJieXRlcyE=", 5, 1);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final SessionTokenService service = new SessionTokenService(jwt, users);
    private AppUser user;
    private AppUserPrincipal principal;

    @BeforeEach void setup() {
        user = new AppUser();
        user.setId(UUID.randomUUID()); user.setSchoolId(UUID.randomUUID());
        user.setUsername("teacher"); user.setDisplayName("Current name");
        user.setInitials("CN"); user.setRoleCode("teacher"); user.setCredentialsVersion(3);
        principal = new AppUserPrincipal(user.getId(), user.getSchoolId(), "teacher", "teacher", "Old name", "ON");
        when(users.findActiveLoginById(user.getId())).thenReturn(Optional.of(user));
    }

    @Test void validSessionUsesTheCurrentProfile() {
        assertThat(service.requireAccess(jwt.issueAccess(principal,3)).displayName()).isEqualTo("Current name");
        assertThat(service.requireUser(jwt.issueRefresh(principal,3),"refresh")).isSameAs(user);
    }

    @Test void resetInvalidatesBothTypesOfToken() {
        assertThatThrownBy(() -> service.requireAccess(jwt.issueAccess(principal,2))).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.requireUser(jwt.issueRefresh(principal,2),"refresh")).isInstanceOf(BadCredentialsException.class);
    }

    @Test void disabledOrArchivedStaffCannotUseEitherType() {
        when(users.findActiveLoginById(user.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireAccess(jwt.issueAccess(principal,3))).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.requireUser(jwt.issueRefresh(principal,3),"refresh")).isInstanceOf(BadCredentialsException.class);
    }

    @Test void staleRoleCannotSurviveInAnAccessOrRefreshToken() {
        user.setRoleCode("accountant");
        assertThatThrownBy(() -> service.requireAccess(jwt.issueAccess(principal,3))).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.requireUser(jwt.issueRefresh(principal,3),"refresh")).isInstanceOf(BadCredentialsException.class);
    }

    @Test void rejectsWrongTenantAndTokenType() {
        var foreign = new AppUserPrincipal(user.getId(), UUID.randomUUID(), "teacher", "teacher", "Teacher", "T");
        assertThatThrownBy(() -> service.requireAccess(jwt.issueAccess(foreign,3))).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.requireAccess(jwt.issueRefresh(principal,3))).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.requireUser(jwt.issueAccess(principal,3),"refresh")).isInstanceOf(BadCredentialsException.class);
    }

    @Test void rejectsMalformedTokensAndLegacyUnversionedSessions() {
        assertThatThrownBy(() -> service.requireAccess("invalid")).isInstanceOf(BadCredentialsException.class);
        // A previous-generation token without cv must not silently acquire today's version.
        var claims = io.jsonwebtoken.Jwts.claims().add("uid",user.getId().toString())
                .add("sid",user.getSchoolId().toString()).add("role","teacher").add("typ","access").build();
        JwtService parser = mock(JwtService.class);
        when(parser.parse("legacy")).thenReturn(claims);
        assertThatThrownBy(() -> new SessionTokenService(parser,users).requireAccess("legacy"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
