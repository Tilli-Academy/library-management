package com.pageturn.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.pageturn.common.error.ApiException;

/**
 * Slows down password guessing: after {@code max-failures} failed sign-ins for one email within the window,
 * further attempts for that email are refused (429) until the window passes. A successful sign-in resets it.
 * Attempts are counted before the password is checked, so parallel guesses are counted too.
 *
 * <p>Kept in memory (resets on restart; per server instance). Counted per email; the per-IP limit lives in
 * RateLimitFilter. Trade-off: someone could deliberately lock
 * an account for up to the window length; that is preferable to unlimited guessing.
 */
@Component
public class LoginAttemptLimiter {

    private static final int MAX_TRACKED = 10_000;

    private record Attempts(Instant windowStart, int failures) {
    }

    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;
    private final Clock clock = Clock.systemUTC();

    public LoginAttemptLimiter(@Value("${pageturn.login.max-failures:5}") int maxFailures,
            @Value("${pageturn.login.failure-window:PT15M}") Duration window) {
        this.maxFailures = maxFailures;
        this.window = window;
    }

    /**
     * Counts this attempt BEFORE the (slow) password check, atomically. So 20 guesses sent at the same
     * moment can't all slip past the limit while the first ones are still being checked.
     * A successful sign-in clears the count again (see {@link #recordSuccess}).
     */
    public void reserveAttempt(String email) {
        if (attempts.size() > MAX_TRACKED) {
            attempts.values().removeIf(this::expired);
        }
        Attempts current = attempts.compute(email, (key, existing) -> existing == null || expired(existing)
                ? new Attempts(clock.instant(), 1)
                : new Attempts(existing.windowStart(), existing.failures() + 1));
        if (current.failures() > maxFailures) {
            long minutes = Math.max(1, Duration.between(clock.instant(), current.windowStart().plus(window)).toMinutes());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed sign-in attempts. Try again in about " + minutes + " minute(s).");
        }
    }

    public void recordSuccess(String email) {
        attempts.remove(email);
    }

    private boolean expired(Attempts a) {
        return clock.instant().isAfter(a.windowStart().plus(window));
    }

}
