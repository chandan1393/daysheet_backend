package com.daysheet.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locks an email address for 15 minutes after 5 wrong passwords in 15 minutes,
 * whichever IP the attempts come from. Works alongside the per-IP limit in RateLimitFilter.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK = Duration.ofMinutes(15);

    private record Attempts(int failures, Instant firstFailure, Instant lockedUntil) {}

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    /** Minutes left on the lock, or 0 when the address may try again. */
    public long minutesLocked(String email) {
        Attempts a = attempts.get(key(email));
        if (a == null || a.lockedUntil() == null) return 0;
        long seconds = Duration.between(Instant.now(), a.lockedUntil()).getSeconds();
        return seconds <= 0 ? 0 : (seconds + 59) / 60;
    }

    public void failed(String email) {
        Instant now = Instant.now();
        attempts.compute(key(email), (k, a) -> {
            if (a == null || a.firstFailure().plus(WINDOW).isBefore(now)) return new Attempts(1, now, null);
            int failures = a.failures() + 1;
            return new Attempts(failures, a.firstFailure(), failures >= MAX_FAILURES ? now.plus(LOCK) : a.lockedUntil());
        });
    }

    public void succeeded(String email) {
        attempts.remove(key(email));
    }

    @Scheduled(fixedDelay = 600_000)
    public void cleanUp() {
        Instant cutoff = Instant.now().minus(WINDOW.plus(LOCK));
        attempts.entrySet().removeIf(e -> e.getValue().firstFailure().isBefore(cutoff)
                && (e.getValue().lockedUntil() == null || e.getValue().lockedUntil().isBefore(Instant.now())));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
