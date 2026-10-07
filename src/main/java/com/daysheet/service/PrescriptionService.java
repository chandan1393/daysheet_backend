package com.daysheet.service;

import com.daysheet.config.ApiException;
import com.daysheet.domain.*;
import com.daysheet.dto.PackDtos.*;
import com.daysheet.repository.AppUserRepository;
import com.daysheet.repository.PrescriptionRepository;
import com.daysheet.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionRepository prescriptions;
    private final AppUserRepository users;
    private final ClientService clientService;

    @Transactional(readOnly = true)
    public PrescriptionDto get(Long id) { return toDto(find(id)); }

    @Transactional(readOnly = true)
    public List<PrescriptionDto> forClient(Long clientId) {
        clientService.find(clientId);
        return prescriptions.findByClientIdOrderByCreatedAtDesc(clientId).stream().map(PrescriptionService::toDto).toList();
    }

    @Transactional
    public PrescriptionDto create(PrescriptionRequest r) {
        Client c = clientService.find(r.clientId());
        Prescription p = new Prescription();
        p.setWorkspace(c.getWorkspace());
        p.setClient(c);
        p.setAuthorName(users.findById(CurrentUser.userId()).map(AppUser::getFullName).orElse(null));
        apply(p, c, r);
        return toDto(prescriptions.save(p));
    }

    @Transactional
    public PrescriptionDto update(Long id, PrescriptionRequest r) {
        Prescription p = find(id);
        apply(p, p.getClient(), r);
        return toDto(p);
    }

    @Transactional
    public void delete(Long id) { prescriptions.delete(find(id)); }

    @Transactional(readOnly = true)
    public List<String> medicines(String q) {
        String prefix = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return prescriptions.medicineSuggestions(CurrentUser.workspaceId(), prefix, PageRequest.of(0, 12));
    }

    private void apply(Prescription p, Client c, PrescriptionRequest r) {
        p.setAppointment(clientService.visitOf(c, r.appointmentId()));
        p.setVitals(trim(r.vitals()));
        p.setComplaints(trim(r.complaints()));
        p.setDiagnosis(trim(r.diagnosis()));
        p.setAdvice(trim(r.advice()));
        p.setTests(trim(r.tests()));
        p.setFollowUpDate(r.followUpDate());
        List<RxItem> items = r.items() == null ? List.of() : r.items();
        if (items.isEmpty() && p.getDiagnosis() == null && p.getAdvice() == null) {
            throw ApiException.badRequest("Add at least one medicine, a diagnosis or advice.");
        }
        p.replaceItems(items.stream().map(i -> {
            PrescriptionItem it = new PrescriptionItem();
            it.setMedicine(i.medicine().trim());
            it.setDose(trim(i.dose()));
            it.setFrequency(trim(i.frequency()));
            it.setTiming(trim(i.timing()));
            it.setDuration(trim(i.duration()));
            it.setNotes(trim(i.notes()));
            return it;
        }).toList());
    }

    private Prescription find(Long id) {
        return prescriptions.findByIdAndWorkspaceId(id, CurrentUser.workspaceId())
                .orElseThrow(() -> ApiException.notFound("Prescription"));
    }

    static PrescriptionDto toDto(Prescription p) {
        return new PrescriptionDto(p.getId(), p.getClient().getId(), p.getClient().getFullName(),
                p.getAppointment() != null ? p.getAppointment().getId() : null, p.getVitals(), p.getComplaints(),
                p.getDiagnosis(), p.getAdvice(), p.getTests(), p.getFollowUpDate(),
                p.getItems().stream().map(i -> new RxItem(i.getMedicine(), i.getDose(), i.getFrequency(), i.getTiming(),
                        i.getDuration(), i.getNotes())).toList(),
                p.getAuthorName(), p.getCreatedAt());
    }

    private static String trim(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
