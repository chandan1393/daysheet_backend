package com.daysheet.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}

    // ---------- Accounts
    public record LoginRequest(@NotBlank @Size(max = 160) String email, @NotBlank @Size(max = 72) String password) {}

    public record AdminDto(Long id, String email, String fullName, boolean active, Instant lastLoginAt, Instant createdAt) {}

    public record AdminSession(String token, AdminDto admin) {}

    public record CreateAdminRequest(
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(min = 10, max = 72, message = "Admin passwords need 10 to 72 characters.") String password) {}

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 10, max = 72, message = "Admin passwords need 10 to 72 characters.") String newPassword) {}

    public record ActiveRequest(boolean active) {}

    // ---------- Overview
    public record PaymentRow(Long id, Long workspaceId, String practiceName, String planName, long amountPaise,
                             String method, String reference, String invoiceNumber, LocalDate invoiceDate, Instant paidAt) {}

    public record AuditRow(Long id, String adminEmail, String action, String details, Instant createdAt) {}

    public record Overview(long practices, long inTrial, long activePaid, long expired, long suspended,
                           long signups7d, long signups30d, long revenueThisMonthPaise, long revenueThisYearPaise,
                           long revenueAllTimePaise, List<PaymentRow> recentPayments, List<PracticeRow> recentPractices,
                           List<AuditRow> recentActivity) {}

    // ---------- Practices
    public record PracticeRow(Long id, String name, String slug, String ownerName, String ownerEmail, String profession,
                              String status, Instant accessEndsAt, long daysLeft, boolean suspended, long clients,
                              Instant createdAt, boolean emailVerified) {}

    public record PracticeDetail(PracticeRow practice, String phone, String email, String address, String timezone,
                                 String billingName, String billingAddress, String billingState, String billingGstin,
                                 String suspendedReason, Instant trialEndsAt, Instant subscriptionEndsAt,
                                 long appointments, long storageBytes, List<PaymentRow> payments) {}

    public record ExtendRequest(@Min(1) @Max(730) int days, @Size(max = 300) String note) {}

    public record ManualPaymentRequest(
            @NotBlank @Size(max = 40) String planCode,
            @NotNull @DecimalMin("1") @DecimalMax("1000000") BigDecimal amountRupees,
            @NotBlank(message = "Enter the UTR or transaction reference.") @Size(max = 80) String reference,
            @Pattern(regexp = "^$|\\d{2}", message = "Choose a state.") String billingStateCode) {}

    public record SuspendRequest(boolean suspended, @Size(max = 300) String reason) {}

    // ---------- Company and GST
    public record CompanyDto(
            @NotBlank(message = "Enter the business name.") @Size(max = 160) String businessName,
            @NotBlank(message = "Enter the registered address.") @Size(max = 400) String businessAddress,
            @Size(max = 30) String businessPhone,
            @Size(max = 160) String legalName,
            @NotBlank @Email @Size(max = 160) String supportEmail,
            @Size(max = 15) String gstin,
            @Min(0) @Max(28) int gstRate,
            boolean pricesIncludeTax,
            @Size(max = 10) @Pattern(regexp = "^$|\\d{4,8}", message = "SAC is 4 to 8 digits.") String sac,
            @NotBlank @Size(max = 8) @Pattern(regexp = "[A-Za-z0-9]{1,8}", message = "Prefix: letters and numbers only.") String invoicePrefix,
            @Size(max = 80) String jurisdictionCity,
            @Size(max = 120) String grievanceOfficer,
            Instant updatedAt) {}
}
