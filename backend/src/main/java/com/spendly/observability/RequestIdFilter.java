package com.spendly.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id that appears in the logs, in the response headers
 * and in the error body.
 *
 * <p>Before this, an unhandled exception was logged as a bare stack trace with
 * no way to tie it to the request that caused it, to the user, or to the error
 * the user saw on screen. A screenshot of a failed request now carries the id
 * that finds the matching log line.
 *
 * <p>Runs at the head of the container's filter chain — ahead of Spring
 * Security — so responses the security chain writes itself (401, 403, 429) are
 * tagged too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /** Bounded so a client cannot push arbitrary volume into every log line. */
    private static final int MAX_SUPPLIED_LENGTH = 64;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Reads the current request's id, or null outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    private static String resolve(String supplied) {
        if (supplied == null || supplied.isBlank() || supplied.length() > MAX_SUPPLIED_LENGTH) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
        // Whatever a proxy passes in ends up in log output, so keep it to
        // characters that cannot break a log line or a JSON string.
        return supplied.replaceAll("[^A-Za-z0-9_.:-]", "");
    }
}
