package com.pageturn.common.config;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.pageturn.common.web.RequestPaths;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limits how many API requests one IP address may make per minute (429 Too Many Requests after that).
 * Sign-in and sign-up have a much lower limit, to slow down password guessing and fake sign-ups.
 *
 * <p>Counters live in memory: simple and fast, but per server instance and reset on restart. If the app
 * ever runs on several servers, move this to a shared store (e.g. Redis) or the load balancer.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final FixedWindowLimiter authLimiter;
    private final FixedWindowLimiter apiLimiter;

    public RateLimitFilter(@Value("${pageturn.rate-limit.auth-per-minute:10}") int authPerMinute,
            @Value("${pageturn.rate-limit.api-per-minute:300}") int apiPerMinute) {
        this.authLimiter = new FixedWindowLimiter(authPerMinute, WINDOW, Clock.systemUTC());
        this.apiLimiter = new FixedWindowLimiter(apiPerMinute, WINDOW, Clock.systemUTC());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !path(request).startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = request.getRemoteAddr();
        String path = path(request);
        boolean isAuthAttempt = "POST".equals(request.getMethod())
                && (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register"));

        FixedWindowLimiter limiter = isAuthAttempt ? authLimiter : apiLimiter;
        long retryAfter = limiter.tryAcquire(client);
        if (retryAfter > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType("application/problem+json");
            response.getWriter().write("""
                    {"type":"about:blank","title":"Too Many Requests","status":429,"detail":"Too many requests. Please wait a minute and try again."}""");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String path(HttpServletRequest request) {
        return RequestPaths.of(request);
    }

    /** Counts requests per key in fixed one-minute windows. */
    static final class FixedWindowLimiter {

        private record Window(Instant start, int count) {
        }

        private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
        private final int limit;
        private final Duration length;
        private final Clock clock;

        FixedWindowLimiter(int limit, Duration length, Clock clock) {
            this.limit = limit;
            this.length = length;
            this.clock = clock;
        }

        /** @return 0 if the request is allowed, otherwise the number of seconds to wait. */
        long tryAcquire(String key) {
            Instant now = clock.instant();
            if (windows.size() > MAX_TRACKED_CLIENTS) {
                windows.values().removeIf(w -> !now.isBefore(w.start().plus(length)));
            }
            Window window = windows.compute(key, (k, current) ->
                    current == null || !now.isBefore(current.start().plus(length))
                            ? new Window(now, 1)
                            : new Window(current.start(), current.count() + 1));
            if (window.count() <= limit) {
                return 0;
            }
            return Math.max(1, Duration.between(now, window.start().plus(length)).toSeconds());
        }
    }
}
