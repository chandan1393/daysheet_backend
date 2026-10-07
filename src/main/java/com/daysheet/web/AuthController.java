package com.daysheet.web;

import com.daysheet.dto.AuthDtos.*;
import com.daysheet.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;
    private final com.daysheet.service.EmailVerificationService verification;

    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/verify-email")
    public SessionResponse verifyEmail(@Valid @RequestBody VerifyCodeRequest request) {
        return verification.verifyCode(request.pendingToken(), request.code());
    }

    @PostMapping("/verify-link")
    public SessionResponse verifyLink(@Valid @RequestBody VerifyLinkRequest request) {
        return verification.verifyLink(request.token());
    }

    @PostMapping("/resend-verification")
    public MessageResponse resend(@Valid @RequestBody ResendRequest request) {
        verification.resend(request.pendingToken());
        return new MessageResponse("A new code is on its way. Check your inbox and spam folder.");
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return auth.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return auth.resetPassword(request);
    }

    @GetMapping("/me")
    public MeResponse me() {
        return auth.me();
    }
}
