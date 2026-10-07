package com.daysheet.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class BookingDtos {
    private BookingDtos() {}

    public record PublicPractice(String name, String slug, String professionName, String clientLabel,
                                 String sessionLabel, String currency, String phone, String email,
                                 String address, List<ServiceDtos.ServiceDto> services, List<Integer> openDays,
                                 LocalDate today) {}

    public record SlotsResponse(LocalDate date, List<String> slots) {}

    public record BookingRequest(
            @NotNull(message = "Choose a service.") Long serviceId,
            @NotNull(message = "Choose a date.") LocalDate date,
            @NotNull(message = "Choose a time.") @JsonFormat(pattern = "HH:mm") LocalTime time,
            @NotBlank(message = "Enter your name.") @Size(max = 120) String fullName,
            @Email(message = "Enter a valid email.") @Size(max = 160) String email,
            @NotBlank(message = "Enter your phone number.")
            @Pattern(regexp = "^[+0-9 ()-]{7,20}$", message = "Enter a valid phone number.") String phone,
            @Size(max = 1000) String notes,
            /** Hidden field: people never fill it, spam bots do. */
            @Size(max = 200) String website) {}

    public record BookingConfirmation(Long id, String practiceName, String serviceName, LocalDateTime startAt,
                                      LocalDateTime endAt, String clientName) {}
}
