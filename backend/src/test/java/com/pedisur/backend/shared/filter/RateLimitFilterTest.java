package com.pedisur.backend.shared.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private RateLimitFilter newFilter() {
        RateLimitFilter f = new RateLimitFilter();
        f.menuMaxRequests = 60;
        f.ordersMaxRequests = 10;
        f.loginMaxRequests = 10;
        f.loginWindowSeconds = 60;
        f.init();
        return f;
    }

    private final RateLimitFilter filter = newFilter();

    // --- resolveLimit ---

    @Test
    void resolveLimit_menuGetRequest_returnsMenuLimit() {
        assertThat(filter.resolveLimit("GET", "/branches/1/menu")).isEqualTo(filter.menuMaxRequests);
        assertThat(filter.resolveLimit("GET", "/branches/abc-def/menu")).isEqualTo(filter.menuMaxRequests);
        assertThat(filter.resolveLimit("get", "/branches/2/menu")).isEqualTo(filter.menuMaxRequests);
    }

    @Test
    void resolveLimit_ordersPostRequest_returnsOrdersLimit() {
        assertThat(filter.resolveLimit("POST", "/orders")).isEqualTo(filter.ordersMaxRequests);
        assertThat(filter.resolveLimit("post", "/orders")).isEqualTo(filter.ordersMaxRequests);
    }

    @Test
    void resolveLimit_otherEndpoints_returnsNull() {
        assertThat(filter.resolveLimit("GET", "/branches")).isNull();
        assertThat(filter.resolveLimit("GET", "/orders/abc/status")).isNull();
        assertThat(filter.resolveLimit("POST", "/auth/login")).isNull();
        assertThat(filter.resolveLimit("POST", "/payments/webhook")).isNull();
        assertThat(filter.resolveLimit("GET", "/orders")).isNull();
    }

    // --- doFilter: non-rate-limited endpoint ---

    @Test
    void doFilter_nonRateLimitedEndpoint_alwaysPassesThrough() throws Exception {
        RateLimitFilter f = newFilter();
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/branches");
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isNotEqualTo(429);
        verify(chain).doFilter(any(), any());
    }

    // --- doFilter: POST /orders ---

    @Test
    void doFilter_ordersEndpoint_allowsExactlyUpToLimit() throws Exception {
        RateLimitFilter f = newFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < f.ordersMaxRequests; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/orders");
            req.setRemoteAddr("1.2.3.4");
            MockHttpServletResponse res = new MockHttpServletResponse();
            f.doFilter(req, res, chain);
            assertThat(res.getStatus()).isNotEqualTo(429);
        }

        verify(chain, times((int) f.ordersMaxRequests)).doFilter(any(), any());
    }

    @Test
    void doFilter_ordersEndpoint_returns429WithRetryAfterHeaderAfterLimit() throws Exception {
        RateLimitFilter f = newFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < f.ordersMaxRequests; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/orders");
            req.setRemoteAddr("2.3.4.5");
            f.doFilter(req, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/orders");
        req.setRemoteAddr("2.3.4.5");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("60");
        verify(chain, times((int) f.ordersMaxRequests)).doFilter(any(), any());
    }

    // --- doFilter: GET /branches/{id}/menu ---

    @Test
    void doFilter_menuEndpoint_returns429WithRetryAfterHeaderAfterLimit() throws Exception {
        RateLimitFilter f = newFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < f.menuMaxRequests; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/branches/1/menu");
            req.setRemoteAddr("3.4.5.6");
            f.doFilter(req, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/branches/1/menu");
        req.setRemoteAddr("3.4.5.6");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("60");
    }

    // --- doFilter: per-IP isolation ---

    @Test
    void doFilter_differentIps_haveSeparateBuckets() throws Exception {
        RateLimitFilter f = newFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < f.ordersMaxRequests; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/orders");
            req.setRemoteAddr("10.0.0.1");
            f.doFilter(req, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/orders");
        req.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isNotEqualTo(429);
    }

    // --- doFilter: POST /auth/login ---

    @Test
    void doFilter_loginEndpoint_tenthAttemptPasses() throws Exception {
        RateLimitFilter f = newFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 1; i <= 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
            req.setRemoteAddr("9.9.9.9");
            MockHttpServletResponse res = new MockHttpServletResponse();
            f.doFilter(req, res, chain);
            assertThat(res.getStatus()).as("Attempt %d should pass", i).isNotEqualTo(429);
        }

        verify(chain, times(10)).doFilter(any(), any());
    }

    @Test
    void doFilter_loginEndpoint_eleventhAttemptReturns429() throws Exception {
        RateLimitFilter f = newFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
            req.setRemoteAddr("8.8.8.8");
            f.doFilter(req, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
        req.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isNotNull();
        assertThat(res.getContentAsString()).contains("\"status\":429");
        assertThat(res.getContentAsString()).contains("Demasiados intentos");
        verify(chain, times(10)).doFilter(any(), any());
    }

    // --- doFilter: X-Forwarded-For ---

    @Test
    void doFilter_xForwardedFor_usesLastIpForRateLimiting() throws Exception {
        RateLimitFilter f = newFilter();
        FilterChain chain = mock(FilterChain.class);

        // Exhaust bucket for 192.168.1.1 (last IP in header)
        for (int i = 0; i < f.ordersMaxRequests; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/orders");
            req.addHeader("X-Forwarded-For", "5.6.7.8, 192.168.1.1");
            f.doFilter(req, new MockHttpServletResponse(), chain);
        }

        // Same last IP → 429
        MockHttpServletRequest blocked = new MockHttpServletRequest("POST", "/orders");
        blocked.addHeader("X-Forwarded-For", "5.6.7.8, 192.168.1.1");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        f.doFilter(blocked, blockedRes, chain);
        assertThat(blockedRes.getStatus()).isEqualTo(429);

        // Different last IP → passes (proves last IP is used, not first)
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/orders");
        other.addHeader("X-Forwarded-For", "5.6.7.8, 10.0.0.99");
        MockHttpServletResponse otherRes = new MockHttpServletResponse();
        f.doFilter(other, otherRes, chain);
        assertThat(otherRes.getStatus()).isNotEqualTo(429);
    }
}
