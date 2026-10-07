package com.daysheet.billing;

import com.daysheet.domain.Plan;
import com.daysheet.domain.Workspace;
import com.daysheet.repository.WorkspaceRepository;
import com.daysheet.service.MailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Expires lapsed plans every hour and sends one reminder before a trial or paid period ends. */
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionScheduler.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final WorkspaceRepository workspaces;
    private final MailService mail;

    @Value("${app.public-url}")
    private String publicUrl;

    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void expireLapsed() {
        int n = workspaces.expireLapsed(Instant.now(), Plan.TRIAL, Plan.ACTIVE, Plan.EXPIRED);
        if (n > 0) log.info("Marked {} practice(s) as expired.", n);
    }

    /** Daily at 10:00 IST. Each practice falls into the 24-hour window exactly once, so it gets one email. */
    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Kolkata")
    @Transactional(readOnly = true)
    public void remindBeforeEnd() {
        Instant now = Instant.now();
        Instant trialFrom = now.plus(Duration.ofDays(2)), trialTo = trialFrom.plus(Duration.ofDays(1));
        Instant paidFrom = now.plus(Duration.ofDays(6)), paidTo = paidFrom.plus(Duration.ofDays(1));
        workspaces.trialsEndingBetween(Plan.TRIAL, trialFrom, trialTo).forEach(w -> remind(w, w.getTrialEndsAt(), true));
        workspaces.paidEndingBetween(Plan.ACTIVE, paidFrom, paidTo).forEach(w -> remind(w, w.getSubscriptionEndsAt(), false));
    }

    private void remind(Workspace w, Instant endsAt, boolean trial) {
        if (w.getEmail() == null) return;
        ZoneId zone;
        try { zone = ZoneId.of(w.getTimezone()); } catch (Exception e) { zone = ZoneId.of("Asia/Kolkata"); }
        String date = DATE.format(endsAt.atZone(zone));
        String link = publicUrl.replaceAll("/+$", "") + "/app/settings?tab=plan";
        mail.send(w.getEmail(),
                trial ? "Your Daysheet trial ends on " + date : "Your Daysheet plan ends on " + date,
                "Hi,\n\n" + (trial ? "Your free trial for \"" + w.getName() + "\" ends on " + date + "."
                        : "Your Daysheet plan for \"" + w.getName() + "\" ends on " + date + ".")
                        + "\nTo keep taking online bookings without a break, choose a plan here:\n\n" + link + "\n\n"
                        + "Pay with UPI, card or net banking, or reply to this email to pay by bank transfer.\n"
                        + "Paying early never loses days: the new period starts when the current one ends.\n\nDaysheet");
    }
}
