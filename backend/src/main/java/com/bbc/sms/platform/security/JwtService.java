package com.bbc.sms.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/** Issues and validates JWT access & refresh tokens. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessMs;
    private final long refreshMs;

    public JwtService(
            @Value("${bbc.security.jwt-secret}") String secret,
            @Value("${bbc.security.access-token-minutes}") long accessMinutes,
            @Value("${bbc.security.refresh-token-days}") long refreshDays) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.accessMs = accessMinutes * 60_000;
        this.refreshMs = refreshDays * 24 * 3_600_000;
    }

    public String issueAccess(AppUserPrincipal p) {
        Date now = new Date();
        return Jwts.builder()
                .subject(p.username())
                .claim("uid", p.userId().toString())
                .claim("sid", p.schoolId().toString())
                .claim("role", p.roleCode())
                .claim("name", p.displayName())
                .claim("initials", p.initials())
                .claim("typ", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessMs))
                .signWith(key)
                .compact();
    }

    public String issueRefresh(AppUserPrincipal p) {
        Date now = new Date();
        return Jwts.builder()
                .subject(p.username())
                .claim("uid", p.userId().toString())
                .claim("sid", p.schoolId().toString())
                .claim("typ", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public AppUserPrincipal toPrincipal(Claims c) {
        return new AppUserPrincipal(
                UUID.fromString(c.get("uid", String.class)),
                UUID.fromString(c.get("sid", String.class)),
                c.getSubject(),
                c.get("role", String.class),
                c.get("name", String.class),
                c.get("initials", String.class));
    }

    public long getAccessMs() { return accessMs; }
}
