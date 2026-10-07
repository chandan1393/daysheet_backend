package com.daysheet.service;

import com.daysheet.config.ApiException;
import com.daysheet.domain.*;
import com.daysheet.dto.InvoiceDtos.*;
import com.daysheet.repository.InvoiceRepository;
import com.daysheet.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final ClientService clientService;
    private final AppointmentService appointmentService;
    private final WorkspaceService workspaceService;

    @Transactional(readOnly = true)
    public List<InvoiceSummary> list() {
        Workspace w = workspaceService.current();
        LocalDate today = WorkspaceService.today(w);
        return invoices.findAllForWorkspace(w.getId()).stream().map(i -> Mappers.invoiceSummary(i, today)).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceDetail get(Long id) {
        return detail(find(id));
    }

    @Transactional
    public InvoiceDetail create(InvoiceRequest r) {
        Workspace w = workspaceService.current();
        Invoice i = new Invoice();
        i.setWorkspace(w);
        i.setNumber(nextNumber(w));
        apply(i, r, w);
        invoices.save(i);
        return detail(i);
    }

    @Transactional
    public InvoiceDetail update(Long id, InvoiceRequest r) {
        Invoice i = find(id);
        if (i.getStatus() == InvoiceStatus.PAID || i.getStatus() == InvoiceStatus.VOID) {
            throw ApiException.badRequest("Paid and void invoices can't be edited.");
        }
        apply(i, r, i.getWorkspace());
        return detail(i);
    }

    @Transactional
    public InvoiceDetail changeStatus(Long id, InvoiceStatus status) {
        Invoice i = find(id);
        i.setStatus(status);
        i.setPaidDate(status == InvoiceStatus.PAID ? WorkspaceService.today(i.getWorkspace()) : null);
        return detail(i);
    }

    /** One click: bill a finished appointment at its price. */
    @Transactional
    public InvoiceDetail fromAppointment(Long appointmentId) {
        Appointment a = appointmentService.find(appointmentId);
        if (invoices.existsByAppointmentIdAndStatusNot(a.getId(), InvoiceStatus.VOID)) {
            throw ApiException.badRequest("This appointment already has an invoice.");
        }
        Workspace w = a.getWorkspace();
        LocalDate today = WorkspaceService.today(w);
        Invoice i = new Invoice();
        i.setWorkspace(w);
        i.setClient(a.getClient());
        i.setAppointment(a);
        i.setNumber(nextNumber(w));
        i.setIssueDate(today);
        i.setDueDate(today.plusDays(7));
        i.setStatus(InvoiceStatus.SENT);
        String label = (a.getService() != null ? a.getService().getName() : w.getSessionLabel())
                + " on " + a.getStartAt().toLocalDate();
        BigDecimal price = a.getPrice() != null ? a.getPrice() : BigDecimal.ZERO;
        i.replaceItems(List.of(new InvoiceItem(label, BigDecimal.ONE, price)));
        invoices.save(i);
        return detail(i);
    }

    @Transactional
    public void delete(Long id) {
        Invoice i = find(id);
        if (i.getStatus() != InvoiceStatus.DRAFT) {
            throw ApiException.badRequest("Only drafts can be deleted. Mark this invoice as void instead.");
        }
        invoices.delete(i);
    }

    private Invoice find(Long id) {
        return invoices.findByIdAndWorkspaceId(id, CurrentUser.workspaceId())
                .orElseThrow(() -> ApiException.notFound("Invoice"));
    }

    private String nextNumber(Workspace w) {
        return String.format("INV-%04d", invoices.countByWorkspaceId(w.getId()) + 1);
    }

    private void apply(Invoice i, InvoiceRequest r, Workspace w) {
        LocalDate today = WorkspaceService.today(w);
        i.setClient(clientService.find(r.clientId()));
        i.setAppointment(r.appointmentId() != null ? appointmentService.find(r.appointmentId()) : null);
        i.setIssueDate(r.issueDate() != null ? r.issueDate() : today);
        i.setDueDate(r.dueDate());
        i.setTaxRate(r.taxRate() != null ? r.taxRate() : BigDecimal.ZERO);
        i.setNotes(WorkspaceService.blankToNull(r.notes()));
        if (r.status() != null) {
            i.setStatus(r.status());
            i.setPaidDate(r.status() == InvoiceStatus.PAID ? today : null);
        }
        i.replaceItems(r.items().stream()
                .map(it -> new InvoiceItem(it.description().trim(), it.quantity(), it.unitPrice()))
                .toList());
    }

    private InvoiceDetail detail(Invoice i) {
        Workspace w = i.getWorkspace();
        Client c = i.getClient();
        BigDecimal tax = i.getTotal().subtract(i.getSubtotal());
        return new InvoiceDetail(i.getId(), i.getNumber(),
                new PartyDto(c.getId(), c.getFullName(), c.getEmail(), c.getPhone(), null),
                new PartyDto(w.getId(), w.getName(), w.getEmail(), w.getPhone(), w.getAddress()),
                w.getCurrency(),
                i.getAppointment() != null ? i.getAppointment().getId() : null,
                i.getIssueDate(), i.getDueDate(), i.getStatus().name(),
                i.getItems().stream().map(it -> new InvoiceItemDto(it.getDescription(), it.getQuantity(), it.getUnitPrice())).toList(),
                i.getSubtotal(), i.getTaxRate(), tax, i.getTotal(), i.getPaidDate(), i.getNotes(),
                Mappers.isOverdue(i, WorkspaceService.today(w)));
    }
}
