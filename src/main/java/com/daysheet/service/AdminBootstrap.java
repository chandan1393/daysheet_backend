package com.daysheet.service;

import com.daysheet.domain.AdminUser;
import com.daysheet.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Creates the first admin when there is none.
 * Production: set ADMIN_EMAIL and ADMIN_PASSWORD, start once, log in, then remove ADMIN_PASSWORD from the settings.
 * Development: a default admin is created so you can try the panel straight away.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    static final String DEV_EMAIL = "admin@daysheet.in";
    static final String DEV_PASSWORD = "Daysheet@Admin123";

    private final AdminUserRepository admins;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.env}") private String env;
    @Value("${app.admin.email}") private String email;
    @Value("${app.admin.password}") private String password;
    @Value("${app.admin.name}") private String name;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (admins.count() > 0) {
            if (!password.isBlank() && "production".equalsIgnoreCase(env)) {
                log.warn("ADMIN_PASSWORD is still set. An admin already exists, so remove it from the server settings.");
            }
            return;
        }
        boolean production = "production".equalsIgnoreCase(env);
        String e = email.isBlank() ? (production ? "" : DEV_EMAIL) : email.trim().toLowerCase(Locale.ROOT);
        String p = password.isBlank() ? (production ? "" : DEV_PASSWORD) : password;
        if (e.isBlank() || p.isBlank()) {
            log.error("No admin account exists. Set ADMIN_EMAIL and ADMIN_PASSWORD (10+ characters) and restart.");
            return;
        }
        if (p.length() < 10) {
            log.error("ADMIN_PASSWORD must be at least 10 characters. No admin was created.");
            return;
        }
        AdminUser a = new AdminUser();
        a.setEmail(e);
        a.setFullName(name.isBlank() ? "Admin" : name.trim());
        a.setPasswordHash(passwordEncoder.encode(p));
        admins.save(a);
        if (production) {
            log.info("Created admin {}. Log in at /admin, then remove ADMIN_PASSWORD from the server settings.", e);
        } else {
            log.warn("Created development admin {} with password {} . Open http://localhost:4200/admin", e, p);
        }
    }
}
