package com.daysheet.web;

import com.daysheet.domain.PricePlan;
import com.daysheet.dto.AdminDtos.*;
import com.daysheet.dto.BillingDtos.PlanUpdate;
import com.daysheet.dto.BillingDtos.TaxInvoiceDto;
import com.daysheet.service.AdminAccountService;
import com.daysheet.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The admin panel's API. Every route needs an admin login (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService admin;
    private final AdminAccountService accounts;

    @GetMapping("/overview")
    public Overview overview() { return admin.overview(); }

    // ---------- Practices
    @GetMapping("/practices")
    public List<PracticeRow> practices(@RequestParam(required = false) String q, @RequestParam(required = false) String status) {
        return admin.practices(q, status);
    }

    @GetMapping("/practices/{id}")
    public PracticeDetail practice(@PathVariable Long id) { return admin.practice(id); }

    @PostMapping("/practices/{id}/extend")
    public PracticeDetail extend(@PathVariable Long id, @Valid @RequestBody ExtendRequest r) { return admin.extend(id, r); }

    @PostMapping("/practices/{id}/payments")
    public PracticeDetail recordPayment(@PathVariable Long id, @Valid @RequestBody ManualPaymentRequest r) { return admin.recordPayment(id, r); }

    @PostMapping("/practices/{id}/suspend")
    public PracticeDetail suspend(@PathVariable Long id, @Valid @RequestBody SuspendRequest r) { return admin.suspend(id, r); }

    // ---------- Payments and GST
    @GetMapping("/payments")
    public List<PaymentRow> payments(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return admin.payments(from, to);
    }

    @GetMapping("/payments/{id}/invoice")
    public TaxInvoiceDto invoice(@PathVariable Long id) { return admin.invoice(id); }

    /** Every paid invoice between two dates as CSV, for GSTR-1. */
    @GetMapping(value = "/gst-report", produces = "text/csv")
    public ResponseEntity<String> gstReport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        StringBuilder csv = new StringBuilder(
                "Invoice number,Invoice date,Type,Customer name,Customer GSTIN,Place of supply,State code,SAC,Taxable value,GST rate,CGST,SGST,IGST,Invoice total,Payment method,Payment reference\n");
        for (TaxInvoiceDto i : admin.invoicesBetween(from, to)) {
            csv.append(cell(i.invoiceNumber())).append(',').append(i.invoiceDate()).append(',')
                    .append(i.buyer().gstin() != null ? "B2B" : "B2C").append(',')
                    .append(cell(i.buyer().name())).append(',').append(cell(i.buyer().gstin())).append(',')
                    .append(cell(i.buyer().stateName())).append(',').append(cell(i.buyer().stateCode())).append(',')
                    .append(cell(i.sac())).append(',').append(money(i.taxablePaise())).append(',').append(i.gstRate()).append(',')
                    .append(money(i.cgstPaise())).append(',').append(money(i.sgstPaise())).append(',').append(money(i.igstPaise())).append(',')
                    .append(money(i.totalPaise())).append(',').append(cell(i.paymentMethod())).append(',')
                    .append(cell(i.razorpayPaymentId())).append('\n');
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"gst-" + from + "-to-" + to + ".csv\"")
                .body(csv.toString());
    }

    // ---------- Prices
    @GetMapping("/plans")
    public List<PricePlan> plans() { return admin.plans(); }

    @PutMapping("/plans/{code}")
    public PricePlan savePlan(@PathVariable String code, @Valid @RequestBody PlanUpdate r) { return admin.savePlan(code, r); }

    // ---------- Company and GST
    @GetMapping("/company")
    public CompanyDto company() { return admin.company(); }

    @PutMapping("/company")
    public CompanyDto saveCompany(@Valid @RequestBody CompanyDto r) { return admin.saveCompany(r); }

    // ---------- Admins and activity
    @GetMapping("/admins")
    public List<AdminDto> admins() { return accounts.list(); }

    @PostMapping("/admins")
    public AdminDto createAdmin(@Valid @RequestBody CreateAdminRequest r) { return accounts.create(r); }

    @PostMapping("/admins/{id}/active")
    public AdminDto setActive(@PathVariable Long id, @RequestBody ActiveRequest r) { return accounts.setActive(id, r.active()); }

    @GetMapping("/activity")
    public List<AuditRow> activity() { return accounts.activity(); }

    private static String money(long paise) {
        return BigDecimal.valueOf(paise).movePointLeft(2).toPlainString();
    }

    /** Quotes a CSV cell and stops spreadsheet formula injection. */
    private static String cell(String v) {
        if (v == null) return "";
        String s = v.replace("\"", "\"\"");
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s + "\"";
    }
}
