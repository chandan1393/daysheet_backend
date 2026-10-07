package com.daysheet.security;

import com.daysheet.domain.AdminUser;
import com.daysheet.domain.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationHours;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-hours}") long expirationHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("ws", user.getWorkspace().getId())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /** Admin sessions are shorter (12 hours) and marked so they only work on /api/admin. */
    public String issueAdmin(AdminUser admin) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(admin.getId()))
                .claim("typ", "admin")
                .claim("email", admin.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(12, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /**
     * Short-lived token (2 hours) that only lets a new user enter their verification code or ask for another.
     * It never works as a login.
     */
    public String issueVerify(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("typ", "verify")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(2, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /** User id from a verification token, or null if it's invalid, expired or not a verification token. */
    public Long parseVerify(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return "verify".equals(c.get("typ", String.class)) ? Long.valueOf(c.getSubject()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns an AuthPrincipal for practice users or an AdminPrincipal for admins. */
    public Object parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if ("verify".equals(c.get("typ", String.class))) throw new IllegalArgumentException("Not a login token");
        if ("admin".equals(c.get("typ", String.class))) {
            return new AdminPrincipal(Long.valueOf(c.getSubject()), c.get("email", String.class));
        }
        Number ws = c.get("ws", Number.class);
        if (ws == null) throw new IllegalArgumentException("Token has no workspace");
        return new AuthPrincipal(Long.valueOf(c.getSubject()), ws.longValue(),
                c.get("email", String.class), c.get("role", String.class));
    }
}
