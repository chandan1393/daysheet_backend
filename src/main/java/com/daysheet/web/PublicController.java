package com.daysheet.web;

import com.daysheet.dto.AuthDtos.ProfessionDto;
import com.daysheet.dto.BookingDtos.*;
import com.daysheet.billing.GstSettings;
import com.daysheet.billing.GstStates;
import com.daysheet.dto.BillingDtos.BusinessInfo;
import com.daysheet.dto.BillingDtos.PlanDto;
import com.daysheet.dto.BillingDtos.StateDto;
import com.daysheet.service.AuthService;
import com.daysheet.service.BillingService;
import com.daysheet.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Endpoints that work without logging in: signup options and the booking page. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final AuthService auth;
    private final BookingService booking;
    private final BillingService billing;
    private final GstSettings gst;


    /** States and union territories for the billing form. */
    @GetMapping("/gst-states")
    public List<StateDto> gstStates() {
        return GstStates.BY_CODE.entrySet().stream().map(e -> new StateDto(e.getKey(), e.getValue())).toList();
    }

    /** Business details for the Contact, Privacy and Terms pages, from the same settings as the invoices. */
    @GetMapping("/business")
    public BusinessInfo business() {
        return new BusinessInfo(gst.businessName(), gst.businessAddress(), gst.businessPhone(), gst.supportEmail(),
                gst.gstin(), gst.jurisdictionCity(), gst.grievanceOfficer());
    }

    /** For uptime monitors. */
    @GetMapping("/health")
    public java.util.Map<String, String> health() { return java.util.Map.of("status", "ok"); }

    /** Prices for the landing page, from the database, in INR. */
    @GetMapping("/plans")
    public List<PlanDto> plans() { return billing.activePlans(); }

    @GetMapping("/professions")
    public List<ProfessionDto> professions() { return auth.professions(); }

    @GetMapping("/book/{slug}")
    public PublicPractice practice(@PathVariable String slug) { return booking.practice(slug); }

    @GetMapping("/book/{slug}/slots")
    public SlotsResponse slots(@PathVariable String slug,
                               @RequestParam Long serviceId,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return booking.slots(slug, serviceId, date);
    }

    @PostMapping("/book/{slug}")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingConfirmation book(@PathVariable String slug, @Valid @RequestBody BookingRequest request) {
        return booking.book(slug, request);
    }
}
