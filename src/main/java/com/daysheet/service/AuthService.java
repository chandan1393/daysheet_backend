package com.daysheet.service;

import com.daysheet.config.ApiException;
import com.daysheet.domain.*;
import com.daysheet.dto.AuthDtos.*;
import com.daysheet.repository.AppUserRepository;
import com.daysheet.repository.PasswordResetTokenRepository;
import com.daysheet.security.LoginAttemptService;
import com.daysheet.repository.ServiceOfferingRepository;
import com.daysheet.repository.WorkspaceRepository;
import com.daysheet.security.CurrentUser;
import com.daysheet.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Rough multipliers so preset prices look sensible in each currency. */
    private static final Map<String, Integer> PRICE_FACTOR = Map.of(
            "INR", 20, "AED", 4, "PKR", 100, "NGN", 600, "ZAR", 15, "SGD", 1, "MYR", 4);

    private final AppUserRepository users;
    private final WorkspaceRepository workspaces;
    private final ServiceOfferingRepository services;
    private final WorkspaceService workspaceService;
    private final SampleDataService sampleData;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttempts;
    private final PasswordResetTokenRepository resetTokens;
    private final MailService mail;
    private final EmailGuard emailGuard;
    private final EmailVerificationService verification;
    private final WorkspacePurgeService purge;

    @Value("${app.public-url}")
    private String publicUrl;

    /** Used so a login for an unknown email takes as long as a wrong password (no account probing by timing). */
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO5n3Jk6zAKfZ5r1Vn0cL1qQ2bW9yZ6Ga";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.trial-days}")
    private int trialDays;

    public List<ProfessionDto> professions() {
        return Arrays.stream(Profession.values())
                .map(p -> new ProfessionDto(p.name(), p.displayName(), p.clientLabel(), p.clientLabelPlural(),
                        p.sessionLabel(), p.services().stream().map(Profession.ServicePreset::name).toList()))
                .toList();
    }

    @Transactional
    public RegisterResponse register(RegisterRequest r) {
        if (r.website() != null && !r.website().isBlank()) {
            throw ApiException.badRequest("We couldn't create the account. Please try again.");
        }
        String email = r.email().trim().toLowerCase();
        emailGuard.check(email);
        Optional<AppUser> existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            AppUser old = existing.get();
            // An earlier signup that never verified: replace it, so whoever owns the inbox ends up with their own details.
            if (!old.isPendingVerification() || !purge.purgeUnverified(old.getWorkspace().getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists. Log in instead.");
            }
        }
        WorkspaceService.validateZone(r.timezone());

        Profession p = r.profession();
        Workspace w = new Workspace();
        w.setName(r.workspaceName().trim());
        w.setSlug(uniqueSlug(r.workspaceName()));
        w.setProfession(p);
        w.setClientLabel(p.clientLabel());
        w.setClientLabelPlural(p.clientLabelPlural());
        w.setSessionLabel(p.sessionLabel());
        w.setCurrency(r.currency().toUpperCase());
        w.setTimezone(r.timezone());
        w.setEmail(email);
        w.setPlan(Plan.TRIAL);
        w.setTrialEndsAt(Instant.now().plus(trialDays, ChronoUnit.DAYS));
        workspaces.save(w);

        AppUser u = new AppUser();
        u.setWorkspace(w);
        u.setFullName(r.fullName().trim());
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(r.password()));
        u.setRole(Role.OWNER);
        u.setEmailVerified(false);
        users.save(u);

        workspaceService.createDefaultHours(w);

        int factor = PRICE_FACTOR.getOrDefault(w.getCurrency(), 1);
        List<ServiceOffering> created = new ArrayList<>();
        for (Profession.ServicePreset preset : p.services()) {
            ServiceOffering s = new ServiceOffering();
            s.setWorkspace(w);
            s.setName(preset.name());
            s.setDurationMinutes(preset.minutes());
            s.setPrice(BigDecimal.valueOf((long) preset.basePrice() * factor));
            s.setColor(preset.color());
            created.add(services.save(s));
        }

        // Sample data is added after the email is verified, so unverified signups stay tiny.
        w.setSampleDataPending(r.sampleData());

        boolean sent = verification.sendNew(u);
        return new RegisterResponse(true, email, jwtService.issueVerify(u), sent);
    }

    @Transactional
    public SessionResponse login(LoginRequest r) {
        String email = r.email().trim();
        long locked = loginAttempts.minutesLocked(email);
        if (locked > 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many wrong passwords. Try again in " + locked + " minute" + (locked == 1 ? "" : "s") + ", or reset your password.");
        }
        Optional<AppUser> found = users.findByEmailIgnoreCase(email);
        boolean ok = passwordEncoder.matches(r.password(), found.map(AppUser::getPasswordHash).orElse(DUMMY_HASH));
        if (found.isEmpty() || !ok) {
            loginAttempts.failed(email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "That email and password don't match.");
        }
        loginAttempts.succeeded(email);
        AppUser u = found.get();
        if (u.getWorkspace().isSuspendedNow()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is suspended. Contact support to restore it.");
        }
        if (u.isPendingVerification()) {
            verification.sendIfNoneActive(u);
            throw new ApiException(HttpStatus.FORBIDDEN, "Please confirm your email first. We've sent a code to " + u.getEmail() + ".",
                    java.util.Map.of("reason", "EMAIL_NOT_VERIFIED", "pendingToken", jwtService.issueVerify(u), "email", u.getEmail()));
        }
        return new SessionResponse(jwtService.issue(u), Mappers.user(u), Mappers.workspace(u.getWorkspace()));
    }

    /** Always answers the same way, so it can't be used to find out who has an account. */
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest r) {
        MessageResponse answer = new MessageResponse("If that email has an account, a reset link is on its way. Check your inbox and spam folder.");
        Optional<AppUser> found = users.findByEmailIgnoreCase(r.email().trim());
        if (found.isEmpty()) return answer;
        AppUser u = found.get();
        if (resetTokens.countByUserIdAndCreatedAtAfter(u.getId(), Instant.now().minus(1, ChronoUnit.HOURS)) >= 3) return answer;

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        PasswordResetToken t = new PasswordResetToken();
        t.setUser(u);
        t.setTokenHash(sha256(token));
        t.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        resetTokens.save(t);

        String link = publicUrl.replaceAll("/+$", "") + "/reset-password?token=" + token;
        mail.send(u.getEmail(), "Reset your Daysheet password",
                "Hi " + u.getFullName() + ",\n\nSomeone asked to reset the password for your Daysheet account.\n"
                        + "Use this link within 30 minutes to choose a new one:\n\n" + link + "\n\n"
                        + "If you didn't ask for this, you can ignore this email. Your password stays the same.\n\nDaysheet");
        return answer;
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest r) {
        PasswordResetToken t = resetTokens.findByTokenHash(sha256(r.token().trim()))
                .filter(found -> found.getUsedAt() == null && found.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("This reset link has expired or was already used. Ask for a new one."));
        AppUser u = t.getUser();
        u.setPasswordHash(passwordEncoder.encode(r.password()));
        // The reset link reached their inbox, so the email is genuine.
        if (u.isPendingVerification()) {
            u.setEmailVerified(true);
            u.setEmailVerifiedAt(Instant.now());
        }
        resetTokens.invalidateAll(u.getId(), Instant.now());
        loginAttempts.succeeded(u.getEmail());
        return new MessageResponse("Your password has been changed. You can log in now.");
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        AppUser u = users.findById(CurrentUser.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
        return new MeResponse(Mappers.user(u), Mappers.workspace(u.getWorkspace()));
    }

    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) base = "practice";
        if (base.length() > 60) base = base.substring(0, 60);
        String slug = base;
        int i = 2;
        while (workspaces.existsBySlug(slug)) {
            slug = base + "-" + i++;
        }
        return slug;
    }
}
