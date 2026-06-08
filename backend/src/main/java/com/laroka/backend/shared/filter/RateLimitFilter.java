package com.laroka.backend.shared.filter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(-200)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_SECONDS = 60;

    @Value("${rate.limit.menu.max-requests:60}")
    long menuMaxRequests;

    @Value("${rate.limit.orders.max-requests:10}")
    long ordersMaxRequests;

    @Value("${rate.limit.login.max-requests:10}")
    long loginMaxRequests;

    @Value("${rate.limit.login.window-seconds:60}")
    long loginWindowSeconds;

    private Cache<String, Bucket> buckets;
    private Cache<String, Bucket> loginBuckets;

    @PostConstruct
    void init() {
        buckets = Caffeine.newBuilder()
            .expireAfterAccess(WINDOW_SECONDS + 60, TimeUnit.SECONDS)
            .build();
        loginBuckets = Caffeine.newBuilder()
            .expireAfterAccess(loginWindowSeconds + 60, TimeUnit.SECONDS)
            .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String ip = extractClientIp(request);

        if ("POST".equalsIgnoreCase(method) && "/auth/login".equals(uri)) {
            Bucket bucket = loginBuckets.get(ip, k -> createBucket(loginMaxRequests, loginWindowSeconds));
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                chain.doFilter(request, response);
            } else {
                long retryAfter = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
                log.warn("Login rate limit exceeded | ip={}", ip);
                write429Login(response, retryAfter);
            }
            return;
        }

        Long limit = resolveLimit(method, uri);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }

        String bucketKey = method.toUpperCase() + ":" + normalizeUri(uri) + ":" + ip;
        Bucket bucket = buckets.get(bucketKey, k -> createBucket(limit, WINDOW_SECONDS));

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
            return menuMaxRequests;
        }
        if ("POST".equalsIgnoreCase(method) && "/orders".equals(uri)) {
            return ordersMaxRequests;
        }
        return null;
    }

    private void write429Login(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":429,\"message\":\"Demasiados intentos. Intentá de nuevo en 1 minuto.\"}");
    }

    private String normalizeUri(String uri) {
        return uri.replaceAll("/branches/[^/]+/menu", "/branches/\\{id\\}/menu");
    }

    private Bucket createBucket(long limit, long windowSeconds) {
        Bandwidth bandwidth = Bandwidth.classic(limit, Refill.greedy(limit, Duration.ofSeconds(windowSeconds)));
        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] ips = xff.split(",");
            return ips[ips.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
