package com.daysheet.dto;

import com.daysheet.domain.AppointmentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AppointmentDtos {
    private AppointmentDtos() {}

    public record NewClient(@Size(max = 120) String fullName, @Size(max = 30) String phone,
                            @Email @Size(max = 160) String email) {}

    public record AppointmentRequest(
            Long clientId,
            @Valid NewClient newClient,
            Long serviceId,
            @NotNull(message = "Pick a date and time.") LocalDateTime startAt,
            @Min(value = 5, message = "Duration must be at least 5 minutes.")
            @Max(value = 720, message = "Duration must be under 12 hours.") Integer durationMinutes,
            @PositiveOrZero @DecimalMax("10000000") BigDecimal price,
            @Size(max = 2000) String notes,
            boolean force) {}

    public record StatusRequest(@NotNull AppointmentStatus status) {}

    public record AppointmentDto(Long id, Long clientId, String clientName, String clientPhone,
                                 Long serviceId, String serviceName, String color,
                                 LocalDateTime startAt, LocalDateTime endAt, int durationMinutes,
                                 String status, String source, BigDecimal price, String notes,
                                 boolean invoiced, String packageName, int packageUsed, int packageTotal) {}

    /** The previous visit, shown when today's appointment is opened. */
    public record PreviousVisit(AppointmentDto appointment, List<ClientDtos.NoteDto> notes,
                                List<DocumentDtos.DocumentDto> documents, List<PackDtos.PrescriptionDto> prescriptions) {}

    /** Everything recorded for one visit, plus the visit before it. */
    public record VisitRecord(Long appointmentId, int visitNumber, List<ClientDtos.NoteDto> notes,
                              List<DocumentDtos.DocumentDto> documents, PreviousVisit previous,
                              List<PackDtos.PrescriptionDto> prescriptions) {}
}
