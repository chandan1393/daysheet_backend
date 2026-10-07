package com.daysheet.web;

import com.daysheet.dto.AdminDtos.*;
import com.daysheet.service.AdminAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAccountService accounts;

    @PostMapping("/login")
    public AdminSession login(@Valid @RequestBody LoginRequest request) { return accounts.login(request); }

    @GetMapping("/me")
    public AdminDto me() { return AdminAccountService.toDtoPublic(accounts.current()); }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) { accounts.changePassword(request); }
}
