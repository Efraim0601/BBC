package com.bbc.sms.platform.security;

import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import io.jsonwebtoken.Claims;
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

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) { this.jwt = jwt; }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwt.parse(header.substring(7));
                if ("access".equals(claims.get("typ", String.class))) {
                    AppUserPrincipal principal = jwt.toPrincipal(claims);
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    TenantContext.set(principal.schoolId());
                    // Optional parcours scope (Maternelle/Primaire/Secondaire × FR/EN) used
                    // to compartmentalise list views. Validated against the user's allowed
                    // parcours by @parcours when an endpoint requires it.
                    ParcoursContext.set(ParcoursContext.parse(request.getHeader("X-Parcours")));
                    // Verrou de section d'un administrateur de cycle : il se lit dans le
                    // code de rôle, donc sans requête. Contrairement au parcours, il ne
                    // vient pas du client — un en-tête absent ne l'affranchit de rien.
                    ParcoursContext.lockSection(SectionRoles.sectionOf(principal.roleCode()));
                }
            } catch (Exception ignored) {
                // invalid/expired token -> stays anonymous, secured endpoints will 401
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
