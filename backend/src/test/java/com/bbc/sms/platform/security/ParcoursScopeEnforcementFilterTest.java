package com.bbc.sms.platform.security;

import com.bbc.sms.platform.tenant.ParcoursContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParcoursScopeEnforcementFilterTest {
    private final UUID userId = UUID.randomUUID();
    private final AppUserPrincipal principal = new AppUserPrincipal(
            userId, UUID.randomUUID(), "principal", "principal", "Principal", "PR");

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        ParcoursContext.clear();
    }

    @Test
    void explicitPrincipalMustSendAParcoursForBusinessApis() throws Exception {
        ParcoursAccessService access = mock(ParcoursAccessService.class);
        when(access.scopeMode(userId)).thenReturn("EXPLICIT");
        ParcoursScopeEnforcementFilter filter = new ParcoursScopeEnforcementFilter(access);
        MockHttpServletRequest request = request("/api/setup/classes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("PARCOURS_REQUIRED");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void explicitPrincipalCannotForgeAnUnassignedParcours() throws Exception {
        ParcoursAccessService access = mock(ParcoursAccessService.class);
        ParcoursContext.Scope secondary = new ParcoursContext.Scope("secondary", "FR");
        when(access.scopeMode(userId)).thenReturn("EXPLICIT");
        when(access.isAllowed(userId, secondary)).thenReturn(false);
        ParcoursContext.set(secondary);
        ParcoursScopeEnforcementFilter filter = new ParcoursScopeEnforcementFilter(access);
        MockHttpServletRequest request = request("/api/students");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("PARCOURS_SCOPE_MISMATCH");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void assignedParcoursAndCapabilityBootstrapAreAllowedThrough() throws Exception {
        ParcoursAccessService access = mock(ParcoursAccessService.class);
        ParcoursContext.Scope primary = new ParcoursContext.Scope("primary", "EN");
        when(access.scopeMode(userId)).thenReturn("EXPLICIT");
        when(access.isAllowed(userId, primary)).thenReturn(true);
        ParcoursScopeEnforcementFilter filter = new ParcoursScopeEnforcementFilter(access);
        FilterChain chain = mock(FilterChain.class);

        ParcoursContext.set(primary);
        MockHttpServletRequest allowedRequest = request("/api/setup/classes");
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(allowedRequest, allowedResponse, chain);
        verify(chain).doFilter(allowedRequest, allowedResponse);

        ParcoursContext.clear();
        MockHttpServletRequest capabilities = request("/api/access/me/capabilities");
        MockHttpServletResponse capabilitiesResponse = new MockHttpServletResponse();
        filter.doFilterInternal(capabilities, capabilitiesResponse, chain);
        verify(chain).doFilter(capabilities, capabilitiesResponse);
    }

    private MockHttpServletRequest request(String path) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }
}
