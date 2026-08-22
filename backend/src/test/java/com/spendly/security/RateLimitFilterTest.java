package com.spendly.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    @Test
    void blocksAuthRequestsOverTheLimit() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(true, 3, 30, false);

        for (int i = 0; i < 3; i++) {
            assertThat(perform(filter, "/api/auth/login", "1.2.3.4").getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = perform(filter, "/api/auth/login", "1.2.3.4");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(blocked.getContentAsString()).contains("Too many requests");
    }

    @Test
    void limitsAreTrackedPerIp() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 30, false);

        assertThat(perform(filter, "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(perform(filter, "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(429);
        assertThat(perform(filter, "/api/auth/login", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresUnrelatedEndpoints() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, false);

        for (int i = 0; i < 5; i++) {
            assertThat(perform(filter, "/api/expenses", "1.2.3.4").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void usesFirstForwardedAddressBehindProxy() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 30, true);

        assertThat(performForwarded(filter, "10.0.0.1", "203.0.113.7, 10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(performForwarded(filter, "10.0.0.2", "203.0.113.7, 10.0.0.2").getStatus()).isEqualTo(429);
    }

    /**
     * Without a trusted proxy in front, X-Forwarded-For is just something the
     * caller typed. Honouring it would let one client rotate the header and get
     * an unlimited number of login attempts.
     */
    @Test
    void ignoresForwardedHeaderWhenNotBehindProxy() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 30, false);

        assertThat(performForwarded(filter, "1.2.3.4", "203.0.113.1").getStatus()).isEqualTo(200);
        assertThat(performForwarded(filter, "1.2.3.4", "203.0.113.2").getStatus()).isEqualTo(429);
    }

    @Test
    void disabledFilterSkipsEverything() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(false, 1, 1, false);

        for (int i = 0; i < 5; i++) {
            assertThat(perform(filter, "/api/auth/login", "1.2.3.4").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse perform(RateLimitFilter filter, String uri, String ip)
            throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(uri, ip), response, new MockFilterChain());
        return response;
    }

    private MockHttpServletResponse performForwarded(RateLimitFilter filter, String ip, String forwardedFor)
            throws ServletException, IOException {
        MockHttpServletRequest request = request("/api/auth/login", ip);
        request.addHeader("X-Forwarded-For", forwardedFor);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletRequest request(String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr(ip);
        return request;
    }
}
