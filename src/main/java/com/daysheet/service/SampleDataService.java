package com.daysheet.service;

import com.daysheet.domain.*;
import com.daysheet.repository.AppointmentRepository;
import com.daysheet.repository.ClientNoteRepository;
import com.daysheet.repository.ClientRepository;
import com.daysheet.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fills a new workspace with believable demo data so the first login
 * shows a living practice instead of empty screens.
 */
@Service
@RequiredArgsConstructor
public class SampleDataService {

    private static final String[][] PEOPLE = {
            {"Aarav Mehta", "+91 98200 11234", "aarav.mehta@example.com", "Regular"},
            {"Emma Lawson", "+44 7700 900123", "emma.lawson@example.com", ""},
            {"Rohan Iyer", "+91 99300 45678", "rohan.iyer@example.com", "Insurance"},
            {"Maya Fernandes", "+91 98111 22334", "maya.f@example.com", "Regular"},
            {"Daniel Osei", "+44 7700 900456", "daniel.osei@example.com", ""},
            {"Priya Nair", "+91 97400 55667", "priya.nair@example.com", "VIP"},
            {"Lucas Brennan", "+1 415 555 0142", "lucas.b@example.com", ""},
            {"Sara Kapoor", "+91 98765 43210", "sara.kapoor@example.com", "New"},
            {"Noah Williams", "+1 212 555 0187", "noah.w@example.com", ""},
            {"Ananya Rao", "+91 90000 12121", "ananya.rao@example.com", "Regular"},
    };

    /** Neutral enough for any profession; attached to recent past visits so the history looks lived-in. */
    private static final String[] VISIT_NOTES = {
            "Reviewed progress since the last visit. Next steps agreed.",
            "Went through the plan for the coming weeks. Follow up in 15 days.",
            "Shared documents to review before the next session.",
            "Good improvement. Keep the current plan and check again in two weeks.",
            "Discussed concerns raised last time. Added details to the record."
    };

    private final ClientRepository clients;
    private final ClientNoteRepository notes;
    private final AppointmentRepository appointments;
    private final InvoiceRepository invoices;
    private final com.daysheet.repository.PrescriptionRepository prescriptions;
    private final com.daysheet.repository.PackageTemplateRepository packageTemplates;
    private final com.daysheet.repository.ClientPackageRepository clientPackages;
    private final com.daysheet.repository.LegalCaseRepository legalCases;
    private final com.daysheet.repository.DeadlineRepository deadlines;

