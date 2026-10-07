package com.daysheet.service;

import com.daysheet.config.ApiException;
import com.daysheet.domain.*;
import com.daysheet.dto.PackDtos.*;
import com.daysheet.repository.HearingRepository;
import com.daysheet.repository.LegalCaseRepository;
import com.daysheet.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Cases and hearings for lawyers. */
@Service
@RequiredArgsConstructor
public class CaseService {

    private final LegalCaseRepository cases;
    private final HearingRepository hearings;
    private final ClientService clientService;
    private final WorkspaceService workspaceService;

    @Transactional(readOnly = true)
    public List<CaseDto> list(String status, String q) {
        LocalDate today = WorkspaceService.today(workspaceService.current());
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return cases.findAllForWorkspace(CurrentUser.workspaceId()).stream()
                .filter(c -> status == null || status.isBlank() || status.equalsIgnoreCase("ALL") || c.getStatus().equalsIgnoreCase(status))
                .filter(c -> query.isEmpty() || has(c.getTitle(), query) || has(c.getCaseNumber(), query) || has(c.getCourt(), query)
                        || has(c.getClient().getFullName(), query) || has(c.getOppositeParty(), query))
                .map(c -> toDto(c, today, false))
                .sorted(Comparator.comparing(CaseDto::nextHearing, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CaseDto get(Long id) { return toDto(find(id), today(), true); }

    @Transactional
    public CaseDto save(Long id, CaseRequest r) {
        LegalCase c = id == null ? new LegalCase() : find(id);
        Client client = clientService.find(r.clientId());
        if (id == null) c.setWorkspace(client.getWorkspace());
        c.setClient(client);
        c.setTitle(r.title().trim());
        c.setCaseNumber(trim(r.caseNumber()));
        c.setCourt(trim(r.court()));
        c.setOppositeParty(trim(r.oppositeParty()));
        c.setCaseType(trim(r.caseType()));
        c.setStatus(r.status() == null ? "OPEN" : r.status());
        c.setNotes(trim(r.notes()));
        return toDto(cases.save(c), today(), true);
    }

    @Transactional
    public void delete(Long id) { cases.delete(find(id)); }

    @Transactional
    public CaseDto addHearing(Long caseId, HearingRequest r) {
        LegalCase c = find(caseId);
        Hearing h = new Hearing();
        h.setLegalCase(c);
        apply(h, r);
        hearings.save(h);
        c.getHearings().add(h);
        return toDto(c, today(), true);
    }

    @Transactional
    public CaseDto updateHearing(Long hearingId, HearingRequest r) {
        Hearing h = findHearing(hearingId);
        apply(h, r);
        return toDto(h.getLegalCase(), today(), true);
    }

    @Transactional
    public CaseDto deleteHearing(Long hearingId) {
        Hearing h = findHearing(hearingId);
        LegalCase c = h.getLegalCase();
        c.getHearings().remove(h);
        hearings.delete(h);
        return toDto(c, today(), true);
    }

    @Transactional(readOnly = true)
    public List<HearingDto> upcoming(int days) {
        LocalDate today = today();
        return hearings.upcoming(CurrentUser.workspaceId(), today, today.plusDays(days)).stream().map(CaseService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CaseDto> forClient(Long clientId) {
        clientService.find(clientId);
        LocalDate today = today();
        return cases.findByClientIdOrderByCreatedAtDesc(clientId).stream().map(c -> toDto(c, today, false)).toList();
    }

    private void apply(Hearing h, HearingRequest r) {
        h.setHearingDate(r.hearingDate());
        h.setPurpose(trim(r.purpose()));
        h.setOutcome(trim(r.outcome()));
    }

    private LocalDate today() { return WorkspaceService.today(workspaceService.current()); }

    private LegalCase find(Long id) {
        return cases.findByIdAndWorkspaceId(id, CurrentUser.workspaceId()).orElseThrow(() -> ApiException.notFound("Case"));
    }

    private Hearing findHearing(Long id) {
        return hearings.findForWorkspace(id, CurrentUser.workspaceId()).orElseThrow(() -> ApiException.notFound("Hearing"));
    }

    static CaseDto toDto(LegalCase c, LocalDate today, boolean withHearings) {
        LocalDate next = c.getHearings().stream().map(Hearing::getHearingDate).filter(d -> !d.isBefore(today))
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDate last = c.getHearings().stream().map(Hearing::getHearingDate).filter(d -> d.isBefore(today))
                .max(Comparator.naturalOrder()).orElse(null);
        return new CaseDto(c.getId(), c.getClient().getId(), c.getClient().getFullName(), c.getTitle(), c.getCaseNumber(),
                c.getCourt(), c.getOppositeParty(), c.getCaseType(), c.getStatus(), c.getNotes(), next, last,
                c.getHearings().size(),
                withHearings ? c.getHearings().stream().filter(Objects::nonNull).map(CaseService::toDto).toList() : List.of(),
                c.getCreatedAt());
    }

    static HearingDto toDto(Hearing h) {
        LegalCase c = h.getLegalCase();
        return new HearingDto(h.getId(), c.getId(), c.getTitle(), c.getCaseNumber(), c.getCourt(), c.getClient().getId(),
                c.getClient().getFullName(), h.getHearingDate(), h.getPurpose(), h.getOutcome());
    }

    private static boolean has(String v, String q) { return v != null && v.toLowerCase(Locale.ROOT).contains(q); }

    private static String trim(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
