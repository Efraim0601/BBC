package com.bbc.sms.identity;

import com.bbc.sms.identity.dto.AuthDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.JwtService;
import com.bbc.sms.platform.security.ParcoursAccessService;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final SchoolRepository schools;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final JdbcTemplate jdbc;
    private final ParcoursAccessService parcoursAccess;

    public AuthService(AppUserRepository users, SchoolRepository schools,
                       PasswordEncoder encoder, JwtService jwt, JdbcTemplate jdbc,
                       ParcoursAccessService parcoursAccess) {
        this.users = users;
        this.schools = schools;
        this.encoder = encoder;
        this.jwt = jwt;
        this.jdbc = jdbc;
        this.parcoursAccess = parcoursAccess;
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        AppUser user = resolveUser(req);
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(java.time.OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.LOCKED, "Compte temporairement verrouillé après plusieurs échecs");
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= 5) user.setLockedUntil(java.time.OffsetDateTime.now().plusMinutes(15));
            users.save(user);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(java.time.OffsetDateTime.now());
        users.save(user);
        return tokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest req) {
        Claims claims;
        try {
            claims = jwt.parse(req.refreshToken());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token invalide");
        }
        if (!"refresh".equals(claims.get("typ", String.class))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Token non valide pour le rafraîchissement");
        }
        AppUser user = users.findById(UUID.fromString(claims.get("uid", String.class)))
                .orElseThrow(() -> ApiException.notFound("Utilisateur"));
        return tokens(user);
    }

    private AppUser resolveUser(LoginRequest req) {
        if (req.schoolCode() != null && !req.schoolCode().isBlank()) {
            School school = schools.findByCode(req.schoolCode())
                    .orElseThrow(() -> ApiException.notFound("École"));
            return users.findBySchoolIdAndUsernameAndActiveTrue(school.getId(), req.username())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Identifiants invalides"));
        }
        List<AppUser> matches = users.findByUsernameAndActiveTrue(req.username());
        if (matches.isEmpty()) throw new ApiException(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
        if (matches.size() > 1) throw ApiException.badRequest("Plusieurs écoles : précisez le code école");
        return matches.get(0);
    }

    private TokenResponse tokens(AppUser user) {
        AppUserPrincipal principal = new AppUserPrincipal(
                user.getId(), user.getSchoolId(), user.getUsername(),
                user.getRoleCode(), user.getDisplayName(), user.getInitials());
        String access = jwt.issueAccess(principal);
        String refresh = jwt.issueRefresh(principal);
        return new TokenResponse(access, refresh, jwt.getAccessMs(), buildUserView(user));
    }

    public UserView buildUserView(AppUser user) {
        School school = schools.findById(user.getSchoolId()).orElseThrow();
        Map<String, String> perms = new LinkedHashMap<>();
        jdbc.query("SELECT module, level FROM permission_grant WHERE school_id = ? AND role_code = ?",
                rs -> { perms.put(rs.getString("module"), rs.getString("level")); },
                user.getSchoolId(), user.getRoleCode());
        List<String> modules = perms.entrySet().stream()
                .filter(e -> !"none".equals(e.getValue()))
                .map(Map.Entry::getKey).toList();
        List<Parcours> allowedParcours = parcoursAccess.allowed(user.getId()).stream()
                .map(s -> new Parcours(s.level(), s.subsystem())).toList();
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getInitials(), user.getRoleCode(), user.getSchoolId(),
                school.getCode(), school.getName(), user.getLocale(), perms, modules, allowedParcours);
    }
}
