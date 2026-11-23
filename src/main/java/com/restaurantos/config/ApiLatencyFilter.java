package com.restaurantos.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Low-overhead request timing to identify slow API endpoints.
 * Never logs query strings, request bodies, credentials or authorization headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiLatencyFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ApiLatencyFilter.class);

    @Value("${restaurantos.performance.slow-request-ms:750}")
    private long slowRequestMs;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (!response.isCommitted()) {
                response.setHeader("Server-Timing", "app;dur=" + elapsedMs);
            }
            if (elapsedMs >= slowRequestMs) {
                // Avoid logging raw paths: identifiers in URL segments may be sensitive.
                log.warn("Slow API request method={} status={} durationMs={}",
                        request.getMethod(), response.getStatus(), elapsedMs);
            }
        }
    }
}
