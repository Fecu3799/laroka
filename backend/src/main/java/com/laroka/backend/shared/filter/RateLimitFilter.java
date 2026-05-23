package com.laroka.backend.shared.filter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(-200)
public class RateLimitFilter extends OncePerRequestFilter {

    static final long MENU_LIMIT = 60;
    static final long ORDERS_LIMIT = 10;
    private static final long WINDOW_SECONDS = 60;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String method = request.getMethod();
        String uri = request.getRequestURI();

        Long limit = resolveLimit(method, uri);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }

        String ip = extractClientIp(request);
        String bucketKey = method.toUpperCase() + ":" + normalizeUri(uri) + ":" + ip;
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> createBucket(limit));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded | method={} uri={} ip={}", method, uri, ip);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(WINDOW_SECONDS));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"Límite de requests superado. Reintentar en "
                    + WINDOW_SECONDS + " segundos.\"}");
        }
    }

    Long resolveLimit(String method, String uri) {
        if ("GET".equalsIgnoreCase(method) && uri.matches("/branches/[^/]+/menu")) {
            return MENU_LIMIT;
        }
        if ("POST".equalsIgnoreCase(method) && "/orders".equals(uri)) {
            return ORDERS_LIMIT;
        }
        return null;
    }

    private String normalizeUri(String uri) {
        return uri.replaceAll("/branches/[^/]+/menu", "/branches/\\{id\\}/menu");
    }

    private Bucket createBucket(long limit) {
        Bandwidth bandwidth = Bandwidth.classic(limit, Refill.greedy(limit, Duration.ofSeconds(WINDOW_SECONDS)));
        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
