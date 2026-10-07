package com.daysheet.dto;

import com.daysheet.domain.InvoiceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class InvoiceDtos {
    private InvoiceDtos() {}

    public record InvoiceItemDto(
            @NotBlank(message = "Every line needs a description.") @Size(max = 300) String description,
            @NotNull @PositiveOrZero @DecimalMax("100000") BigDecimal quantity,
            @NotNull @PositiveOrZero @DecimalMax("10000000") BigDecimal unitPrice) {}

    public record InvoiceRequest(
            @NotNull(message = "Choose who the invoice is for.") Long clientId,
            Long appointmentId,
            LocalDate issueDate,
            LocalDate dueDate,
            @PositiveOrZero @DecimalMax("100") BigDecimal taxRate,
            @NotEmpty(message = "Add at least one line.") @Size(max = 100, message = "Use at most 100 lines.") @Valid List<InvoiceItemDto> items,
            @Size(max = 2000) String notes,
            InvoiceStatus status) {}

    public record InvoiceStatusRequest(@NotNull InvoiceStatus status) {}

    public record InvoiceSummary(Long id, String number, Long clientId, String clientName,
                                 LocalDate issueDate, LocalDate dueDate, String status,
                                 BigDecimal total, boolean overdue) {}

    public record PartyDto(Long id, String name, String email, String phone, String address) {}

    public record InvoiceDetail(Long id, String number, PartyDto client, PartyDto practice, String currency,
                                Long appointmentId, LocalDate issueDate, LocalDate dueDate, String status,
                                List<InvoiceItemDto> items, BigDecimal subtotal, BigDecimal taxRate,
                                BigDecimal taxAmount, BigDecimal total, LocalDate paidDate, String notes,
                                boolean overdue) {}
}
