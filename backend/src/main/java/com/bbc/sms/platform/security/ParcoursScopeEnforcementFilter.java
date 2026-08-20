package com.bbc.sms.platform.security;

import com.bbc.sms.platform.tenant.ParcoursContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces the request-level parcours envelope for explicitly scoped users.
 * This is deliberately a filter rather than a controller convention: a
 * principal must not escape their assigned levels through an older endpoint
 * that only applies a module or staff guard.
 */
@Component
public class ParcoursScopeEnforcementFilter extends OncePerRequestFilter {

    private final ParcoursAccessService parcours;

    public ParcoursScopeEnforcementFilter(ParcoursAccessService parcours) {
        this.parcours = parcours;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        Object rawPrincipal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(rawPrincipal instanceof AppUserPrincipal principal)
                || !requiresExplicitScope(principal)
                || exempt(request)) {
            chain.doFilter(request, response);
            return;
        }

        ParcoursContext.Scope requested = ParcoursContext.get();
        if (requested == null) {
            reject(response, "PARCOURS_REQUIRED",
                    "Sélectionnez un parcours autorisé avant d’accéder à cette ressource.");
            return;
        }
        if (!parcours.isAllowed(principal.userId(), requested)) {
            reject(response, "PARCOURS_SCOPE_MISMATCH",
                    "Ce parcours ne fait pas partie des niveaux attribués à ce compte.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean requiresExplicitScope(AppUserPrincipal principal) {
        return "EXPLICIT".equals(parcours.scopeMode(principal.userId()));
    }

    private boolean exempt(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) return true;
        return path.startsWith("/api/auth/") || path.startsWith("/api/access/me/");
    }

    private void reject(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"code\":\""
                + code + "\",\"message\":\"" + message + "\"}");
    }
}
