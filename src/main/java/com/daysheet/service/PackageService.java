package com.daysheet.service;

import com.daysheet.config.ApiException;
import com.daysheet.domain.*;
import com.daysheet.dto.PackDtos.*;
import com.daysheet.repository.*;
import com.daysheet.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Session packages: what a practice sells, what each person bought, and sessions used by completed visits. */
@Service
@RequiredArgsConstructor
public class PackageService {

    private final PackageTemplateRepository templates;
    private final ClientPackageRepository packages;
    private final ServiceOfferingRepository services;
    private final InvoiceRepository invoices;
    private final ClientService clientService;
    private final WorkspaceService workspaceService;

    // ---------- Templates
    @Transactional(readOnly = true)
    public List<PackageTemplateDto> templates() {
        return templates.findByWorkspaceIdAndActiveTrueOrderByNameAsc(CurrentUser.workspaceId()).stream()
                .map(PackageService::toDto).toList();
    }

    @Transactional
    public PackageTemplateDto saveTemplate(Long id, PackageTemplateRequest r) {
        PackageTemplate t = id == null ? new PackageTemplate() : findTemplate(id);
        if (id == null) t.setWorkspace(workspaceService.current());
        t.setName(r.name().trim());
        t.setSessions(r.sessions());
        t.setPrice(r.price());
        t.setValidityDays(r.validityDays());
        t.setService(r.serviceId() == null ? null : services.findByIdAndWorkspaceId(r.serviceId(), CurrentUser.workspaceId())
                .orElseThrow(() -> ApiException.notFound("Service")));
        return toDto(templates.save(t));
    }

    @Transactional
    public void deleteTemplate(Long id) { findTemplate(id).setActive(false); }

    // ---------- Client packages
    @Transactional(readOnly = true)
    public List<ClientPackageDto> forClient(Long clientId) {
        clientService.find(clientId);
        LocalDate today = WorkspaceService.today(workspaceService.current());
        return packages.findByClientIdOrderByPurchasedOnDescIdDesc(clientId).stream().map(p -> toDto(p, today)).toList();
    }

    @Transactional
    public ClientPackageDto sell(Long clientId, SellPackageRequest r) {
        Client c = clientService.find(clientId);
        PackageTemplate t = findTemplate(r.templateId());
        Workspace w = c.getWorkspace();
        LocalDate today = WorkspaceService.today(w);
        LocalDate bought = r.purchasedOn() != null ? r.purchasedOn() : today;

        ClientPackage p = new ClientPackage();
        p.setWorkspace(w);
        p.setClient(c);
        p.setTemplate(t);
        p.setService(t.getService());
        p.setName(t.getName());
        p.setTotalSessions(t.getSessions());
        p.setPrice(t.getPrice());
        p.setPurchasedOn(bought);
        p.setExpiresOn(t.getValidityDays() == null ? null : bought.plusDays(t.getValidityDays()));

        if (r.createInvoice() && t.getPrice().signum() > 0) {
            Invoice inv = new Invoice();
            inv.setWorkspace(w);
            inv.setClient(c);
            inv.setNumber(String.format("INV-%04d", invoices.countByWorkspaceId(w.getId()) + 1));
            inv.setIssueDate(today);
            inv.setDueDate(r.markPaid() ? today : today.plusDays(7));
            inv.setStatus(r.markPaid() ? InvoiceStatus.PAID : InvoiceStatus.SENT);
            inv.setPaidDate(r.markPaid() ? today : null);
            inv.replaceItems(List.of(new InvoiceItem(t.getName() + " (" + t.getSessions() + " sessions)", BigDecimal.ONE, t.getPrice())));
            invoices.save(inv);
            p.setInvoiceId(inv.getId());
        }
        return toDto(packages.save(p), today);
    }

    /** Correct the count by hand, e.g. a session taken before the package was recorded. */
    @Transactional
    public ClientPackageDto adjust(Long id, int delta) {
        ClientPackage p = findPackage(id);
        int used = Math.max(0, Math.min(p.getTotalSessions(), p.getUsedSessions() + delta));
        p.setUsedSessions(used);
        return toDto(p, WorkspaceService.today(p.getWorkspace()));
    }

    @Transactional
    public void delete(Long id) {
        ClientPackage p = findPackage(id);
        if (p.getUsedSessions() > 0) throw ApiException.badRequest("Sessions have been used from this package, so it can't be deleted.");
        packages.delete(p);
    }

    /**
     * Called when an appointment's status changes. Completing a visit uses one session from the person's
     * soonest-expiring usable package (matching the service, if the package is for one service).
     * Moving it away from "completed" gives the session back.
     */
    @Transactional
    public void onStatusChange(Appointment a, AppointmentStatus before, AppointmentStatus after) {
        if (after == AppointmentStatus.COMPLETED && before != AppointmentStatus.COMPLETED && a.getClientPackage() == null) {
            LocalDate today = WorkspaceService.today(a.getWorkspace());
            packages.findByClientIdOrderByPurchasedOnDescIdDesc(a.getClient().getId()).stream()
                    .filter(p -> p.usable(today))
                    .filter(p -> p.getService() == null || (a.getService() != null && p.getService().getId().equals(a.getService().getId())))
                    .min(Comparator.comparing((ClientPackage p) -> p.getExpiresOn() == null ? LocalDate.MAX : p.getExpiresOn())
                            .thenComparing(ClientPackage::getPurchasedOn))
                    .ifPresent(p -> {
                        p.setUsedSessions(p.getUsedSessions() + 1);
                        a.setClientPackage(p);
                    });
        } else if (before == AppointmentStatus.COMPLETED && after != AppointmentStatus.COMPLETED) {
            release(a);
        }
    }

    /** Gives a session back (status changed or appointment deleted). */
    @Transactional
    public void release(Appointment a) {
        ClientPackage p = a.getClientPackage();
        if (p == null) return;
        p.setUsedSessions(Math.max(0, p.getUsedSessions() - 1));
        a.setClientPackage(null);
    }

    private PackageTemplate findTemplate(Long id) {
        return templates.findByIdAndWorkspaceId(id, CurrentUser.workspaceId())
                .filter(PackageTemplate::isActive).orElseThrow(() -> ApiException.notFound("Package"));
    }

    private ClientPackage findPackage(Long id) {
        return packages.findByIdAndWorkspaceId(id, CurrentUser.workspaceId()).orElseThrow(() -> ApiException.notFound("Package"));
    }

    static PackageTemplateDto toDto(PackageTemplate t) {
        return new PackageTemplateDto(t.getId(), t.getName(), t.getSessions(), t.getPrice(), t.getValidityDays(),
                t.getService() != null ? t.getService().getId() : null, t.getService() != null ? t.getService().getName() : null);
    }

    static ClientPackageDto toDto(ClientPackage p, LocalDate today) {
        return new ClientPackageDto(p.getId(), p.getName(), p.getTotalSessions(), p.getUsedSessions(), p.getPrice(),
                p.getPurchasedOn(), p.getExpiresOn(), p.status(today), p.getService() != null ? p.getService().getName() : null,
                p.getInvoiceId());
    }
}
