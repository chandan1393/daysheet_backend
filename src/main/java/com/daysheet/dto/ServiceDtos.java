package com.daysheet.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public final class ServiceDtos {
    private ServiceDtos() {}

    public record ServiceRequest(
            @NotBlank(message = "Enter a service name.") @Size(max = 120) String name,
            @Size(max = 500) String description,
            @Min(value = 5, message = "Duration must be at least 5 minutes.")
            @Max(value = 600, message = "Duration must be under 10 hours.") int durationMinutes,
            @NotNull(message = "Enter a price (0 for free).") @PositiveOrZero @DecimalMax("10000000") BigDecimal price,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Pick a colour.") String color,
            boolean bookableOnline) {}

    public record ServiceDto(Long id, String name, String description, int durationMinutes,
                             BigDecimal price, String color, boolean bookableOnline) {}
}
