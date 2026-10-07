package com.daysheet.service;

import com.daysheet.domain.*;
import com.daysheet.dto.AppointmentDtos.AppointmentDto;
import com.daysheet.dto.AuthDtos.UserDto;
import com.daysheet.dto.AuthDtos.WorkspaceDto;
import com.daysheet.dto.ClientDtos.NoteDto;
import com.daysheet.dto.DocumentDtos.DocumentDto;
import com.daysheet.dto.InvoiceDtos.InvoiceSummary;
import com.daysheet.dto.ServiceDtos.ServiceDto;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

final class Mappers {
    private Mappers() {}

    static UserDto user(AppUser u) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name());
    }

    static WorkspaceDto workspace(Workspace w) {
        Instant now = Instant.now();
        boolean expired = w.isExpired(now);
        Plan plan = expired ? Plan.EXPIRED : w.getPlan();
        return new WorkspaceDto(w.getId(), w.getName(), w.getSlug(), w.getProfession().name(),
                w.getProfession().displayName(), w.getClientLabel(), w.getClientLabelPlural(),
                w.getSessionLabel(), w.getCurrency(), w.getTimezone(), w.getPhone(), w.getEmail(),
                w.getAddress(), w.getSlotMinutes(), plan.name(), w.getTrialEndsAt(), daysUntil(w.getTrialEndsAt(), now),
                w.getSubscriptionEndsAt(), expired, expired ? 0 : daysUntil(w.accessEndsAt(), now),
                w.modules().stream().map(Enum::name).toList(), w.getPractitionerTitle(), w.getRegistrationNumber());
    }

    private static long daysUntil(Instant end, Instant now) {
        if (end == null) return 0;
        long hours = Duration.between(now, end).toHours();
        return Math.max(0, (long) Math.ceil(hours / 24.0));
    }

    static ServiceDto service(ServiceOffering s) {
        return new ServiceDto(s.getId(), s.getName(), s.getDescription(), s.getDurationMinutes(),
                s.getPrice(), s.getColor(), s.isBookableOnline());
    }

    static AppointmentDto appointment(Appointment a, boolean invoiced) {
        ServiceOffering s = a.getService();
        Client c = a.getClient();
        int minutes = (int) Duration.between(a.getStartAt(), a.getEndAt()).toMinutes();
        return new AppointmentDto(a.getId(), c.getId(), c.getFullName(), c.getPhone(),
                s != null ? s.getId() : null,
                s != null ? s.getName() : "Appointment",
                s != null ? s.getColor() : "#64748B",
                a.getStartAt(), a.getEndAt(), minutes, a.getStatus().name(), a.getSource().name(),
                a.getPrice(), a.getNotes(), invoiced,
                a.getClientPackage() != null ? a.getClientPackage().getName() : null,
                a.getClientPackage() != null ? a.getClientPackage().getUsedSessions() : 0,
                a.getClientPackage() != null ? a.getClientPackage().getTotalSessions() : 0);
    }

    static NoteDto note(ClientNote n) {
        return new NoteDto(n.getId(), n.getBody(), n.getAuthorName(), n.getCreatedAt(),
                n.getAppointment() != null ? n.getAppointment().getId() : null);
    }

    static DocumentDto document(ClientDocument d) {
        return new DocumentDto(d.getId(), d.getFileName(), d.getContentType(), d.getSizeBytes(),
                d.getAppointment() != null ? d.getAppointment().getId() : null, d.getUploadedBy(), d.getCreatedAt());
    }

    static InvoiceSummary invoiceSummary(Invoice i, LocalDate today) {
        return new InvoiceSummary(i.getId(), i.getNumber(), i.getClient().getId(), i.getClient().getFullName(),
                i.getIssueDate(), i.getDueDate(), i.getStatus().name(), i.getTotal(), isOverdue(i, today));
    }

    static boolean isOverdue(Invoice i, LocalDate today) {
        return i.getStatus() == InvoiceStatus.SENT && i.getDueDate() != null && i.getDueDate().isBefore(today);
    }
}