    public void seed(Workspace w, List<ServiceOffering> services, String authorName) {
        if (services.isEmpty()) return;
        Random rnd = new Random(42);
        LocalDate today = WorkspaceService.today(w);
        LocalDateTime now = WorkspaceService.now(w);

        List<Client> people = new ArrayList<>();
        for (int i = 0; i < PEOPLE.length; i++) {
            String[] p = PEOPLE[i];
            Client c = new Client();
            c.setWorkspace(w);
            c.setFullName(p[0]);
            c.setPhone(p[1]);
            c.setEmail(p[2]);
            c.setTags(p[3].isBlank() ? null : p[3]);
            c.setCreatedAt(Instant.now().minus(Duration.ofDays(160L - i * 14L)));
            people.add(clients.save(c));
        }

        addNote(w, people.get(0), authorName, "Prefers morning slots. WhatsApp reminders work best.");
        addNote(w, people.get(0), authorName, "Good progress since last visit. Review again in two weeks.");
        addNote(w, people.get(2), authorName, "Asked for a receipt with full details for reimbursement.");
        addNote(w, people.get(5), authorName, "Referred by a friend. Wants a plan for the next three months.");

        int invoiceNo = 1;
        Appointment lastSeenFirstClient = null;
        int[] dayStartHours = {9, 10, 11, 12, 14, 15, 16, 17};

        for (int offset = -150; offset <= 12; offset++) {
            LocalDate day = today.plusDays(offset);
            if (day.getDayOfWeek() == DayOfWeek.SUNDAY) continue;

            List<LocalTime> times = new ArrayList<>();
            if (offset == 0) {
                times = List.of(LocalTime.of(9, 30), LocalTime.of(11, 0), LocalTime.of(14, 0), LocalTime.of(16, 30));
            } else {
                int count = day.getDayOfWeek() == DayOfWeek.SATURDAY ? rnd.nextInt(2) : 1 + rnd.nextInt(3);
                if (offset < -90) count = Math.max(0, count - 1); // practice grew over time
                for (int k = 0; k < count; k++) {
                    LocalTime t = LocalTime.of(dayStartHours[rnd.nextInt(dayStartHours.length)], rnd.nextBoolean() ? 0 : 30);
                    if (!times.contains(t)) times.add(t);
                }
            }

            for (int k = 0; k < times.size(); k++) {
                Client c = offset == 0 ? people.get(k) : people.get(rnd.nextInt(people.size()));
                ServiceOffering s = services.get(rnd.nextInt(services.size()));
                LocalDateTime start = day.atTime(times.get(k));

                Appointment a = new Appointment();
                a.setWorkspace(w);
                a.setClient(c);
                a.setService(s);
                a.setStartAt(start);
                a.setEndAt(start.plusMinutes(s.getDurationMinutes()));
                a.setPrice(s.getPrice());
                a.setSource(rnd.nextInt(4) == 0 ? AppointmentSource.ONLINE : AppointmentSource.MANUAL);

                boolean past = a.getEndAt().isBefore(now);
                if (past) {
                    int roll = rnd.nextInt(20);
                    a.setStatus(roll == 0 ? AppointmentStatus.NO_SHOW
                            : roll == 1 ? AppointmentStatus.CANCELLED : AppointmentStatus.COMPLETED);
                } else {
                    a.setStatus(rnd.nextBoolean() ? AppointmentStatus.CONFIRMED : AppointmentStatus.SCHEDULED);
                }
                appointments.save(a);
                if (c == people.get(0) && a.getStatus() == AppointmentStatus.COMPLETED) lastSeenFirstClient = a;

                if (a.getStatus() == AppointmentStatus.COMPLETED && offset > -60 && rnd.nextInt(2) == 0) {
                    ClientNote vn = new ClientNote();
                    vn.setWorkspace(w);
                    vn.setClient(c);
                    vn.setAppointment(a);
                    vn.setAuthorName(authorName);
                    vn.setBody(VISIT_NOTES[rnd.nextInt(VISIT_NOTES.length)]);
                    vn.setCreatedAt(a.getEndAt().atZone(WorkspaceService.zone(w)).toInstant());
                    notes.save(vn);
                }

                if (a.getStatus() == AppointmentStatus.COMPLETED && s.getPrice().signum() > 0) {
                    Invoice inv = new Invoice();
                    inv.setWorkspace(w);
                    inv.setClient(c);
                    inv.setAppointment(a);
                    inv.setNumber(String.format("INV-%04d", invoiceNo++));
                    inv.setIssueDate(day);
                    inv.setDueDate(day.plusDays(7));
                    inv.replaceItems(List.of(new InvoiceItem(s.getName(), BigDecimal.ONE, s.getPrice())));
                    boolean unpaid = offset > -12 && rnd.nextInt(3) == 0;
                    if (unpaid) {
                        inv.setStatus(InvoiceStatus.SENT);
                    } else {
                        inv.setStatus(InvoiceStatus.PAID);
                        LocalDate paid = day.plusDays(rnd.nextInt(3));
                        inv.setPaidDate(paid.isAfter(today) ? today : paid);
                    }
                    invoices.save(inv);
                }
            }
        }
        seedPack(w, people, services, lastSeenFirstClient, today, authorName);
    }

