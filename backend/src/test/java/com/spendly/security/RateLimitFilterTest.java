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
        RateLimitFilter filter = new RateLimitFilter(true, 3, 30);

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
        RateLimitFilter filter = new RateLimitFilter(true, 1, 30);

        assertThat(perform(filter, "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(perform(filter, "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(429);
        assertThat(perform(filter, "/api/auth/login", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresUnrelatedEndpoints() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1);

        for (int i = 0; i < 5; i++) {
            assertThat(perform(filter, "/api/expenses", "1.2.3.4").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void usesFirstForwardedAddressBehindProxy() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 30);

        MockHttpServletRequest first = request("/api/auth/login", "10.0.0.1");
        first.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, new MockFilterChain());
        assertThat(firstResponse.getStatus()).isEqualTo(200);

        MockHttpServletRequest second = request("/api/auth/login", "10.0.0.2");
        second.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.2");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, new MockFilterChain());
        assertThat(secondResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void disabledFilterSkipsEverything() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(false, 1, 1);

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

    private MockHttpServletRequest request(String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr(ip);
        return request;
    }
}
