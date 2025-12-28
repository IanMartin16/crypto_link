package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.security.ApiKeyFilter;
import com.evilink.crypto_link.security.ApiKeyStore;
import com.evilink.crypto_link.security.SseTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
public class MeController {

  private final SseTokenService sseTokenService;

  public MeController(SseTokenService sseTokenService) {
    this.sseTokenService = sseTokenService;
  }

  @GetMapping("/v1/me")
  public Map<String, Object> me(HttpServletRequest req) {
    String apiKey = (String) req.getAttribute(ApiKeyFilter.REQ_ATTR_API_KEY);
    ApiKeyStore.Plan plan = (ApiKeyStore.Plan) req.getAttribute(ApiKeyFilter.REQ_ATTR_PLAN);

    String masked = apiKey == null ? "" :
      apiKey.substring(0, Math.min(4, apiKey.length())) + "..." +
      apiKey.substring(Math.max(0, apiKey.length()-4));

    return Map.of(
      "ok", true,
      "apiKey", masked,
      "plan", plan.name(),
      "limits", Map.of(
        "requestsPerMinute", plan.requestsPerMinute,
        "sseConnections", plan.sseConnections,
        "maxSymbols", plan.maxSymbols
      ),
      "sseTokenTtlSeconds", sseTokenService.getTtlSeconds(),
      "ts", OffsetDateTime.now().toString()
    );
  }
}
