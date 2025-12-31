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

  public ApiKeyFilter(ApiKeyStore store,
                      RateLimiter limiter,
                      SseTokenService sseTokenService,
                      ApiMetrics metrics) {
    this.store = store;
    this.limiter = limiter;
    this.sseTokenService = sseTokenService;
    this.metrics = metrics;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String p = request.getRequestURI();

    // normaliza contextPath (por si Tomcat/railway mete prefijos)
    String ctx = request.getContextPath();
    if (ctx != null && !ctx.isBlank() && p.startsWith(ctx)) {
      p = p.substring(ctx.length());
    }

    return p.startsWith("/actuator")
      || p.equals("/error")
      || p.startsWith("/admin")
      || p.equals("/v1/ping")
      || p.equals("/v1/symbols")
      || p.equals("/v1/fiats")
      || p.equals("/v1/meta")
      || p.equals("/docs")
      || p.startsWith("/swagger-ui")
      || p.equals("/api-docs")
      || p.startsWith("/api-docs/")
      || p.startsWith("/v3/api-docs")
      || p.startsWith("/v1/billing/links")
      || p.startsWith("/stripe")
      || p.startsWith("/stripe/webhook");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {

    String path = request.getRequestURI();

    // Extra safety (aunque shouldNotFilter ya lo excluye)
    if (path.startsWith("/admin")) {
      filterChain.doFilter(request, response);
      return;
    }

    String apiKey = request.getHeader("x-api-key");
    ApiKeyStore.Plan plan = null;

    // 1) SSE por token si no viene header
    if ((apiKey == null || apiKey.isBlank()) && path.startsWith("/v1/stream/")) {
      String token = request.getParameter("token");
      var ctx = sseTokenService.resolve(token);
      if (ctx != null) {
        apiKey = ctx.apiKey;
        plan = ctx.plan;
      }
    }

    // 2) Si NO venía de token, resuelve plan desde DB
    if (plan == null) {
      var planOpt = store.resolvePlan(apiKey);
      if (planOpt.isEmpty()) {
        metrics.incDenied("invalid_or_missing_key");
        writeJson(request, response, 401, "{\"ok\":false,\"error\":\"Invalid or missing x-api-key\"}");
        log.warn("Denied request: missing/invalid credentials path={}", request.getRequestURI());
        return;
      }
      plan = planOpt.get();
    }

    // 3) Rate limit + headers "pro"
    //    (asume que RateLimiter.check regresa un objeto con allowed/limit/remaining/resetEpochSec/used)
    var d = limiter.check(apiKey, plan.requestsPerMinute);

    response.setHeader("X-Plan", plan.name());
    response.setHeader("X-RateLimit-Limit", String.valueOf(d.limit()));
    response.setHeader("X-RateLimit-Remaining", String.valueOf(d.remaining()));
    response.setHeader("X-RateLimit-Reset", String.valueOf(d.resetEpochSec()));
    response.setHeader("X-RateLimit-Used", String.valueOf(d.used()));

    if (!d.allowed()) {
      long nowSec = System.currentTimeMillis() / 1000;
      long retryAfter = Math.max(1, d.resetEpochSec() - nowSec);
      response.setHeader("Retry-After", String.valueOf(retryAfter));

      metrics.incDenied("rate_limit");
      writeJson(request,response, 429, "{\"ok\":false,\"error\":\"Rate limit exceeded\"}");
      log.warn("Rate limit exceeded path={} plan={} apiKey={}", request.getRequestURI(), plan.name(), safeKey(apiKey));
      return;
    }

    // 4) Deja contexto para controllers
    request.setAttribute(REQ_ATTR_API_KEY, apiKey);
    request.setAttribute(REQ_ATTR_PLAN, plan);

    metrics.incRequest(request.getRequestURI(), plan.name());
    filterChain.doFilter(request, response);
  }

  private void writeJson(HttpServletRequest req, HttpServletResponse res, int status, String error) throws IOException {
  String rid = (String) req.getAttribute("requestId"); // lo setea RequestIdFilter

  // ultra defensivo
  if (rid == null || rid.isBlank()) {
    rid = java.util.UUID.randomUUID().toString();
    req.setAttribute("requestId", rid);
  }

  // siempre reflejarlo en response
   if (res.getHeader("X-Request-Id") == null) {
    res.setHeader("X-Request-Id", rid);
   }

   res.setStatus(status);
   res.setContentType(MediaType.APPLICATION_JSON_VALUE);

   String safeMsg = (error == null) ? "Error" : error.replace("\"", "\\\"");
   res.getWriter().write(("""
     {"ok":false,"error":"%s","requestId":"%s"}
    """).formatted(safeMsg, rid));
}

  // Para no loggear completa la apiKey
  private String safeKey(String apiKey) {
    if (apiKey == null) return "";
    if (apiKey.length() <= 6) return "***";
    return apiKey.substring(0, 3) + "***" + apiKey.substring(apiKey.length() - 3);
  }
}
