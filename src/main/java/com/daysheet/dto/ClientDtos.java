package com.daysheet.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ClientDtos {
    private ClientDtos() {}

    public record ClientRequest(
            @NotBlank(message = "Enter a name.") @Size(max = 120) String fullName,
            @Email(message = "Enter a valid email.") @Size(max = 160) String email,
            @Size(max = 30) String phone,
            @Past(message = "Date of birth must be in the past.") LocalDate dateOfBirth,
            @Size(max = 200) String tags) {}

    public record ClientSummary(Long id, String fullName, String email, String phone, String tags,
                                long visits, LocalDateTime lastVisit, LocalDateTime nextVisit, Instant createdAt) {}

    public record NoteRequest(@NotBlank(message = "Write something first.") @Size(max = 10000, message = "Notes can be up to 10,000 characters.") String body,
                              Long appointmentId) {}

    public record NoteDto(Long id, String body, String authorName, Instant createdAt, Long appointmentId) {}

    public record ClientDetail(Long id, String fullName, String email, String phone, LocalDate dateOfBirth,
                               String tags, Instant createdAt, BigDecimal totalPaid, long visits,
                               List<NoteDto> notes,
                               List<DocumentDtos.DocumentDto> documents,
                               List<AppointmentDtos.AppointmentDto> appointments,
                               List<InvoiceDtos.InvoiceSummary> invoices,
                               List<PackDtos.PrescriptionDto> prescriptions,
                               List<PackDtos.ClientPackageDto> packages,
                               List<PackDtos.CaseDto> cases,
                               List<PackDtos.DeadlineDto> deadlines) {}
}
