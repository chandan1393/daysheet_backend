package com.daysheet.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public final class WorkspaceDtos {
    private WorkspaceDtos() {}

    public record WorkspaceUpdateRequest(
            @NotBlank(message = "Enter your practice name.") @Size(max = 120) String name,
            @Size(max = 30) String phone,
            @Email(message = "Enter a valid email.") @Size(max = 160) String email,
            @Size(max = 300) String address,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotBlank @Size(max = 60) String timezone,
            @NotBlank @Size(max = 40) String clientLabel,
            @NotBlank @Size(max = 40) String clientLabelPlural,
            @NotBlank @Size(max = 40) String sessionLabel,
            @Min(5) @Max(120) int slotMinutes,
            @Size(max = 160) String practitionerTitle,
            @Size(max = 80) String registrationNumber) {}

    public record ModulesRequest(@jakarta.validation.constraints.NotNull java.util.List<@Size(max = 30) String> modules) {}

    public record WorkingHoursDto(
            int dayOfWeek,
            boolean enabled,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime) {}
}
