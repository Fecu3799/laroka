package com.laroka.backend.shared.filter;

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

    private final RateLimitFilter filter = new RateLimitFilter();

    // --- resolveLimit ---

    @Test
    void resolveLimit_menuGetRequest_returns60() {
        assertThat(filter.resolveLimit("GET", "/branches/1/menu")).isEqualTo(RateLimitFilter.MENU_LIMIT);
        assertThat(filter.resolveLimit("GET", "/branches/abc-def/menu")).isEqualTo(RateLimitFilter.MENU_LIMIT);
        assertThat(filter.resolveLimit("get", "/branches/2/menu")).isEqualTo(RateLimitFilter.MENU_LIMIT);
    }

    @Test
    void resolveLimit_ordersPostRequest_returns10() {
        assertThat(filter.resolveLimit("POST", "/orders")).isEqualTo(RateLimitFilter.ORDERS_LIMIT);
        assertThat(filter.resolveLimit("post", "/orders")).isEqualTo(RateLimitFilter.ORDERS_LIMIT);
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
        RateLimitFilter f = new RateLimitFilter();
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
        RateLimitFilter f = new RateLimitFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < RateLimitFilter.ORDERS_LIMIT; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/orders");
            req.setRemoteAddr("1.2.3.4");
            MockHttpServletResponse res = new MockHttpServletResponse();
            f.doFilter(req, res, chain);
            assertThat(res.getStatus()).isNotEqualTo(429);
        }

        verify(chain, times((int) RateLimitFilter.ORDERS_LIMIT)).doFilter(any(), any());
    }

    @Test
    void doFilter_ordersEndpoint_returns429WithRetryAfterHeaderAfterLimit() throws Exception {
        RateLimitFilter f = new RateLimitFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < RateLimitFilter.ORDERS_LIMIT; i++) {
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
        verify(chain, times((int) RateLimitFilter.ORDERS_LIMIT)).doFilter(any(), any());
    }

    // --- doFilter: GET /branches/{id}/menu ---

    @Test
    void doFilter_menuEndpoint_returns429WithRetryAfterHeaderAfterLimit() throws Exception {
        RateLimitFilter f = new RateLimitFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < RateLimitFilter.MENU_LIMIT; i++) {
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
        RateLimitFilter f = new RateLimitFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < RateLimitFilter.ORDERS_LIMIT; i++) {
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

    // --- doFilter: X-Forwarded-For ---

    @Test
    void doFilter_xForwardedFor_usesFirstIpForRateLimiting() throws Exception {
        RateLimitFilter f = new RateLimitFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < RateLimitFilter.ORDERS_LIMIT; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/orders");
            req.addHeader("X-Forwarded-For", "5.6.7.8, 192.168.1.1");
            f.doFilter(req, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/orders");
        req.addHeader("X-Forwarded-For", "5.6.7.8, 10.0.0.99");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
    }
}
