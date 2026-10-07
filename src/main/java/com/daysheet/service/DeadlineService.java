package com.daysheet.service;

import com.daysheet.config.ApiException;
import com.daysheet.domain.Client;
import com.daysheet.domain.Deadline;
import com.daysheet.dto.PackDtos.*;
import com.daysheet.repository.DeadlineRepository;
import com.daysheet.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Compliance deadlines per client (GST, TDS, ITR...). Recurring ones create the next one when marked done. */
@Service
@RequiredArgsConstructor
public class DeadlineService {

    private final DeadlineRepository deadlines;
    private final ClientService clientService;
    private final WorkspaceService workspaceService;

    @Transactional(readOnly = true)
    public List<DeadlineDto> list() {
        LocalDate today = today();
        return deadlines.findAllForWorkspace(CurrentUser.workspaceId()).stream().map(d -> toDto(d, today)).toList();
    }

    @Transactional(readOnly = true)
    public List<DeadlineDto> forClient(Long clientId) {
        clientService.find(clientId);
        LocalDate today = today();
        return deadlines.findByClientIdOrderByDueDateDesc(clientId).stream().map(d -> toDto(d, today)).toList();
    }

    @Transactional
    public DeadlineDto save(Long id, DeadlineRequest r) {
        Deadline d = id == null ? new Deadline() : find(id);
        Client c = clientService.find(r.clientId());
        if (id == null) d.setWorkspace(c.getWorkspace());
        d.setClient(c);
        d.setTitle(r.title().trim());
        d.setCategory(r.category() == null ? "OTHER" : r.category());
        d.setPeriod(trim(r.period()));
        d.setDueDate(r.dueDate());
        d.setRecurrence(r.recurrence() == null ? "NONE" : r.recurrence());
        d.setNotes(trim(r.notes()));
        return toDto(deadlines.save(d), today());
    }

    /** Adds the same task for many clients at once, e.g. GSTR-3B for every GST client. */
    @Transactional
    public List<DeadlineDto> bulk(BulkDeadlineRequest r) {
        LocalDate today = today();
        List<DeadlineDto> created = new ArrayList<>();
        for (Long clientId : r.clientIds().stream().distinct().toList()) {
            Client c = clientService.find(clientId);
            Deadline d = new Deadline();
            d.setWorkspace(c.getWorkspace());
            d.setClient(c);
            d.setTitle(r.title().trim());
            d.setCategory(r.category() == null ? "OTHER" : r.category());
            d.setPeriod(trim(r.period()));
            d.setDueDate(r.dueDate());
            d.setRecurrence(r.recurrence() == null ? "NONE" : r.recurrence());
            created.add(toDto(deadlines.save(d), today));
        }
        return created;
    }

    /** Marks done; for recurring tasks, creates the next one. Returns the next one if it was created. */
    @Transactional
    public DeadlineDto markDone(Long id, boolean done) {
        Deadline d = find(id);
        LocalDate today = today();
        if (!done) {
            d.setStatus("PENDING");
            d.setDoneOn(null);
            return toDto(d, today);
        }
        if ("DONE".equals(d.getStatus())) return toDto(d, today);
        d.setStatus("DONE");
        d.setDoneOn(today);
        LocalDate nextDue = switch (d.getRecurrence()) {
            case "MONTHLY" -> d.getDueDate().plusMonths(1);
            case "QUARTERLY" -> d.getDueDate().plusMonths(3);
            case "YEARLY" -> d.getDueDate().plusYears(1);
            default -> null;
        };
        if (nextDue == null) return toDto(d, today);
        Deadline next = new Deadline();
        next.setWorkspace(d.getWorkspace());
        next.setClient(d.getClient());
        next.setTitle(d.getTitle());
        next.setCategory(d.getCategory());
        next.setDueDate(nextDue);
        next.setRecurrence(d.getRecurrence());
        next.setNotes(d.getNotes());
        return toDto(deadlines.save(next), today);
    }

    @Transactional
    public void delete(Long id) { deadlines.delete(find(id)); }

    @Transactional(readOnly = true)
    public List<DeadlineDto> dueWithin(int days) {
        LocalDate today = today();
        return deadlines.pendingDueBy(CurrentUser.workspaceId(), today.plusDays(days)).stream().map(d -> toDto(d, today)).toList();
    }

    private LocalDate today() { return WorkspaceService.today(workspaceService.current()); }

    private Deadline find(Long id) {
        return deadlines.findByIdAndWorkspaceId(id, CurrentUser.workspaceId()).orElseThrow(() -> ApiException.notFound("Deadline"));
    }

    static DeadlineDto toDto(Deadline d, LocalDate today) {
        return new DeadlineDto(d.getId(), d.getClient().getId(), d.getClient().getFullName(), d.getTitle(), d.getCategory(),
                d.getPeriod(), d.getDueDate(), d.getStatus(), d.getDoneOn(), d.getRecurrence(), d.getNotes(),
                "PENDING".equals(d.getStatus()) && d.getDueDate().isBefore(today));
    }

    private static String trim(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
