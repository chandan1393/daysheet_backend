package com.daysheet.service;

import com.daysheet.config.ApiException;
import com.daysheet.domain.AppUser;
import com.daysheet.domain.EmailVerification;
import com.daysheet.domain.ServiceOffering;
import com.daysheet.domain.Workspace;
import com.daysheet.dto.AuthDtos.SessionResponse;
import com.daysheet.repository.AppUserRepository;
import com.daysheet.repository.EmailVerificationRepository;
import com.daysheet.repository.ServiceOfferingRepository;
import com.daysheet.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/** Sends verification emails and checks codes and links. Nobody gets a login session until their email is confirmed. */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;

    private final EmailVerificationRepository verifications;
    private final AppUserRepository users;
    private final ServiceOfferingRepository services;
    private final SampleDataService sampleData;
    private final MailService mail;
    private final JwtService jwt;

    @Value("${app.public-url}") private String publicUrl;
    @Value("${app.trial-days}") private int trialDays;

    /** Creates a fresh code and link (older ones stop working) and emails them. */
    @Transactional
    public boolean sendNew(AppUser u) {
        Instant now = Instant.now();
        verifications.invalidateAll(u.getId(), now);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        EmailVerification v = new EmailVerification();
        v.setUser(u);
        v.setCodeHash(codeHash(u.getId(), code));
        v.setTokenHash(sha256(token));
        v.setCodeExpiresAt(now.plus(30, ChronoUnit.MINUTES));
        v.setLinkExpiresAt(now.plus(24, ChronoUnit.HOURS));
        verifications.save(v);

        String link = publicUrl.replaceAll("/+$", "") + "/verify-email?token=" + token;
        String spaced = code.substring(0, 3) + " " + code.substring(3);
        String first = u.getFullName().split("\\s+")[0];
        String text = "Hi " + first + ",\n\nYour Daysheet verification code is: " + spaced + "\n\n"
                + "Or confirm your email with this link (valid for 24 hours):\n" + link + "\n\n"
                + "The code works for 30 minutes. If you didn't sign up for Daysheet, ignore this email.\n\nDaysheet";
        return mail.send(u.getEmail(), spaced + " is your Daysheet code", text, html(first, spaced, link));
    }

    /** Sends a code only if there isn't a live one (used when an unverified user tries to log in). */
    @Transactional
    public void sendIfNoneActive(AppUser u) {
        boolean live = verifications.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(u.getId())
                .filter(v -> v.getCodeExpiresAt().isAfter(Instant.now()) && v.getAttempts() < MAX_ATTEMPTS)
                .isPresent();
        if (!live) sendNew(u);
    }

    @Transactional
    public void resend(String pendingToken) {
        AppUser u = fromPending(pendingToken);
        Instant now = Instant.now();
        verifications.findFirstByUserIdOrderByCreatedAtDesc(u.getId()).ifPresent(last -> {
            long wait = 60 - Duration.between(last.getCreatedAt(), now).getSeconds();
            if (wait > 0) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Please wait " + wait + " seconds before asking for another code.");
        });
        if (verifications.countByUserIdAndCreatedAtAfter(u.getId(), now.minus(1, ChronoUnit.HOURS)) >= 5) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many codes requested. Try again in an hour, or check your spam folder.");
        }
        if (!sendNew(u)) throw new ApiException(HttpStatus.BAD_GATEWAY, "We couldn't send the email right now. Try again in a minute.");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SessionResponse verifyCode(String pendingToken, String code) {
        AppUser u = fromPending(pendingToken);
        String clean = code == null ? "" : code.replaceAll("\\D", "");
        EmailVerification v = verifications.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(u.getId())
                .orElseThrow(() -> ApiException.badRequest("That code has expired. Ask for a new one."));
        if (v.getCodeExpiresAt().isBefore(Instant.now())) throw ApiException.badRequest("That code has expired. Ask for a new one.");
        if (v.getAttempts() >= MAX_ATTEMPTS) throw ApiException.badRequest("Too many wrong tries. Ask for a new code.");
        if (!MessageDigest.isEqual(v.getCodeHash().getBytes(StandardCharsets.UTF_8), codeHash(u.getId(), clean).getBytes(StandardCharsets.UTF_8))) {
            v.setAttempts(v.getAttempts() + 1);
            int left = MAX_ATTEMPTS - v.getAttempts();
            throw ApiException.badRequest(left > 0 ? "That code isn't right. " + left + " " + (left == 1 ? "try" : "tries") + " left."
                    : "Too many wrong tries. Ask for a new code.");
        }
        return complete(u);
    }

    @Transactional
    public SessionResponse verifyLink(String token) {
        EmailVerification v = verifications.findByTokenHash(sha256(token == null ? "" : token.trim()))
                .filter(found -> found.getUsedAt() == null && found.getLinkExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("This link has expired or was already used. Log in to get a new code."));
        AppUser u = v.getUser();
        if (!u.isPendingVerification()) throw ApiException.badRequest("This email is already verified. Log in instead.");
        return complete(u);
    }

    /** Marks the email verified, starts the trial from today, adds sample data if asked, and logs the user in. */
    private SessionResponse complete(AppUser u) {
        Instant now = Instant.now();
        u.setEmailVerified(true);
        u.setEmailVerifiedAt(now);
        verifications.invalidateAll(u.getId(), now);
        Workspace w = u.getWorkspace();
        w.setTrialEndsAt(now.plus(trialDays, ChronoUnit.DAYS));
        if (Boolean.TRUE.equals(w.getSampleDataPending())) {
            List<ServiceOffering> list = services.findByWorkspaceIdAndActiveTrueOrderByNameAsc(w.getId());
            sampleData.seed(w, list, u.getFullName());
            w.setSampleDataPending(false);
        }
        return new SessionResponse(jwt.issue(u), Mappers.user(u), Mappers.workspace(w));
    }

    AppUser fromPending(String pendingToken) {
        Long id = pendingToken == null ? null : jwt.parseVerify(pendingToken);
        AppUser u = id == null ? null : users.findById(id).orElse(null);
        if (u == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "This verification session has expired. Log in again to get a new code.");
        if (!u.isPendingVerification()) throw ApiException.badRequest("This email is already verified. Log in instead.");
        return u;
    }

    private static String codeHash(Long userId, String code) { return sha256(userId + ":" + code); }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Simple, table-based HTML that renders well in Gmail, Outlook and phone mail apps. */
    private static String html(String firstName, String code, String link) {
        return """
                <!doctype html><html><body style="margin:0;background:#F3F5F9;font-family:Segoe UI,Helvetica,Arial,sans-serif;color:#14233B">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#F3F5F9;padding:32px 12px">
                <tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:18px;overflow:hidden">
                  <tr><td style="background:#10213A;padding:20px 28px;color:#ffffff;font-size:20px;font-weight:800">Daysheet</td></tr>
                  <tr><td style="padding:28px">
                    <p style="margin:0 0 6px;font-size:22px;font-weight:800;color:#10213A">Confirm your email</p>
                    <p style="margin:0 0 22px;font-size:15px;color:#5B6B82">Hi %s, enter this code to finish setting up your practice.</p>
                    <div style="background:#E2F3F3;border-radius:14px;padding:18px;text-align:center;font-size:34px;font-weight:800;letter-spacing:8px;color:#0A5F67">%s</div>
                    <p style="margin:22px 0 12px;font-size:14px;color:#5B6B82;text-align:center">or</p>
                    <p style="text-align:center;margin:0 0 22px"><a href="%s" style="display:inline-block;background:#0E7C86;color:#ffffff;text-decoration:none;font-weight:700;padding:13px 26px;border-radius:12px">Verify my email</a></p>
                    <p style="margin:0;font-size:13px;color:#8A97AA">The code works for 30 minutes and the button for 24 hours. If you didn't sign up for Daysheet, you can ignore this email.</p>
                  </td></tr>
                </table>
                </td></tr></table></body></html>
                """.formatted(esc(firstName), esc(code), esc(link));
    }
}
