package com.daysheet.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class BillingDtos {
    private BillingDtos() {}

    /** amountPaise is the list price; totalPaise is what the customer pays (same when prices include GST). */
    public record PlanDto(String code, String name, String description, long amountPaise, int durationDays,
                          String intervalLabel, String badge, List<String> features, boolean highlighted,
                          long totalPaise, boolean taxIncluded, int gstRate) {}

    public record BillingDetailsDto(
            @NotBlank(message = "Enter the name to put on the invoice.") @Size(max = 120) String name,
            @NotBlank(message = "Enter the billing address.") @Size(max = 300) String address,
            @NotBlank(message = "Choose your state.") @Pattern(regexp = "\\d{2}", message = "Choose your state.") String stateCode,
            @Size(max = 15) String gstin) {}

    public record StateDto(String code, String name) {}

    /** Public business details. Deliberately has no legal name: only the trade name is published. */
    public record BusinessInfo(String name, String address, String phone, String email, String gstin,
                               String jurisdictionCity, String grievanceOfficer) {}

    public record Party(String name, String address, String gstin, String stateCode, String stateName) {}

    /** Everything a GST tax invoice must show (CGST Rules, rule 46). */
    public record TaxInvoiceDto(String invoiceNumber, java.time.LocalDate invoiceDate, boolean gstRegistered,
                                Party seller, Party buyer, String placeOfSupply, String sac, String description,
                                Instant periodStart, Instant periodEnd, long taxablePaise, int gstRate,
                                long cgstPaise, long sgstPaise, long igstPaise, long totalPaise,
                                String razorpayPaymentId, String paymentMethod) {}

    public record OrderRequest(@NotBlank @Size(max = 40) String planCode) {}

    /** Everything the browser needs to open Razorpay Checkout. */
    public record CheckoutDto(String keyId, String orderId, long amountPaise, String currency, String businessName,
                              String description, String prefillName, String prefillEmail, String prefillContact) {}

    public record VerifyRequest(
            @NotBlank @Size(max = 60) String razorpayOrderId,
            @NotBlank @Size(max = 60) String razorpayPaymentId,
            @NotBlank @Size(max = 200) String razorpaySignature) {}

    public record PaymentDto(Long id, String planName, long amountPaise, String status, String razorpayPaymentId,
                             Instant createdAt, Instant paidAt, Instant periodEnd, String invoiceNumber) {}

    /** Admin API: create or change a plan. Amount is in rupees here, stored as paise. */
    public record PlanUpdate(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 300) String description,
            @NotNull @DecimalMin("1") @DecimalMax("1000000") java.math.BigDecimal amountRupees,
            @Min(1) @Max(1830) int durationDays,
            @NotBlank @Size(max = 20) String intervalLabel,
            @Size(max = 40) String badge,
            @Size(max = 2000) String features,
            boolean highlighted,
            boolean active,
            int sortOrder) {}
}
