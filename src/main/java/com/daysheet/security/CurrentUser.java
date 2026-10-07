package com.daysheet.security;

import com.daysheet.config.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {}

    public static AuthPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
            return p;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
    }

    public static Long workspaceId() { return get().workspaceId(); }

    public static Long userId() { return get().userId(); }

    public static AdminPrincipal admin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminPrincipal p) return p;
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Admin sign-in required.");
    }
}
