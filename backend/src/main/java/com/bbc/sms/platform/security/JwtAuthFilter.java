package com.bbc.sms.platform.security;

import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Validates the Bearer access token, binds the principal and the tenant for the request. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final SessionTokenService sessions;

    public JwtAuthFilter(SessionTokenService sessions) { this.sessions = sessions; }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                AppUserPrincipal principal = sessions.requireAccess(header.substring(7));
                var requestedScope = ParcoursContext.parse(request.getHeader("X-Parcours"));
                var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                TenantContext.set(principal.schoolId());
                // The client may narrow its parcours; the server controls the section lock.
                ParcoursContext.set(requestedScope);
                ParcoursContext.lockSection(SectionRoles.sectionOf(principal.roleCode()));
            } catch (Exception ignored) {
                // Fail closed even if authentication failed after a context was bound.
                SecurityContextHolder.clearContext();
                TenantContext.clear();
                ParcoursContext.clear();
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            ParcoursContext.clear();
        }
    }
}
