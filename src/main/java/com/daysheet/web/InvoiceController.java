package com.daysheet.web;

import com.daysheet.dto.InvoiceDtos.*;
import com.daysheet.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoices;

    @GetMapping
    public List<InvoiceSummary> list() { return invoices.list(); }

    @GetMapping("/{id}")
    public InvoiceDetail get(@PathVariable Long id) { return invoices.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDetail create(@Valid @RequestBody InvoiceRequest request) { return invoices.create(request); }

    @PostMapping("/from-appointment/{appointmentId}")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDetail fromAppointment(@PathVariable Long appointmentId) {
        return invoices.fromAppointment(appointmentId);
    }

    @PutMapping("/{id}")
    public InvoiceDetail update(@PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
        return invoices.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public InvoiceDetail status(@PathVariable Long id, @Valid @RequestBody InvoiceStatusRequest request) {
        return invoices.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { invoices.delete(id); }
}
