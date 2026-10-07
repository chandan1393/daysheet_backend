package com.daysheet.billing;

import com.daysheet.domain.Deadline;
import com.daysheet.domain.Hearing;
import com.daysheet.domain.PackModule;
import com.daysheet.domain.Workspace;
import com.daysheet.repository.DeadlineRepository;
import com.daysheet.repository.HearingRepository;
import com.daysheet.repository.WorkspaceRepository;
import com.daysheet.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Every evening at 7 pm IST: tomorrow's hearings (lawyers) and deadlines due soon or overdue (CAs). */
@Component
@RequiredArgsConstructor
public class PackDigestScheduler {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final WorkspaceRepository workspaces;
    private final HearingRepository hearings;
    private final DeadlineRepository deadlines;
    private final MailService mail;

    @Value("${app.public-url}")
    private String publicUrl;

    @Scheduled(cron = "0 0 19 * * *", zone = "Asia/Kolkata")
    @Transactional(readOnly = true)
    public void sendDigests() {
        Instant now = Instant.now();
        for (Workspace w : workspaces.findAll()) {
            if (w.getEmail() == null || w.isExpired(now) || w.isSuspendedNow()) continue;
            boolean cases = w.hasModule(PackModule.CASES), due = w.hasModule(PackModule.DEADLINES);
            if (!cases && !due) continue;
            LocalDate today = LocalDate.now(zone(w));
            List<Hearing> tomorrow = cases ? hearings.upcoming(w.getId(), today.plusDays(1), today.plusDays(1)) : List.of();
            List<Deadline> soon = due ? deadlines.pendingDueBy(w.getId(), today.plusDays(3)) : List.of();
            if (tomorrow.isEmpty() && soon.isEmpty()) continue;

            StringBuilder b = new StringBuilder("Hi,\n\nHere's what needs your attention.\n");
            if (!tomorrow.isEmpty()) {
                b.append("\nHEARINGS TOMORROW (").append(DAY.format(today.plusDays(1))).append(")\n");
                for (Hearing h : tomorrow) {
                    var c = h.getLegalCase();
                    b.append("- ").append(c.getTitle());
                    if (c.getCaseNumber() != null) b.append(", ").append(c.getCaseNumber());
                    if (c.getCourt() != null) b.append(", ").append(c.getCourt());
                    b.append(" (").append(c.getClient().getFullName()).append(")");
                    if (h.getPurpose() != null) b.append(": ").append(h.getPurpose());
                    b.append("\n");
                }
            }
            if (!soon.isEmpty()) {
                b.append("\nDEADLINES\n");
                for (Deadline d : soon) {
                    b.append("- ").append(d.getDueDate().isBefore(today) ? "OVERDUE since " : "Due ").append(DAY.format(d.getDueDate()))
                            .append(": ").append(d.getTitle());
                    if (d.getPeriod() != null) b.append(" (").append(d.getPeriod()).append(")");
                    b.append(" for ").append(d.getClient().getFullName()).append("\n");
                }
            }
            b.append("\nOpen Daysheet: ").append(publicUrl.replaceAll("/+$", "")).append("/app\n\nDaysheet");
            String subject = !tomorrow.isEmpty() && !soon.isEmpty()
                    ? tomorrow.size() + " hearing(s) tomorrow and " + soon.size() + " deadline(s) due"
                    : !tomorrow.isEmpty() ? tomorrow.size() + " hearing(s) tomorrow" : soon.size() + " deadline(s) need attention";
            mail.send(w.getEmail(), subject, b.toString());
        }
    }

    private static ZoneId zone(Workspace w) {
        try { return ZoneId.of(w.getTimezone()); } catch (Exception e) { return ZoneId.of("Asia/Kolkata"); }
    }
}
