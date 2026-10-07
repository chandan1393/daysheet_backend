package com.daysheet.web;

import com.daysheet.dto.AuthDtos.WorkspaceDto;
import com.daysheet.dto.BillingDtos.*;
import com.daysheet.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billing;

    @GetMapping("/plans")
    public List<PlanDto> plans() { return billing.activePlans(); }

    @PostMapping("/orders")
    public CheckoutDto createOrder(@Valid @RequestBody OrderRequest request) { return billing.createOrder(request); }

    @PostMapping("/verify")
    public WorkspaceDto verify(@Valid @RequestBody VerifyRequest request) { return billing.verify(request); }

    @GetMapping("/payments")
    public List<PaymentDto> payments() { return billing.history(); }

    @GetMapping("/payments/{id}/invoice")
    public TaxInvoiceDto invoice(@PathVariable Long id) { return billing.invoice(id); }

    @GetMapping("/details")
    public BillingDetailsDto details() { return billing.billingDetails(); }

    @PutMapping("/details")
    public BillingDetailsDto saveDetails(@Valid @RequestBody BillingDetailsDto request) { return billing.saveBillingDetails(request); }
}
