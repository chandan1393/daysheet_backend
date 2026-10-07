package com.daysheet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stops floods before they reach the database: per-IP limits on login, signup,
 * password resets, public bookings, uploads and the API as a whole, plus a cap on
 * JSON body size. Runs before Spring Security.
 *
 * Counters live in memory, which is right for a single server. If you run several
 * servers, move this to Redis (or rely on the Nginx limits in deploy/nginx as well).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long MAX_JSON_BYTES = 1024 * 1024; // 1 MB for anything that isn't a file upload

    private record Rule(String name, String method, String pattern, int limit, Duration window) {}

    /** First matching rule wins, so specific rules come before general ones. */
    private static final List<Rule> RULES = List.of(
            new Rule("login", "POST", "/api/auth/login", 10, Duration.ofMinutes(5)),
            new Rule("register", "POST", "/api/auth/register", 5, Duration.ofHours(1)),
            new Rule("forgot", "POST", "/api/auth/forgot-password", 5, Duration.ofHours(1)),
            new Rule("reset", "POST", "/api/auth/reset-password", 10, Duration.ofHours(1)),
            new Rule("verify", "POST", "/api/auth/verify-*", 20, Duration.ofMinutes(15)),
            new Rule("resend", "POST", "/api/auth/resend-verification", 6, Duration.ofHours(1)),
            new Rule("book", "POST", "/api/public/book/*", 10, Duration.ofHours(1)),
            new Rule("webhook", "POST", "/api/public/razorpay/webhook", 300, Duration.ofMinutes(1)),
            new Rule("public", "GET", "/api/public/**", 120, Duration.ofMinutes(1)),
            new Rule("admin-login", "POST", "/api/admin/auth/login", 10, Duration.ofMinutes(15)),
            new Rule("admin", null, "/api/admin/**", 300, Duration.ofMinutes(1)),
            new Rule("upload", "POST", "/api/clients/*/documents", 60, Duration.ofMinutes(10)),
            new Rule("payment", "POST", "/api/billing/**", 20, Duration.ofMinutes(10)),
            new Rule("api", null, "/api/**", 600, Duration.ofMinutes(1))
    );

    private static final class Window {
        final long startedAt;
        final AtomicInteger count = new AtomicInteger();
        Window(long startedAt) { this.startedAt = startedAt; }
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String contentType = request.getContentType();
        boolean multipart = contentType != null && contentType.toLowerCase().startsWith("multipart/");
        if (!multipart && request.getContentLengthLong() > MAX_JSON_BYTES) {
            reject(response, 413, "That request is too large.", 0);
            return;
        }

        for (Rule rule : RULES) {
            if (rule.method() != null && !rule.method().equalsIgnoreCase(request.getMethod())) continue;
            if (!matcher.match(rule.pattern(), path)) continue;

            long now = System.currentTimeMillis();
            long windowMs = rule.window().toMillis();
            String key = rule.name() + "|" + request.getRemoteAddr();
            Window w = windows.compute(key, (k, existing) ->
                    existing == null || now - existing.startedAt >= windowMs ? new Window(now) : existing);
            if (w.count.incrementAndGet() > rule.limit()) {
                long retryAfter = Math.max(1, (w.startedAt + windowMs - now) / 1000);
                reject(response, 429, "Too many requests. Please wait a little and try again.", retryAfter);
                return;
            }
            break;
        }
        chain.doFilter(request, response);
    }

    /** Drops finished windows so memory stays flat. */
    @Scheduled(fixedDelay = 300_000)
    public void cleanUp() {
        long now = System.currentTimeMillis();
        long longest = RULES.stream().mapToLong(r -> r.window().toMillis()).max().orElse(3_600_000);
        windows.entrySet().removeIf(e -> now - e.getValue().startedAt > longest);
    }

    private static void reject(HttpServletResponse response, int status, String message, long retryAfter) throws IOException {
        response.setStatus(status);
        if (retryAfter > 0) response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\",\"fields\":{}}");
    }
}
