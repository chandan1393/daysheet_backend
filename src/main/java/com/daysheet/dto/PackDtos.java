package com.daysheet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Profession pack: prescriptions, packages, cases and deadlines. */
public final class PackDtos {
    private PackDtos() {}

    // ---------- Prescriptions
    public record RxItem(
            @NotBlank(message = "Each line needs a medicine.") @Size(max = 160) String medicine,
            @Size(max = 60) String dose, @Size(max = 40) String frequency, @Size(max = 60) String timing,
            @Size(max = 40) String duration, @Size(max = 200) String notes) {}

    public record PrescriptionRequest(
            @NotNull Long clientId, Long appointmentId,
            @Size(max = 300) String vitals, @Size(max = 1000) String complaints, @Size(max = 1000) String diagnosis,
            @Size(max = 4000) String advice, @Size(max = 1000) String tests, LocalDate followUpDate,
            @Size(max = 40, message = "Use at most 40 medicines.") @Valid List<RxItem> items) {}

    public record PrescriptionDto(Long id, Long clientId, String clientName, Long appointmentId, String vitals,
                                  String complaints, String diagnosis, String advice, String tests, LocalDate followUpDate,
                                  List<RxItem> items, String authorName, Instant createdAt) {}

    // ---------- Packages
    public record PackageTemplateRequest(
            @NotBlank @Size(max = 120) String name,
            @Min(1) @Max(500) int sessions,
            @NotNull @PositiveOrZero @DecimalMax("10000000") BigDecimal price,
            @Min(1) @Max(1830) Integer validityDays,
            Long serviceId) {}

    public record PackageTemplateDto(Long id, String name, int sessions, BigDecimal price, Integer validityDays,
                                     Long serviceId, String serviceName) {}

    public record SellPackageRequest(@NotNull Long templateId, LocalDate purchasedOn, boolean createInvoice, boolean markPaid) {}

    public record AdjustRequest(@Min(-50) @Max(50) int delta) {}

    public record ClientPackageDto(Long id, String name, int totalSessions, int usedSessions, BigDecimal price,
                                   LocalDate purchasedOn, LocalDate expiresOn, String status, String serviceName,
                                   Long invoiceId) {}

    // ---------- Cases
    public record CaseRequest(
            @NotNull Long clientId,
            @NotBlank(message = "Give the case a title.") @Size(max = 200) String title,
            @Size(max = 80) String caseNumber, @Size(max = 160) String court, @Size(max = 160) String oppositeParty,
            @Size(max = 80) String caseType, @Pattern(regexp = "OPEN|CLOSED") String status, @Size(max = 4000) String notes) {}

    public record HearingRequest(@NotNull(message = "Pick the hearing date.") LocalDate hearingDate,
                                 @Size(max = 200) String purpose, @Size(max = 4000) String outcome) {}

    public record HearingDto(Long id, Long caseId, String caseTitle, String caseNumber, String court, Long clientId,
                             String clientName, LocalDate hearingDate, String purpose, String outcome) {}

    public record CaseDto(Long id, Long clientId, String clientName, String title, String caseNumber, String court,
                          String oppositeParty, String caseType, String status, String notes, LocalDate nextHearing,
                          LocalDate lastHearing, int hearingCount, List<HearingDto> hearings, Instant createdAt) {}

    // ---------- Deadlines
    public record DeadlineRequest(
            @NotNull Long clientId,
            @NotBlank(message = "Give the task a name.") @Size(max = 120) String title,
            @Pattern(regexp = "GST|TDS|ITR|ADVANCE_TAX|ROC|AUDIT|OTHER") String category,
            @Size(max = 40) String period, @NotNull(message = "Pick the due date.") LocalDate dueDate,
            @Pattern(regexp = "NONE|MONTHLY|QUARTERLY|YEARLY") String recurrence, @Size(max = 1000) String notes) {}

    public record BulkDeadlineRequest(
            @NotEmpty(message = "Choose at least one client.") @Size(max = 500) List<Long> clientIds,
            @NotBlank @Size(max = 120) String title,
            @Pattern(regexp = "GST|TDS|ITR|ADVANCE_TAX|ROC|AUDIT|OTHER") String category,
            @Size(max = 40) String period, @NotNull LocalDate dueDate,
            @Pattern(regexp = "NONE|MONTHLY|QUARTERLY|YEARLY") String recurrence) {}

    public record DeadlineDto(Long id, Long clientId, String clientName, String title, String category, String period,
                              LocalDate dueDate, String status, LocalDate doneOn, String recurrence, String notes,
                              boolean overdue) {}

    /** Shown on the dashboard when the matching module is switched on. */
    public record PackSummary(List<HearingDto> upcomingHearings, List<DeadlineDto> dueDeadlines) {}
}