    /** A little data for the profession's pack, so a demo shows it straight away. */
    private void seedPack(Workspace w, List<Client> people, List<ServiceOffering> services, Appointment lastVisit,
                          LocalDate today, String author) {
        switch (w.getProfession()) {
            case DOCTOR, DENTIST -> {
                Prescription p = new Prescription();
                p.setWorkspace(w);
                p.setClient(people.get(0));
                p.setAppointment(lastVisit);
                p.setAuthorName(author);
                p.setVitals("BP 128/84, Pulse 78, Temp 98.6 F, Weight 72 kg");
                p.setComplaints("Fever and sore throat for 3 days");
                p.setDiagnosis("Acute pharyngitis");
                p.setAdvice("Warm salt-water gargles three times a day. Plenty of fluids. Rest.");
                p.setFollowUpDate(today.plusDays(5));
                p.replaceItems(List.of(item("Paracetamol 650 mg", "1 tablet", "1-0-1", "After food", "5 days"),
                        item("Azithromycin 500 mg", "1 tablet", "1-0-0", "Before food", "3 days"),
                        item("Cetirizine 10 mg", "1 tablet", "0-0-1", "After food", "5 days")));
                prescriptions.save(p);
            }
            case LAWYER -> {
                legalCase(w, people.get(0), "Property dispute, Bandra flat", "CS/1423/2025", "City Civil Court, Mumbai", "M/s Shree Builders",
                        today.minusDays(24), "Framing of issues", "Issues framed; evidence next.", today.plusDays(1), "Plaintiff's evidence");
                legalCase(w, people.get(2), "Cheque bounce complaint", "CC/88/2026", "Metropolitan Magistrate, Andheri", "R. Kulkarni",
                        today.minusDays(10), "First hearing", "Summons issued to accused.", today.plusDays(6), "Appearance of accused");
                legalCase(w, people.get(5), "Divorce by mutual consent", "PA/312/2026", "Family Court, Bandra", null,
                        today.minusDays(40), "First motion", "First motion allowed.", today.plusDays(19), "Second motion");
            }
            case ACCOUNTANT -> {
                LocalDate next20 = today.withDayOfMonth(Math.min(20, today.lengthOfMonth()));
                if (next20.isBefore(today)) next20 = next20.plusMonths(1);
                for (int i = 0; i < 5; i++) {
                    deadline(w, people.get(i), "GSTR-3B", "GST", next20, "MONTHLY");
                }
                deadline(w, people.get(1), "TDS payment", "TDS", today.minusDays(2), "MONTHLY");
                deadline(w, people.get(3), "ITR filing", "ITR", today.plusDays(9), "YEARLY");
                deadline(w, people.get(6), "Advance tax instalment", "ADVANCE_TAX", today.plusDays(2), "QUARTERLY");
            }
            default -> {
                PackageTemplate t = new PackageTemplate();
                t.setWorkspace(w);
                t.setName(services.get(0).getName() + ", pack of 10");
                t.setSessions(10);
                t.setPrice(services.get(0).getPrice().multiply(java.math.BigDecimal.valueOf(9)));
                t.setValidityDays(120);
                packageTemplates.save(t);
                ClientPackage cp = new ClientPackage();
                cp.setWorkspace(w);
                cp.setClient(people.get(0));
                cp.setTemplate(t);
                cp.setName(t.getName());
                cp.setTotalSessions(10);
                cp.setUsedSessions(4);
                cp.setPrice(t.getPrice());
                cp.setPurchasedOn(today.minusDays(30));
                cp.setExpiresOn(today.plusDays(90));
                clientPackages.save(cp);
            }
        }
    }

    private static PrescriptionItem item(String medicine, String dose, String frequency, String timing, String duration) {
        PrescriptionItem i = new PrescriptionItem();
        i.setMedicine(medicine);
        i.setDose(dose);
        i.setFrequency(frequency);
        i.setTiming(timing);
        i.setDuration(duration);
        return i;
    }

    private void legalCase(Workspace w, Client c, String title, String number, String court, String opposite,
                           LocalDate lastDate, String lastPurpose, String outcome, LocalDate nextDate, String nextPurpose) {
        LegalCase lc = new LegalCase();
        lc.setWorkspace(w);
        lc.setClient(c);
        lc.setTitle(title);
        lc.setCaseNumber(number);
        lc.setCourt(court);
        lc.setOppositeParty(opposite);
        Hearing past = new Hearing();
        past.setLegalCase(lc);
        past.setHearingDate(lastDate);
        past.setPurpose(lastPurpose);
        past.setOutcome(outcome);
        Hearing next = new Hearing();
        next.setLegalCase(lc);
        next.setHearingDate(nextDate);
        next.setPurpose(nextPurpose);
        lc.getHearings().add(past);
        lc.getHearings().add(next);
        legalCases.save(lc);
    }

    private void deadline(Workspace w, Client c, String title, String category, LocalDate due, String recurrence) {
        Deadline d = new Deadline();
        d.setWorkspace(w);
        d.setClient(c);
        d.setTitle(title);
        d.setCategory(category);
        d.setDueDate(due);
        d.setRecurrence(recurrence);
        deadlines.save(d);
    }

    private void addNote(Workspace w, Client c, String author, String body) {
        ClientNote n = new ClientNote();
        n.setWorkspace(w);
        n.setClient(c);
        n.setAuthorName(author);
        n.setBody(body);
        notes.save(n);
    }
}
