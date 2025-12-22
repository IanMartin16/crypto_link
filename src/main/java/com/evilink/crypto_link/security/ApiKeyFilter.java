package com.evilink.crypto_link.security;

import com.evilink.crypto_link.metrics.ApiMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String REQ_ATTR_API_KEY = "cryptolink.apiKey";
    public static final String REQ_ATTR_PLAN = "cryptolink.plan";

    private final ApiKeyStore store;
    private final RateLimiter limiter;
    private final SseTokenService sseTokenService;
    private final ApiMetrics metrics;

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    public ApiKeyFilter(ApiKeyStore store, RateLimiter limiter, SseTokenService sseTokenService, ApiMetrics metrics) {
        this.store = store;
        this.limiter = limiter;
        this.sseTokenService = sseTokenService;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();

        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isBlank() && p.startsWith(ctx)) {
             p = p.substring(ctx.length());
        }

        return p.startsWith("/actuator")
                || p.equals("/error")
                || p.startsWith("/admin")          // ya con path normalizado
                || p.equals("/v1/ping")
                || p.equals("/v1/symbols")
                || p.equals("/v1/fiats")
                || p.equals("/v1/meta")
                || p.equals("/docs")
                || p.startsWith("/swagger-ui")
                || p.equals("/api-docs")
                || p.startsWith("/api-docs/")
                || p.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith("/admin")) {
           filterChain.doFilter(request, response);
           return;
        }

        String apiKey = request.getHeader("x-api-key");
        ApiKeyStore.Plan plan = null;

        // 1) Si es SSE y no hay header, intenta token query param
        if ((apiKey == null || apiKey.isBlank()) && path.startsWith("/v1/stream/")) {
            String token = request.getParameter("token");
            var ctx = sseTokenService.resolve(token);
            if (ctx != null) {
                apiKey = ctx.apiKey;
                plan = ctx.plan;
            }
        }

        // 2) Si NO venía de token, resuelve plan por apiKey normal en DB
        if (plan == null) {
            var planOpt = store.resolvePlan(apiKey);
            if (planOpt.isEmpty()) {
                metrics.incDenied("invalid_or_missing_key");
                writeJson(response, 401, "{\"ok\":false,\"error\":\"Invalid or missing x-api-key\"}");
                log.warn("Denied request: missing/invalid credentials path={}", request.getRequestURI());
                return;
            }
            plan = planOpt.get();
        }

        // 3) Rate limit
        boolean allowed = limiter.allow(apiKey, plan.requestsPerMinute);
        if (!allowed) {
            metrics.incDenied("rate_limit");
            writeJson(response, 429, "{\"ok\":false,\"error\":\"Rate limit exceeded\"}");
            log.warn("Rate limit exceeded path={} plan={}", request.getRequestURI(), plan.name());
            return;
        }

        request.setAttribute(REQ_ATTR_API_KEY, apiKey);
        request.setAttribute(REQ_ATTR_PLAN, plan);

        metrics.incRequest(request.getRequestURI(), plan.name());
        filterChain.doFilter(request, response);
    }

    private void writeJson(HttpServletResponse res, int status, String body) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(body);
    }
}
