package com.daysheet.dto;

import com.daysheet.domain.Profession;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "Enter your name.") @Size(max = 120) String fullName,
            @NotBlank(message = "Enter your email.") @Email(message = "Enter a valid email.") @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 72, message = "Use 8 to 72 characters for your password.") String password,
            @NotBlank(message = "Enter your practice name.") @Size(max = 120) String workspaceName,
            @NotNull(message = "Choose your profession.") Profession profession,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotBlank @Size(max = 60) String timezone,
            boolean sampleData,
            /** Hidden field: people never fill it, spam bots do. */
            @Size(max = 200) String website) {}

    public record LoginRequest(
            @NotBlank(message = "Enter your email.") @Size(max = 160) String email,
            @NotBlank(message = "Enter your password.") @Size(max = 72) String password) {}

    public record ForgotPasswordRequest(
            @NotBlank(message = "Enter your email.") @Email(message = "Enter a valid email.") @Size(max = 160) String email) {}

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 8, max = 72, message = "Use 8 to 72 characters for your password.") String password) {}

    public record MessageResponse(String message) {}

    /** Signup result: no login yet; the user must confirm their email first. */
    public record RegisterResponse(boolean verificationRequired, String email, String pendingToken, boolean emailSent) {}

    public record VerifyCodeRequest(@NotBlank @Size(max = 600) String pendingToken,
                                    @NotBlank(message = "Enter the 6-digit code.") @Size(max = 12) String code) {}

    public record VerifyLinkRequest(@NotBlank @Size(max = 200) String token) {}

    public record ResendRequest(@NotBlank @Size(max = 600) String pendingToken) {}

    public record UserDto(Long id, String fullName, String email, String role) {}

    public record WorkspaceDto(Long id, String name, String slug, String profession, String professionName,
                               String clientLabel, String clientLabelPlural, String sessionLabel,
                               String currency, String timezone, String phone, String email, String address,
                               int slotMinutes, String plan, Instant trialEndsAt, long trialDaysLeft,
                               Instant subscriptionEndsAt, boolean expired, long daysLeft,
                               List<String> modules, String practitionerTitle, String registrationNumber) {}

    public record SessionResponse(String token, UserDto user, WorkspaceDto workspace) {}

    public record MeResponse(UserDto user, WorkspaceDto workspace) {}

    public record ProfessionDto(String code, String name, String clientLabel, String clientLabelPlural,
                                String sessionLabel, List<String> sampleServices) {}
}
