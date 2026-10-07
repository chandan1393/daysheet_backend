package com.daysheet.service;

import com.daysheet.config.ApiException;
import com.daysheet.domain.*;
import com.daysheet.dto.AppointmentDtos.*;
import com.daysheet.repository.AppointmentRepository;
import com.daysheet.repository.ClientDocumentRepository;
import com.daysheet.repository.ClientNoteRepository;
import com.daysheet.repository.ClientRepository;
import com.daysheet.repository.InvoiceRepository;
import com.daysheet.dto.ClientDtos.NoteDto;
import com.daysheet.dto.DocumentDtos.DocumentDto;
import org.springframework.data.domain.PageRequest;
import com.daysheet.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointments;
    private final ClientRepository clients;
    private final InvoiceRepository invoices;
    private final ClientNoteRepository notes;
    private final ClientDocumentRepository documents;
    private final com.daysheet.repository.PrescriptionRepository prescriptions;
    private final PackageService packageService;
    private final ClientService clientService;
    private final ServiceOfferingService serviceOfferings;
    private final WorkspaceService workspaceService;

    @Transactional(readOnly = true)
    public List<AppointmentDto> list(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw ApiException.badRequest("The end date must be after the start date.");
        if (from.plusDays(62).isBefore(to)) throw ApiException.badRequest("Load at most two months at a time.");
        return toDtos(appointments.findInRange(CurrentUser.workspaceId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
    }

    @Transactional
    public AppointmentDto create(AppointmentRequest r) {
        Workspace w = workspaceService.current();
        Appointment a = new Appointment();
        a.setWorkspace(w);
        a.setClient(resolveClient(w, r));
        apply(a, r);
        appointments.save(a);
        return Mappers.appointment(a, false);
    }

    @Transactional
    public AppointmentDto update(Long id, AppointmentRequest r) {
        Workspace w = workspaceService.current();
        Appointment a = find(id);
        a.setClient(resolveClient(w, r));
        apply(a, r);
        return Mappers.appointment(a, isInvoiced(a));
    }

    @Transactional
    public AppointmentDto changeStatus(Long id, AppointmentStatus status) {
        Appointment a = find(id);
        AppointmentStatus before = a.getStatus();
        a.setStatus(status);
        packageService.onStatusChange(a, before, status);
        return Mappers.appointment(a, isInvoiced(a));
    }

    @Transactional
    public void delete(Long id) {
        Appointment a = find(id);
        if (isInvoiced(a)) {
            throw ApiException.badRequest("This appointment has an invoice. Cancel it instead of deleting.");
        }
        // Notes and files stay on the person's record as general entries.
        notes.unlinkAppointment(a.getId());
        documents.unlinkAppointment(a.getId());
        prescriptions.unlinkAppointment(a.getId());
        packageService.release(a);
        appointments.delete(a);
    }

    /** This visit's notes and files, its visit number, and what happened at the visit before. */
    @Transactional(readOnly = true)
    public VisitRecord record(Long id) {
        Appointment a = find(id);
        Long clientId = a.getClient().getId();
        PreviousVisit previous = appointments
                .findEarlier(clientId, a.getStartAt(), ClientService.INACTIVE, PageRequest.of(0, 1))
                .stream().findFirst()
                .map(p -> new PreviousVisit(Mappers.appointment(p, isInvoiced(p)), notesOf(p), documentsOf(p), prescriptionsOf(p)))
                .orElse(null);
        int visitNumber = (int) appointments.countVisitsUpTo(clientId, a.getStartAt(), ClientService.INACTIVE);
        return new VisitRecord(a.getId(), Math.max(1, visitNumber), notesOf(a), documentsOf(a), previous, prescriptionsOf(a));
    }

    private List<com.daysheet.dto.PackDtos.PrescriptionDto> prescriptionsOf(Appointment a) {
        return prescriptions.findByAppointmentIdOrderByCreatedAtAsc(a.getId()).stream().map(PrescriptionService::toDto).toList();
    }

    private List<NoteDto> notesOf(Appointment a) {
        return notes.findByAppointmentIdOrderByCreatedAtAsc(a.getId()).stream().map(Mappers::note).toList();
    }

    private List<DocumentDto> documentsOf(Appointment a) {
        return documents.findByAppointmentIdOrderByCreatedAtAsc(a.getId()).stream().map(Mappers::document).toList();
    }

    Appointment find(Long id) {
        return appointments.findByIdAndWorkspaceId(id, CurrentUser.workspaceId())
                .orElseThrow(() -> ApiException.notFound("Appointment"));
    }

    List<AppointmentDto> toDtos(List<Appointment> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> invoiced = new HashSet<>(invoices.findInvoicedAppointmentIds(list.stream().map(Appointment::getId).toList()));
        return list.stream().map(a -> Mappers.appointment(a, invoiced.contains(a.getId()))).toList();
    }

    private boolean isInvoiced(Appointment a) {
        return invoices.existsByAppointmentIdAndStatusNot(a.getId(), InvoiceStatus.VOID);
    }

    private Client resolveClient(Workspace w, AppointmentRequest r) {
        if (r.clientId() != null) return clientService.find(r.clientId());
        NewClient nc = r.newClient();
        if (nc == null || nc.fullName() == null || nc.fullName().isBlank()) {
            throw ApiException.badRequest("Choose who this appointment is for.");
        }
        Client c = new Client();
        c.setWorkspace(w);
        c.setFullName(nc.fullName().trim());
        c.setPhone(WorkspaceService.blankToNull(nc.phone()));
        c.setEmail(WorkspaceService.blankToNull(nc.email()));
        return clients.save(c);
    }

    private void apply(Appointment a, AppointmentRequest r) {
        ServiceOffering s = r.serviceId() != null ? serviceOfferings.find(r.serviceId()) : null;
        int minutes = r.durationMinutes() != null ? r.durationMinutes() : s != null ? s.getDurationMinutes() : 30;
        LocalDateTime start = r.startAt().withSecond(0).withNano(0);
        LocalDateTime end = start.plusMinutes(minutes);

        if (!r.force()) {
            List<Appointment> clashes = appointments.findOverlapping(a.getWorkspace().getId(), start, end,
                    ClientService.INACTIVE, a.getId() != null ? a.getId() : -1L);
            if (!clashes.isEmpty()) {
                Appointment c = clashes.get(0);
                throw new ApiException(HttpStatus.CONFLICT, "This overlaps with " + c.getClient().getFullName()
                        + " at " + c.getStartAt().format(TIME) + ".");
            }
        }

        a.setService(s);
        a.setStartAt(start);
        a.setEndAt(end);
        a.setPrice(r.price() != null ? r.price() : s != null ? s.getPrice() : null);
        a.setNotes(WorkspaceService.blankToNull(r.notes()));
    }
}
