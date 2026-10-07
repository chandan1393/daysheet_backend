package com.daysheet.service;

import com.daysheet.config.ApiException;
import com.daysheet.domain.*;
import com.daysheet.dto.BookingDtos.*;
import com.daysheet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The public, no-login booking page every practice gets at /book/{slug}. */
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_DAYS_AHEAD = 60;

    private final WorkspaceRepository workspaces;
    private final ServiceOfferingRepository services;
    private final WorkingHoursRepository hours;
    private final AppointmentRepository appointments;
    private final ClientRepository clients;

    @Transactional(readOnly = true)
    public PublicPractice practice(String slug) {
        Workspace w = workspace(slug);
        List<Integer> openDays = hours.findByWorkspaceIdOrderByDayOfWeekAsc(w.getId()).stream()
                .filter(WorkingHours::isEnabled).map(WorkingHours::getDayOfWeek).toList();
        return new PublicPractice(w.getName(), w.getSlug(), w.getProfession().displayName(), w.getClientLabel(),
                w.getSessionLabel(), w.getCurrency(), w.getPhone(), w.getEmail(), w.getAddress(),
                services.findByWorkspaceIdAndActiveTrueAndBookableOnlineTrueOrderByNameAsc(w.getId())
                        .stream().map(Mappers::service).toList(),
                openDays, WorkspaceService.today(w));
    }

    @Transactional(readOnly = true)
    public SlotsResponse slots(String slug, Long serviceId, LocalDate date) {
        Workspace w = workspace(slug);
        ServiceOffering s = bookableService(w, serviceId);
        return new SlotsResponse(date, freeSlots(w, s, date).stream().map(t -> t.format(HHMM)).toList());
    }

    @Transactional
    public BookingConfirmation book(String slug, BookingRequest r) {
        if (r.website() != null && !r.website().isBlank()) {
            throw ApiException.badRequest("We couldn't complete the booking. Please call the practice.");
        }
        Workspace w = workspace(slug);
        ServiceOffering s = bookableService(w, r.serviceId());
        LocalTime time = r.time().withSecond(0).withNano(0);
        if (!freeSlots(w, s, r.date()).contains(time)) {
            throw new ApiException(HttpStatus.CONFLICT, "That time was just taken. Pick another slot.");
        }

        Optional<Client> existing = r.email() == null || r.email().isBlank() ? Optional.empty()
                : clients.findFirstByWorkspaceIdAndEmailIgnoreCase(w.getId(), r.email().trim());
        Client c = existing.orElseGet(() -> {
            Client n = new Client();
            n.setWorkspace(w);
            n.setFullName(r.fullName().trim());
            n.setEmail(WorkspaceService.blankToNull(r.email()));
            n.setPhone(r.phone().trim());
            n.setTags("Online");
            return clients.save(n);
        });
        if (c.isArchived()) c.setArchived(false);
        if (c.getId() != null && appointments.countByClientIdAndSourceAndStartAtAfterAndStatusIn(c.getId(),
                AppointmentSource.ONLINE, WorkspaceService.now(w),
                List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED)) >= 3) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "You already have 3 upcoming bookings here. Contact the practice to book more.");
        }

        LocalDateTime start = r.date().atTime(time);
        Appointment a = new Appointment();
        a.setWorkspace(w);
        a.setClient(c);
        a.setService(s);
        a.setStartAt(start);
        a.setEndAt(start.plusMinutes(s.getDurationMinutes()));
        a.setPrice(s.getPrice());
        a.setSource(AppointmentSource.ONLINE);
        a.setStatus(AppointmentStatus.SCHEDULED);
        a.setNotes(WorkspaceService.blankToNull(r.notes()));
        appointments.save(a);

        return new BookingConfirmation(a.getId(), w.getName(), s.getName(), a.getStartAt(), a.getEndAt(), c.getFullName());
    }

    private List<LocalTime> freeSlots(Workspace w, ServiceOffering s, LocalDate date) {
        LocalDate today = WorkspaceService.today(w);
        if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) return List.of();

        Optional<WorkingHours> wh = hours.findByWorkspaceIdAndDayOfWeek(w.getId(), date.getDayOfWeek().getValue())
                .filter(WorkingHours::isEnabled);
        if (wh.isEmpty()) return List.of();

        LocalDateTime dayStart = date.atTime(wh.get().getStartTime());
        LocalDateTime dayEnd = date.atTime(wh.get().getEndTime());
        List<Appointment> taken = appointments.findOverlapping(w.getId(), dayStart, dayEnd, ClientService.INACTIVE, -1L);
        LocalDateTime earliest = WorkspaceService.now(w).plusMinutes(30);

        List<LocalTime> free = new ArrayList<>();
        int step = Math.max(5, w.getSlotMinutes());
        for (LocalDateTime t = dayStart; !t.plusMinutes(s.getDurationMinutes()).isAfter(dayEnd); t = t.plusMinutes(step)) {
            LocalDateTime start = t;
            LocalDateTime end = t.plusMinutes(s.getDurationMinutes());
            if (start.isBefore(earliest)) continue;
            boolean clash = taken.stream().anyMatch(a -> a.getStartAt().isBefore(end) && a.getEndAt().isAfter(start));
            if (!clash) free.add(start.toLocalTime());
        }
        return free;
    }

    private Workspace workspace(String slug) {
        Workspace w = workspaces.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Booking page"));
        if (w.isExpired(Instant.now()) || w.isSuspendedNow()) {
            throw new ApiException(HttpStatus.GONE, "Online booking is paused for this practice. Contact them directly.");
        }
        return w;
    }

    private ServiceOffering bookableService(Workspace w, Long serviceId) {
        return services.findByIdAndWorkspaceId(serviceId, w.getId())
                .filter(s -> s.isActive() && s.isBookableOnline())
                .orElseThrow(() -> ApiException.notFound("Service"));
    }
}
