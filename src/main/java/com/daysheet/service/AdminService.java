package com.daysheet.service;

import com.daysheet.billing.GstStates;
import com.daysheet.config.ApiException;
import com.daysheet.domain.*;
import com.daysheet.dto.AdminDtos.*;
import com.daysheet.dto.BillingDtos.PlanUpdate;
import com.daysheet.dto.BillingDtos.TaxInvoiceDto;
import com.daysheet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/** Everything the admin panel does with practices, payments, prices and company settings. */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final WorkspaceRepository workspaces;
    private final AppUserRepository users;
    private final ClientRepository clients;
    private final AppointmentRepository appointments;
    private final ClientDocumentRepository documents;
    private final PaymentRepository payments;
    private final PricePlanRepository plans;
    private final AdminAuditRepository audit;
    private final AdminAccountService accounts;
    private final BillingService billing;
    private final CompanySettingsService company;

    // ---------- Overview

    @Transactional(readOnly = true)
    public Overview overview() {
        accounts.current();
        Instant now = Instant.now();
        List<Workspace> all = workspaces.findAll();
        long trial = 0, active = 0, expired = 0, suspended = 0;
        for (Workspace w : all) {
            if (w.isSuspendedNow()) suspended++;
            if (w.isExpired(now)) expired++;
            else if (w.getPlan() == Plan.TRIAL) trial++;
            else active++;
        }
        LocalDate today = LocalDate.now(IST);
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(IST).toInstant();
        int fyStartYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        Instant fyStart = LocalDate.of(fyStartYear, 4, 1).atStartOfDay(IST).toInstant();
        Map<Long, Long> clientCounts = clientCounts();
        List<PracticeRow> recent = workspaces.findAllByOrderByCreatedAtDesc().stream().limit(8)
                .map(w -> row(w, clientCounts, now)).toList();
        return new Overview(all.size(), trial, active, expired, suspended,
                workspaces.countByCreatedAtAfter(now.minus(Duration.ofDays(7))),
                workspaces.countByCreatedAtAfter(now.minus(Duration.ofDays(30))),
                payments.revenueSince(monthStart), payments.revenueSince(fyStart), payments.revenueSince(Instant.EPOCH),
                payments.findTop10ByStatusOrderByPaidAtDesc(PaymentStatus.PAID).stream().map(this::paymentRow).toList(),
                recent,
                audit.findTop15ByOrderByCreatedAtDesc().stream()
                        .map(a -> new AuditRow(a.getId(), a.getAdminEmail(), a.getAction(), a.getDetails(), a.getCreatedAt())).toList());
    }

    // ---------- Practices

    @Transactional(readOnly = true)
    public List<PracticeRow> practices(String query, String status) {
        accounts.current();
        Instant now = Instant.now();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Map<Long, Long> clientCounts = clientCounts();
        return workspaces.findAllByOrderByCreatedAtDesc().stream()
                .map(w -> row(w, clientCounts, now))
                .filter(r -> q.isEmpty() || contains(r.name(), q) || contains(r.ownerEmail(), q) || contains(r.slug(), q) || contains(r.ownerName(), q))
                .filter(r -> status == null || status.isBlank() || status.equalsIgnoreCase("ALL")
                        || (status.equalsIgnoreCase("SUSPENDED") ? r.suspended()
                            : status.equalsIgnoreCase("UNVERIFIED") ? !r.emailVerified() : r.status().equalsIgnoreCase(status)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PracticeDetail practice(Long id) {
        accounts.current();
        Workspace w = find(id);
        Instant now = Instant.now();
        LocalDateTime far = LocalDateTime.of(2000, 1, 1, 0, 0);
        long appts = appointments.countByWorkspaceIdAndStartAtBetween(w.getId(), far, LocalDateTime.of(2100, 1, 1, 0, 0));
        return new PracticeDetail(row(w, clientCounts(), now), w.getPhone(), w.getEmail(), w.getAddress(), w.getTimezone(),
                w.getBillingName(), w.getBillingAddress(),
                w.getBillingStateCode() == null ? null : GstStates.name(w.getBillingStateCode()) + " (" + w.getBillingStateCode() + ")",
                w.getBillingGstin(), w.getSuspendedReason(), w.getTrialEndsAt(), w.getSubscriptionEndsAt(),
                appts, documents.totalBytes(w.getId()),
                payments.findByWorkspaceIdOrderByCreatedAtDesc(w.getId()).stream()
                        .filter(p -> p.getStatus() == PaymentStatus.PAID).map(this::paymentRow).toList());
    }

    /** Free extra days: on the trial while it's running, otherwise on the paid plan. */
    @Transactional
    public PracticeDetail extend(Long id, ExtendRequest r) {
        AdminUser me = accounts.current();
        Workspace w = find(id);
        Instant now = Instant.now();
        if (w.getPlan() == Plan.TRIAL && !w.isExpired(now)) {
            w.setTrialEndsAt(w.getTrialEndsAt().plus(Duration.ofDays(r.days())));
        } else {
            Instant base = w.getSubscriptionEndsAt() != null && w.getSubscriptionEndsAt().isAfter(now) ? w.getSubscriptionEndsAt() : now;
            w.setPlan(Plan.ACTIVE);
            w.setSubscriptionEndsAt(base.plus(Duration.ofDays(r.days())));
        }
        accounts.log(me.getEmail(), "ACCESS_EXTENDED", w.getName() + " (#" + w.getId() + "): +" + r.days() + " days"
                + (r.note() == null || r.note().isBlank() ? "" : ". " + r.note().trim()));
        return practice(id);
    }

    /** A bank transfer or direct UPI payment: creates the GST invoice and extends the plan. */
    @Transactional
    public PracticeDetail recordPayment(Long id, ManualPaymentRequest r) {
        AdminUser me = accounts.current();
        Workspace w = find(id);
        PricePlan plan = plans.findByCodeIgnoreCase(r.planCode()).orElseThrow(() -> ApiException.notFound("Plan"));
        if (r.billingStateCode() != null && !r.billingStateCode().isBlank()) {
            if (!GstStates.isValid(r.billingStateCode())) throw ApiException.badRequest("Choose a valid state.");
            w.setBillingStateCode(r.billingStateCode());
        }
        if (!GstStates.isValid(w.getBillingStateCode())) {
            throw ApiException.badRequest("Choose the customer's state; it decides CGST/SGST or IGST on the invoice.");
        }
        long paise = r.amountRupees().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        Payment p = billing.recordManualPayment(w, plan, paise, r.reference().trim(), me.getEmail());
        accounts.log(me.getEmail(), "PAYMENT_RECORDED", w.getName() + " (#" + w.getId() + "): " + plan.getName() + ", Rs "
                + r.amountRupees().toPlainString() + ", ref " + r.reference().trim() + ", invoice " + p.getInvoiceNumber());
        return practice(id);
    }

    @Transactional
    public PracticeDetail suspend(Long id, SuspendRequest r) {
        AdminUser me = accounts.current();
        Workspace w = find(id);
        w.setSuspended(r.suspended());
        w.setSuspendedReason(r.suspended() ? (r.reason() == null ? null : r.reason().trim()) : null);
        accounts.log(me.getEmail(), r.suspended() ? "PRACTICE_SUSPENDED" : "PRACTICE_RESTORED",
                w.getName() + " (#" + w.getId() + ")" + (r.suspended() && r.reason() != null && !r.reason().isBlank() ? ": " + r.reason().trim() : ""));
        return practice(id);
    }

    // ---------- Payments

    @Transactional(readOnly = true)
    public List<PaymentRow> payments(LocalDate from, LocalDate to) {
        accounts.current();
        return payments.findByStatusAndInvoiceDateBetweenOrderByInvoiceNumberAsc(PaymentStatus.PAID, from, to)
                .stream().map(this::paymentRow).sorted(Comparator.comparing(PaymentRow::paidAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TaxInvoiceDto invoice(Long paymentId) {
        accounts.current();
        Payment p = payments.findById(paymentId)
                .filter(found -> found.getStatus() == PaymentStatus.PAID && found.getInvoiceNumber() != null)
                .orElseThrow(() -> ApiException.notFound("Invoice"));
        return billing.toInvoice(p);
    }

    @Transactional(readOnly = true)
    public List<TaxInvoiceDto> invoicesBetween(LocalDate from, LocalDate to) {
        accounts.current();
        return billing.invoicesBetween(from, to);
    }

    // ---------- Prices

    @Transactional(readOnly = true)
    public List<PricePlan> plans() {
        accounts.current();
        return plans.findAllByOrderBySortOrderAsc();
    }

    @Transactional
    public PricePlan savePlan(String code, PlanUpdate r) {
        AdminUser me = accounts.current();
        if (!code.matches("[A-Za-z0-9_]{2,40}")) throw ApiException.badRequest("Plan code: letters, numbers and _ only.");
        PricePlan p = plans.findByCodeIgnoreCase(code).orElseGet(() -> {
            PricePlan n = new PricePlan();
            n.setCode(code.toUpperCase(Locale.ROOT));
            return n;
        });
        long before = p.getAmountPaise();
        p.setName(r.name().trim());
        p.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        p.setAmountPaise(r.amountRupees().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact());
        p.setDurationDays(r.durationDays());
        p.setIntervalLabel(r.intervalLabel().trim());
        p.setBadge(r.badge() == null || r.badge().isBlank() ? null : r.badge().trim());
        p.setFeatures(r.features());
        p.setHighlighted(r.highlighted());
        p.setActive(r.active());
        p.setSortOrder(r.sortOrder());
        plans.save(p);
        accounts.log(me.getEmail(), "PLAN_SAVED", p.getCode() + ": Rs " + BigDecimal.valueOf(p.getAmountPaise()).movePointLeft(2).toPlainString()
                + (before > 0 && before != p.getAmountPaise() ? " (was Rs " + BigDecimal.valueOf(before).movePointLeft(2).toPlainString() + ")" : "")
                + ", " + p.getDurationDays() + " days, " + (p.isActive() ? "visible" : "hidden"));
        return p;
    }

    // ---------- Company and GST

    @Transactional(readOnly = true)
    public CompanyDto company() {
        accounts.current();
        var c = company.current();
        return new CompanyDto(c.businessName(), c.businessAddress(), c.businessPhone(), c.legalName(), c.supportEmail(), c.gstin(),
                c.gstRate(), c.pricesIncludeTax(), c.sac(), c.invoicePrefix(), c.jurisdictionCity(), c.grievanceOfficer(), null);
    }

    @Transactional
    public CompanyDto saveCompany(CompanyDto r) {
        AdminUser me = accounts.current();
        String gstin = r.gstin() == null ? "" : r.gstin().trim().toUpperCase(Locale.ROOT);
        if (!gstin.isEmpty() && !GstStates.isValidGstin(gstin)) {
            throw ApiException.badRequest("That GSTIN doesn't look right. Check it for typos.");
        }
        CompanySettings s = new CompanySettings();
        s.setBusinessName(r.businessName().trim());
        s.setBusinessAddress(r.businessAddress().trim());
        s.setBusinessPhone(blank(r.businessPhone()));
        s.setLegalName(blank(r.legalName()));
        s.setSupportEmail(r.supportEmail().trim());
        s.setGstin(gstin.isEmpty() ? null : gstin);
        s.setGstRate(r.gstRate());
        s.setPricesIncludeTax(r.pricesIncludeTax());
        s.setSac(blank(r.sac()));
        s.setInvoicePrefix(r.invoicePrefix().trim().toUpperCase(Locale.ROOT));
        s.setJurisdictionCity(blank(r.jurisdictionCity()));
        s.setGrievanceOfficer(blank(r.grievanceOfficer()));
        company.update(s);
        accounts.log(me.getEmail(), "COMPANY_SAVED", s.getBusinessName() + ", GSTIN " + (s.getGstin() == null ? "none" : s.getGstin())
                + ", GST " + s.getGstRate() + "% " + (s.isPricesIncludeTax() ? "included" : "on top"));
        return company();
    }

    // ---------- Helpers

    private Workspace find(Long id) {
        return workspaces.findById(id).orElseThrow(() -> ApiException.notFound("Practice"));
    }

    private Map<Long, Long> clientCounts() {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] r : clients.countPerWorkspace()) m.put((Long) r[0], ((Number) r[1]).longValue());
        return m;
    }

    private PracticeRow row(Workspace w, Map<Long, Long> clientCounts, Instant now) {
        AppUser owner = users.findByWorkspaceId(w.getId()).stream()
                .filter(u -> u.getRole() == Role.OWNER).findFirst().orElse(null);
        boolean expired = w.isExpired(now);
        String status = expired ? "EXPIRED" : w.getPlan() == Plan.TRIAL ? "TRIAL" : "ACTIVE";
        Instant ends = w.accessEndsAt();
        long daysLeft = ends == null || expired ? 0 : Math.max(0, (long) Math.ceil(Duration.between(now, ends).toHours() / 24.0));
        return new PracticeRow(w.getId(), w.getName(), w.getSlug(), owner != null ? owner.getFullName() : null,
                owner != null ? owner.getEmail() : null, w.getProfession().displayName(), status, ends, daysLeft,
                w.isSuspendedNow(), clientCounts.getOrDefault(w.getId(), 0L), w.getCreatedAt(),
                owner == null || !owner.isPendingVerification());
    }

    private PaymentRow paymentRow(Payment p) {
        return new PaymentRow(p.getId(), p.getWorkspace().getId(), p.getWorkspace().getName(), p.getPlanName(), p.getAmountPaise(),
                p.getMethod() == null ? "RAZORPAY" : p.getMethod(), BillingService.reference(p), p.getInvoiceNumber(),
                p.getInvoiceDate(), p.getPaidAt());
    }

    private static boolean contains(String v, String q) { return v != null && v.toLowerCase(Locale.ROOT).contains(q); }

    private static String blank(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
