package com.spendly.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window, per-IP rate limiter for abuse-prone endpoints: login/register
 * (credential brute-forcing) and the AI suggestion endpoint (external API quota).
 * In-memory on purpose — the app runs as a single instance, same reasoning as
 * choosing Caffeine over Redis for caching.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final boolean enabled;
    private final int authLimitPerMinute;
    private final int aiLimitPerMinute;
    private final boolean behindProxy;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${spendly.rate-limit.enabled:true}") boolean enabled,
            @Value("${spendly.rate-limit.auth-per-minute:10}") int authLimitPerMinute,
            @Value("${spendly.rate-limit.ai-per-minute:30}") int aiLimitPerMinute,
            @Value("${spendly.rate-limit.behind-proxy:false}") boolean behindProxy
    ) {
        this.enabled = enabled;
        this.authLimitPerMinute = authLimitPerMinute;
        this.aiLimitPerMinute = aiLimitPerMinute;
        this.behindProxy = behindProxy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || limitFor(request.getRequestURI()) == 0;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        int limit = limitFor(request.getRequestURI());
        String key = clientIp(request) + '|' + request.getRequestURI();

        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, w) ->
                w == null || now - w.startMillis >= WINDOW_MILLIS ? new Window(now) : w);

        if (window.count.incrementAndGet() > limit) {
            long retryAfterSeconds = Math.max(1, (window.startMillis + WINDOW_MILLIS - now) / 1000);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            RestSecurityErrorHandler.write(response, 429, "Too Many Requests",
                    "Too many requests, try again in " + retryAfterSeconds + "s");
            return;
        }

        cleanUpIfNeeded(now);
        filterChain.doFilter(request, response);
    }

    private int limitFor(String uri) {
        if (uri.startsWith("/api/auth/")) {
            return authLimitPerMinute;
        }
        if (uri.equals("/api/expenses/suggest-category")) {
            return aiLimitPerMinute;
        }
        return 0;
    }

    /**
     * X-Forwarded-For is set by the client and only becomes trustworthy once a
     * proxy we control has overwritten it. Honouring it unconditionally let an
     * attacker send a different value per request and get a fresh bucket every
     * time, which defeats the point of limiting login attempts. So it is read
     * only when the deployment says there is a proxy in front (behind-proxy is
     * true on Render, false locally and in tests).
     */
    private String clientIp(HttpServletRequest request) {
        if (behindProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void cleanUpIfNeeded(long now) {
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMillis >= WINDOW_MILLIS);
        }
    }

    private static final class Window {
        final long startMillis;
        final AtomicInteger count = new AtomicInteger();

        Window(long startMillis) {
            this.startMillis = startMillis;
        }
    }
}
