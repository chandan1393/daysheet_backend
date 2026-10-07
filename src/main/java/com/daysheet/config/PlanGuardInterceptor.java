package com.daysheet.config;

import com.daysheet.domain.Workspace;
import com.daysheet.repository.WorkspaceRepository;
import com.daysheet.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Set;

/**
 * When a practice's trial or paid period has ended, everything stays readable but
 * changes are blocked (HTTP 402) until they pay. Billing, login and settings for the
 * plan keep working so they can renew.
 */
@Component
@RequiredArgsConstructor
public class PlanGuardInterceptor implements HandlerInterceptor {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final WorkspaceRepository workspaces;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) return true;

        Workspace w = workspaces.findById(principal.workspaceId()).orElse(null);
        if (w == null) return true;
        if (w.isSuspendedNow()) {
            return deny(response, 403, "This account is suspended. Contact support to restore it.");
        }

        if (READ_METHODS.contains(request.getMethod())) return true;
        String path = request.getRequestURI();
        if (path.startsWith("/api/billing") || path.startsWith("/api/auth")) return true;
        if (!w.isExpired(Instant.now())) return true;
        return deny(response, 402, "Your plan has ended. Choose a plan to keep making changes.");
    }

    private static boolean deny(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\",\"fields\":{}}");
        return false;
    }
}
