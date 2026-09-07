package com.bbc.sms.platform.security;

import com.bbc.sms.identity.AppUser;
import com.bbc.sms.identity.AppUserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/** Checks current account state as well as the signature; revoked sessions cannot refresh. */
@Service
public class SessionTokenService {
    private final JwtService jwt;
    private final AppUserRepository users;

    public SessionTokenService(JwtService jwt, AppUserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public AppUser requireUser(String token, String expectedType) {
        try {
            Claims claims = jwt.parse(token);
            if (!expectedType.equals(claims.get("typ", String.class))) throw invalid();
            String userId = claims.get("uid", String.class);
            if (userId == null) throw invalid();
            UUID id = UUID.fromString(userId);
            AppUser user = users.findActiveLoginById(id).orElseThrow(SessionTokenService::invalid);
            Integer version = claims.get("cv", Integer.class);
            if (!user.getSchoolId().toString().equals(claims.get("sid", String.class))
                    || version == null || version != user.getCredentialsVersion()
                    || !Objects.equals(user.getRoleCode(), claims.get("role", String.class))) {
                throw invalid();
            }
            return user;
        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (IllegalArgumentException | io.jsonwebtoken.JwtException ex) {
            throw invalid();
        }
    }

    public AppUserPrincipal requireAccess(String token) {
        AppUser user = requireUser(token, "access");
        return new AppUserPrincipal(user.getId(), user.getSchoolId(), user.getUsername(),
                user.getRoleCode(), user.getDisplayName(), user.getInitials());
    }

    private static BadCredentialsException invalid() {
        return new BadCredentialsException("Session expirée ou révoquée.");
    }
}
