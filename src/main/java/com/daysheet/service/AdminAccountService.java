package com.daysheet.service;

import com.daysheet.config.ApiException;
import com.daysheet.domain.AdminAudit;
import com.daysheet.domain.AdminUser;
import com.daysheet.dto.AdminDtos.*;
import com.daysheet.repository.AdminAuditRepository;
import com.daysheet.repository.AdminUserRepository;
import com.daysheet.security.CurrentUser;
import com.daysheet.security.JwtService;
import com.daysheet.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Admin logins, admin accounts and the activity log. */
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO5n3Jk6zAKfZ5r1Vn0cL1qQ2bW9yZ6Ga";

    private final AdminUserRepository admins;
    private final AdminAuditRepository audit;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final LoginAttemptService loginAttempts;

    @Transactional
    public AdminSession login(LoginRequest r) {
        String key = "admin:" + r.email().trim().toLowerCase(Locale.ROOT);
        long locked = loginAttempts.minutesLocked(key);
        if (locked > 0) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong passwords. Try again in " + locked + " minutes.");
        Optional<AdminUser> found = admins.findByEmailIgnoreCase(r.email().trim());
        boolean ok = passwordEncoder.matches(r.password(), found.map(AdminUser::getPasswordHash).orElse(DUMMY_HASH));
        if (found.isEmpty() || !ok || !found.get().isActive()) {
            loginAttempts.failed(key);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "That email and password don't match an active admin.");
        }
        loginAttempts.succeeded(key);
        AdminUser a = found.get();
        a.setLastLoginAt(Instant.now());
        log(a.getEmail(), "LOGIN", null);
        return new AdminSession(jwt.issueAdmin(a), toDto(a));
    }

    /** The admin behind the current token, checked against the database so deactivated admins lose access at once. */
    @Transactional(readOnly = true)
    public AdminUser current() {
        return admins.findById(CurrentUser.admin().adminId())
                .filter(AdminUser::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Admin sign-in required."));
    }

    @Transactional(readOnly = true)
    public List<AdminDto> list() {
        current();
        return admins.findAllByOrderByCreatedAtAsc().stream().map(AdminAccountService::toDto).toList();
    }

    @Transactional
    public AdminDto create(CreateAdminRequest r) {
        AdminUser me = current();
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        if (admins.existsByEmailIgnoreCase(email)) throw ApiException.badRequest("An admin with this email already exists.");
        AdminUser a = new AdminUser();
        a.setEmail(email);
        a.setFullName(r.fullName().trim());
        a.setPasswordHash(passwordEncoder.encode(r.password()));
        admins.save(a);
        log(me.getEmail(), "ADMIN_CREATED", email);
        return toDto(a);
    }

    @Transactional
    public AdminDto setActive(Long id, boolean active) {
        AdminUser me = current();
        AdminUser a = admins.findById(id).orElseThrow(() -> ApiException.notFound("Admin"));
        if (!active && a.getId().equals(me.getId())) throw ApiException.badRequest("You can't deactivate yourself.");
        if (!active && a.isActive() && admins.countByActiveTrue() <= 1) throw ApiException.badRequest("At least one admin must stay active.");
        a.setActive(active);
        log(me.getEmail(), active ? "ADMIN_ACTIVATED" : "ADMIN_DEACTIVATED", a.getEmail());
        return toDto(a);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest r) {
        AdminUser me = current();
        if (!passwordEncoder.matches(r.currentPassword(), me.getPasswordHash())) {
            throw ApiException.badRequest("Your current password is not right.");
        }
        me.setPasswordHash(passwordEncoder.encode(r.newPassword()));
        log(me.getEmail(), "PASSWORD_CHANGED", null);
    }

    @Transactional
    public void log(String adminEmail, String action, String details) {
        AdminAudit a = new AdminAudit();
        a.setAdminEmail(adminEmail);
        a.setAction(action);
        a.setDetails(details == null ? null : details.length() > 500 ? details.substring(0, 500) : details);
        audit.save(a);
    }

    @Transactional(readOnly = true)
    public List<AuditRow> activity() {
        current();
        return audit.findTop100ByOrderByCreatedAtDesc().stream()
                .map(a -> new AuditRow(a.getId(), a.getAdminEmail(), a.getAction(), a.getDetails(), a.getCreatedAt())).toList();
    }

    public static AdminDto toDtoPublic(AdminUser a) { return toDto(a); }

    static AdminDto toDto(AdminUser a) {
        return new AdminDto(a.getId(), a.getEmail(), a.getFullName(), a.isActive(), a.getLastLoginAt(), a.getCreatedAt());
    }
}
