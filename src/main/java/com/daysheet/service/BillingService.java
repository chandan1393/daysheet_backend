package com.daysheet.service;

import com.daysheet.billing.GstSettings;
import com.daysheet.billing.GstStates;
import com.daysheet.billing.RazorpayClient;
import com.daysheet.config.ApiException;
import com.daysheet.domain.*;
import com.daysheet.dto.AuthDtos.WorkspaceDto;
import com.daysheet.dto.BillingDtos.*;
import com.daysheet.repository.AppUserRepository;
import com.daysheet.repository.InvoiceSequenceRepository;
import com.daysheet.repository.PaymentRepository;
import com.daysheet.repository.PricePlanRepository;
import com.daysheet.security.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Subscription payments with Razorpay:
 * 1. createOrder: server fixes the amount from the database and creates a Razorpay order.
 * 2. Browser opens Razorpay Checkout and returns payment id + signature.
 * 3. verify: server checks the signature and extends the plan.
 * The webhook does step 3 as well, in case the customer closes the tab after paying.
 */
@Service
@RequiredArgsConstructor
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final PricePlanRepository plans;
    private final PaymentRepository payments;
    private final AppUserRepository users;
    private final WorkspaceService workspaceService;
    private final RazorpayClient razorpay;
    private final MailService mail;
    private final ObjectMapper json;
    private final GstSettings gst;
    private final InvoiceSequenceRepository sequences;


    @Transactional(readOnly = true)
    public List<PlanDto> activePlans() {
        return plans.findByActiveTrueOrderBySortOrderAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public CheckoutDto createOrder(OrderRequest request) {
        if (!razorpay.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Online payment isn't switched on yet. Email " + gst.supportEmail() + " and we'll help you pay.");
        }
        PricePlan plan = plans.findByCodeIgnoreCase(request.planCode())
                .filter(PricePlan::isActive)
                .orElseThrow(() -> ApiException.notFound("Plan"));
        Workspace w = workspaceService.current();
        if (!GstStates.isValid(w.getBillingStateCode()) || w.getBillingName() == null || w.getBillingAddress() == null) {
            throw ApiException.badRequest("Add your billing details first. They go on your GST invoice.");
        }
        GstSettings.Breakdown tax = gst.breakdown(plan.getAmountPaise(), w.getBillingStateCode());
        // Stop anyone hammering the payment button and creating hundreds of orders.
        if (payments.countByWorkspaceIdAndCreatedAtAfter(w.getId(), Instant.now().minus(Duration.ofHours(1))) >= 15) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many payment attempts. Please try again later.");
        }

        String receipt = "ws" + w.getId() + "-" + System.currentTimeMillis();
        RazorpayClient.Order order = razorpay.createOrder(tax.totalPaise(), receipt,
                Map.of("workspace_id", String.valueOf(w.getId()), "plan_code", plan.getCode()));

        Payment p = new Payment();
        p.setWorkspace(w);
        p.setPlanCode(plan.getCode());
        p.setPlanName(plan.getName());
        p.setDurationDays(plan.getDurationDays());
        p.setAmountPaise(tax.totalPaise());
        p.setTaxablePaise(tax.taxablePaise());
        p.setCgstPaise(tax.cgstPaise());
        p.setSgstPaise(tax.sgstPaise());
        p.setIgstPaise(tax.igstPaise());
        p.setGstRate(gst.rate());
        p.setSac(gst.sac());
        p.setBillName(w.getBillingName());
        p.setBillAddress(w.getBillingAddress());
        p.setBillStateCode(w.getBillingStateCode());
        p.setBillGstin(w.getBillingGstin());
        p.setRazorpayOrderId(order.id());
        p.setMethod("RAZORPAY");
        payments.save(p);

        AppUser u = users.findById(CurrentUser.userId()).orElse(null);
        return new CheckoutDto(razorpay.keyId(), order.id(), tax.totalPaise(), "INR", gst.businessName(),
                "Daysheet " + plan.getName() + " plan for " + w.getName(),
                u != null ? u.getFullName() : "", u != null ? u.getEmail() : "", w.getPhone() != null ? w.getPhone() : "");
    }

    /** Called by the browser after Checkout succeeds. */
    @Transactional
    public WorkspaceDto verify(VerifyRequest r) {
        if (!razorpay.isValidPaymentSignature(r.razorpayOrderId(), r.razorpayPaymentId(), r.razorpaySignature())) {
            log.warn("Rejected payment with a bad signature, order {}", r.razorpayOrderId());
            throw ApiException.badRequest("We couldn't confirm this payment. If money was taken, email us with the payment ID.");
        }
        Payment p = payments.lockByOrderId(r.razorpayOrderId())
                .filter(found -> found.getWorkspace().getId().equals(CurrentUser.workspaceId()))
                .orElseThrow(() -> ApiException.notFound("Payment"));
        markPaid(p, r.razorpayPaymentId());
        return Mappers.workspace(p.getWorkspace());
    }

    /** Razorpay webhook: picks up payments even if the customer closed the tab. */
    @Transactional
    public void handleWebhook(byte[] body, String signature) {
        if (!razorpay.isValidWebhookSignature(body, signature)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid signature.");
        }
        try {
            JsonNode root = json.readTree(body);
            String event = root.path("event").asText();
            if (!event.equals("payment.captured") && !event.equals("order.paid")) return;
            JsonNode payment = root.path("payload").path("payment").path("entity");
            String orderId = payment.path("order_id").asText(null);
            String paymentId = payment.path("id").asText(null);
            long amount = payment.path("amount").asLong(-1);
            if (orderId == null || paymentId == null) return;
            payments.lockByOrderId(orderId).ifPresent(p -> {
                if (amount != p.getAmountPaise()) {
                    log.warn("Webhook amount {} does not match order {} amount {}", amount, orderId, p.getAmountPaise());
                    return;
                }
                markPaid(p, paymentId);
            });
        } catch (java.io.IOException e) {
            log.warn("Unreadable Razorpay webhook: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> history() {
        return payments.findByWorkspaceIdOrderByCreatedAtDesc(CurrentUser.workspaceId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .map(p -> new PaymentDto(p.getId(), p.getPlanName(), p.getAmountPaise(), p.getStatus().name(),
                        reference(p), p.getCreatedAt(), p.getPaidAt(), p.getPeriodEnd(), p.getInvoiceNumber()))
                .toList();
    }

    /** Idempotent: a second call for the same order (browser + webhook) does nothing. */
    private void markPaid(Payment p, String paymentId) {
        if (p.getStatus() == PaymentStatus.PAID) return;
        Workspace w = p.getWorkspace();
        Instant now = Instant.now();
        // New period starts when the current access ends, so paying early never loses days.
        Instant base = now;
        Instant currentEnd = w.accessEndsAt();
        if (!w.isExpired(now) && currentEnd != null && currentEnd.isAfter(now)) base = currentEnd;
        Instant end = base.plus(Duration.ofDays(p.getDurationDays()));

        LocalDate today = LocalDate.now(IST);
        String fy = financialYear(today);
        sequences.ensure(fy);
        InvoiceSequence seq = sequences.lock(fy).orElseThrow();
        seq.setLastNumber(seq.getLastNumber() + 1);
        p.setInvoiceNumber(gst.invoicePrefix() + "/" + fy + "/" + String.format("%04d", seq.getLastNumber()));
        p.setInvoiceDate(today);

        p.setStatus(PaymentStatus.PAID);
        p.setRazorpayPaymentId(paymentId);
        p.setPaidAt(now);
        p.setPeriodStart(base);
        p.setPeriodEnd(end);
        w.setPlan(Plan.ACTIVE);
        w.setSubscriptionEndsAt(end);
        log.info("Payment {} for workspace {}: active until {}", paymentId, w.getId(), end);

        String until = DATE.format(end.atZone(WorkspaceService.zone(w)));
        if (w.getEmail() != null) mail.send(w.getEmail(), invoiceSubject(p), invoiceEmail(p, w, until));
    }

    private String invoiceSubject(Payment p) {
        return (gst.registered() ? "Tax invoice " : "Receipt ") + p.getInvoiceNumber() + " from Daysheet";
    }

    private String invoiceEmail(Payment p, Workspace w, String until) {
        StringBuilder b = new StringBuilder();
        b.append("Hi,\n\nThanks for your payment. Your practice \"").append(w.getName()).append("\" is active until ").append(until).append(".\n\n");
        b.append(gst.registered() ? "TAX INVOICE " : "RECEIPT ").append(p.getInvoiceNumber())
                .append("  |  Date: ").append(DATE.format(p.getInvoiceDate())).append("\n\n");
        b.append("From: ").append(gst.businessName()).append(", ").append(gst.businessAddress()).append("\n");
        if (gst.registered()) b.append("GSTIN: ").append(gst.gstin()).append("\n");
        b.append("\nBilled to: ").append(p.getBillName()).append(", ").append(p.getBillAddress()).append("\n");
        if (p.getBillGstin() != null) b.append("GSTIN: ").append(p.getBillGstin()).append("\n");
        b.append("Place of supply: ").append(GstStates.name(p.getBillStateCode())).append(" (").append(p.getBillStateCode()).append(")\n\n");
        b.append("Daysheet ").append(p.getPlanName()).append(" plan");
        if (gst.registered()) b.append("  (SAC ").append(p.getSac()).append(")");
        b.append("\n");
        b.append("Taxable value: ").append(rupees(nz(p.getTaxablePaise(), p.getAmountPaise()))).append("\n");
        if (nz(p.getCgstPaise(), 0) > 0) {
            int half = p.getGstRate() / 2;
            b.append("CGST ").append(half).append("%: ").append(rupees(p.getCgstPaise())).append("\n");
            b.append("SGST ").append(p.getGstRate() - half).append("%: ").append(rupees(p.getSgstPaise())).append("\n");
        }
        if (nz(p.getIgstPaise(), 0) > 0) {
            b.append("IGST ").append(p.getGstRate()).append("%: ").append(rupees(p.getIgstPaise())).append("\n");
        }
        b.append("Total paid: ").append(rupees(p.getAmountPaise())).append("\n");
        b.append("Payment reference: ").append(reference(p)).append("\n\n");
        b.append("You can view and print this invoice any time in Daysheet under Settings, Plan.\n\n");
        b.append("Questions? Reply to this email or write to ").append(gst.supportEmail()).append(".\n\nDaysheet");
        return b.toString();
    }

    private static String rupees(long paise) {
        return String.format(Locale.ENGLISH, "Rs %,.2f", paise / 100.0);
    }

    private PlanDto toDto(PricePlan p) {
        List<String> features = p.getFeatures() == null ? List.of()
                : Arrays.stream(p.getFeatures().split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        long total = gst.breakdown(p.getAmountPaise(), null).totalPaise();
        return new PlanDto(p.getCode(), p.getName(), p.getDescription(), p.getAmountPaise(), p.getDurationDays(),
                p.getIntervalLabel(), p.getBadge(), features, p.isHighlighted(),
                total, gst.pricesIncludeTax() || !gst.registered(), gst.rate());
    }

    // ---------- Billing details (who the invoice is made out to)

    @Transactional(readOnly = true)
    public BillingDetailsDto billingDetails() {
        Workspace w = workspaceService.current();
        return new BillingDetailsDto(
                w.getBillingName() != null ? w.getBillingName() : w.getName(),
                w.getBillingAddress() != null ? w.getBillingAddress() : (w.getAddress() != null ? w.getAddress() : ""),
                w.getBillingStateCode() != null ? w.getBillingStateCode() : "",
                w.getBillingGstin() != null ? w.getBillingGstin() : "");
    }

    @Transactional
    public BillingDetailsDto saveBillingDetails(BillingDetailsDto r) {
        if (!GstStates.isValid(r.stateCode())) throw ApiException.badRequest("Choose your state.");
        String gstin = r.gstin() == null ? "" : r.gstin().trim().toUpperCase(Locale.ROOT);
        if (!gstin.isEmpty()) {
            if (!GstStates.isValidGstin(gstin)) {
                throw ApiException.badRequest("That GSTIN doesn't look right. Check it, or leave it empty.");
            }
            if (!gstin.startsWith(r.stateCode())) {
                throw ApiException.badRequest("Your GSTIN belongs to " + GstStates.name(gstin.substring(0, 2)) + ". Choose that state.");
            }
        }
        Workspace w = workspaceService.current();
        w.setBillingName(r.name().trim());
        w.setBillingAddress(r.address().trim());
        w.setBillingStateCode(r.stateCode());
        w.setBillingGstin(gstin.isEmpty() ? null : gstin);
        return billingDetails();
    }

    // ---------- Tax invoice

    @Transactional(readOnly = true)
    public TaxInvoiceDto invoice(Long paymentId) {
        Payment p = payments.findByIdAndWorkspaceId(paymentId, CurrentUser.workspaceId())
                .filter(found -> found.getStatus() == PaymentStatus.PAID && found.getInvoiceNumber() != null)
                .orElseThrow(() -> ApiException.notFound("Invoice"));
        return toInvoice(p);
    }

    TaxInvoiceDto toInvoice(Payment p) {
        String sellerState = gst.supplierStateCode();
        Party seller = new Party(gst.businessName(), gst.businessAddress(), gst.gstin(), sellerState, GstStates.name(sellerState));
        Party buyer = new Party(p.getBillName(), p.getBillAddress(), p.getBillGstin(), p.getBillStateCode(),
                GstStates.name(p.getBillStateCode()));
        return new TaxInvoiceDto(p.getInvoiceNumber(), p.getInvoiceDate(), p.getGstRate() != null && p.getGstRate() > 0,
                seller, buyer, GstStates.name(p.getBillStateCode()) + " (" + p.getBillStateCode() + ")", p.getSac(),
                "Daysheet " + p.getPlanName() + " plan, " + p.getDurationDays() + " days of practice software",
                p.getPeriodStart(), p.getPeriodEnd(), nz(p.getTaxablePaise(), p.getAmountPaise()),
                p.getGstRate() == null ? 0 : p.getGstRate(), nz(p.getCgstPaise(), 0), nz(p.getSgstPaise(), 0),
                nz(p.getIgstPaise(), 0), p.getAmountPaise(), reference(p),
                "MANUAL".equals(p.getMethod()) ? "Bank transfer" : "Razorpay");
    }

    /** Rows for the GST report (GSTR-1) between two dates. */
    @Transactional(readOnly = true)
    public List<TaxInvoiceDto> invoicesBetween(LocalDate from, LocalDate to) {
        return payments.findByStatusAndInvoiceDateBetweenOrderByInvoiceNumberAsc(PaymentStatus.PAID, from, to)
                .stream().map(this::toInvoice).toList();
    }

    private static long nz(Long v, long fallback) { return v == null ? fallback : v; }

    static String reference(Payment p) {
        return p.getRazorpayPaymentId() != null ? p.getRazorpayPaymentId() : p.getReference();
    }

    /**
     * Records a bank transfer or direct UPI payment (entered by an admin): GST invoice, plan extension
     * and invoice email, exactly like an online payment.
     */
    @Transactional
    public Payment recordManualPayment(Workspace w, PricePlan plan, long amountPaise, String reference, String adminEmail) {
        GstSettings.Breakdown tax = gst.fromTotal(amountPaise, w.getBillingStateCode());
        Payment p = new Payment();
        p.setWorkspace(w);
        p.setPlanCode(plan.getCode());
        p.setPlanName(plan.getName());
        p.setDurationDays(plan.getDurationDays());
        p.setAmountPaise(tax.totalPaise());
        p.setTaxablePaise(tax.taxablePaise());
        p.setCgstPaise(tax.cgstPaise());
        p.setSgstPaise(tax.sgstPaise());
        p.setIgstPaise(tax.igstPaise());
        p.setGstRate(gst.rate());
        p.setSac(gst.sac());
        p.setBillName(w.getBillingName() != null ? w.getBillingName() : w.getName());
        p.setBillAddress(w.getBillingAddress() != null ? w.getBillingAddress() : (w.getAddress() != null ? w.getAddress() : ""));
        p.setBillStateCode(w.getBillingStateCode());
        p.setBillGstin(w.getBillingGstin());
        p.setMethod("MANUAL");
        p.setReference(reference);
        p.setRecordedBy(adminEmail);
        p.setRazorpayOrderId("manual_" + java.util.UUID.randomUUID());
        payments.save(p);
        markPaid(p, null);
        return p;
    }

    /** GST financial year in India: April to March, e.g. "2026-27". */
    static String financialYear(LocalDate d) {
        int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
        return start + "-" + String.format("%02d", (start + 1) % 100);
    }

}
