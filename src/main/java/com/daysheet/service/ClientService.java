package com.daysheet.service;

import com.daysheet.config.ApiException;
import com.daysheet.domain.*;
import com.daysheet.dto.ClientDtos.*;
import com.daysheet.repository.*;
import com.daysheet.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ClientService {

    static final List<AppointmentStatus> INACTIVE = List.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW);

    private final ClientRepository clients;
    private final ClientNoteRepository notes;
    private final ClientDocumentRepository documents;
    private final PrescriptionRepository prescriptions;
    private final ClientPackageRepository clientPackages;
    private final LegalCaseRepository legalCases;
    private final DeadlineRepository deadlines;
    private final AppointmentRepository appointments;
    private final InvoiceRepository invoices;
    private final AppUserRepository users;
    private final WorkspaceService workspaceService;

    @Transactional(readOnly = true)
    public List<ClientSummary> list(String query) {
        Workspace w = workspaceService.current();
        LocalDateTime now = WorkspaceService.now(w);
        String q = query == null ? "" : query.trim().toLowerCase();

        Map<Long, Object[]> past = new HashMap<>();
        appointments.pastVisitStats(w.getId(), now, INACTIVE).forEach(r -> past.put((Long) r[0], r));
        Map<Long, LocalDateTime> next = new HashMap<>();
        appointments.nextVisits(w.getId(), now, INACTIVE).forEach(r -> next.put((Long) r[0], (LocalDateTime) r[1]));

        return clients.search(w.getId(), q).stream().map(c -> {
            Object[] p = past.get(c.getId());
            return new ClientSummary(c.getId(), c.getFullName(), c.getEmail(), c.getPhone(), c.getTags(),
                    p == null ? 0 : ((Number) p[1]).longValue(),
                    p == null ? null : (LocalDateTime) p[2],
                    next.get(c.getId()), c.getCreatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public ClientDetail get(Long id) {
        Workspace w = workspaceService.current();
        Client c = find(id);
        LocalDate today = WorkspaceService.today(w);
        LocalDateTime now = WorkspaceService.now(w);

        List<Appointment> appts = appointments.findByClient(c.getId());
        Set<Long> invoiced = appts.isEmpty() ? Set.of()
                : new HashSet<>(invoices.findInvoicedAppointmentIds(appts.stream().map(Appointment::getId).toList()));
        long visits = appts.stream()
                .filter(a -> a.getStartAt().isBefore(now) && !INACTIVE.contains(a.getStatus()))
                .count();

        return new ClientDetail(c.getId(), c.getFullName(), c.getEmail(), c.getPhone(), c.getDateOfBirth(),
                c.getTags(), c.getCreatedAt(), invoices.sumPaidForClient(c.getId()), visits,
                notes.findByClientIdOrderByCreatedAtDesc(c.getId()).stream().map(Mappers::note).toList(),
                documents.findByClientIdOrderByCreatedAtDesc(c.getId()).stream().map(Mappers::document).toList(),
                appts.stream().map(a -> Mappers.appointment(a, invoiced.contains(a.getId()))).toList(),
                invoices.findByClient(c.getId()).stream().map(i -> Mappers.invoiceSummary(i, today)).toList(),
                prescriptions.findByClientIdOrderByCreatedAtDesc(c.getId()).stream().map(PrescriptionService::toDto).toList(),
                clientPackages.findByClientIdOrderByPurchasedOnDescIdDesc(c.getId()).stream().map(p -> PackageService.toDto(p, today)).toList(),
                legalCases.findByClientIdOrderByCreatedAtDesc(c.getId()).stream().map(x -> CaseService.toDto(x, today, false)).toList(),
                deadlines.findByClientIdOrderByDueDateDesc(c.getId()).stream().map(d -> DeadlineService.toDto(d, today)).toList());
    }

    @Transactional
    public ClientSummary create(ClientRequest r) {
        Client c = new Client();
        c.setWorkspace(workspaceService.current());
        apply(c, r);
        clients.save(c);
        return new ClientSummary(c.getId(), c.getFullName(), c.getEmail(), c.getPhone(), c.getTags(),
                0, null, null, c.getCreatedAt());
    }

    @Transactional
    public ClientDetail update(Long id, ClientRequest r) {
        apply(find(id), r);
        return get(id);
    }

    @Transactional
    public void archive(Long id) {
        find(id).setArchived(true);
    }

    @Transactional
    public NoteDto addNote(Long clientId, NoteRequest r) {
        Client c = find(clientId);
        String author = users.findById(CurrentUser.userId()).map(AppUser::getFullName).orElse(null);
        ClientNote n = new ClientNote();
        n.setWorkspace(c.getWorkspace());
        n.setClient(c);
        n.setBody(r.body().trim());
        n.setAuthorName(author);
        n.setAppointment(visitOf(c, r.appointmentId()));
        return Mappers.note(notes.save(n));
    }

    @Transactional
    public void deleteNote(Long clientId, Long noteId) {
        ClientNote n = notes.findByIdAndWorkspaceId(noteId, CurrentUser.workspaceId())
                .filter(found -> found.getClient().getId().equals(clientId))
                .orElseThrow(() -> ApiException.notFound("Note"));
        notes.delete(n);
    }

    /** The appointment a note or file belongs to, checked to be this person's. Null means "general". */
    Appointment visitOf(Client c, Long appointmentId) {
        if (appointmentId == null) return null;
        Appointment a = appointments.findByIdAndWorkspaceId(appointmentId, CurrentUser.workspaceId())
                .orElseThrow(() -> ApiException.notFound("Appointment"));
        if (!a.getClient().getId().equals(c.getId())) {
            throw ApiException.badRequest("That appointment belongs to someone else.");
        }
        return a;
    }

    Client find(Long id) {
        return clients.findByIdAndWorkspaceId(id, CurrentUser.workspaceId())
                .orElseThrow(() -> ApiException.notFound("Client"));
    }

    private void apply(Client c, ClientRequest r) {
        c.setFullName(r.fullName().trim());
        c.setEmail(WorkspaceService.blankToNull(r.email()));
        c.setPhone(WorkspaceService.blankToNull(r.phone()));
        c.setDateOfBirth(r.dateOfBirth());
        c.setTags(normalizeTags(r.tags()));
    }

    private static String normalizeTags(String tags) {
        if (tags == null || tags.isBlank()) return null;
        return String.join(",", Arrays.stream(tags.split(","))
                .map(String::trim).filter(t -> !t.isEmpty()).distinct().toList());
    }
}
