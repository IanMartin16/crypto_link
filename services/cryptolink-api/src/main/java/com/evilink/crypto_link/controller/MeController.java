package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.security.ApiKeyFilter;
import com.evilink.crypto_link.security.ApiKeyStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
public class MeController {

  @Value("${cryptolink.sse.token-ttl-seconds:180}")
  private int sseTokenTtlSeconds;

  @SecurityRequirement(name = "apiKeyAuth")
  @Operation(
    summary = "Plan y límites del API key actual",
    description = "Devuelve el plan activo y límites efectivos (rpm, sseConnections, maxSymbols)."
  )
  @ApiResponses({
    @ApiResponse(
      responseCode = "200",
      description = "OK",
      content = @Content(
        mediaType = "application/json",
        examples = @ExampleObject(value = """
          {
            "ok": true,
            "requestId": "1ef2203f-5fff-450a-af46-6f21e802130c",
            "apiKey": "cl_r...GteR",
            "plan": "FREE",
            "limits": {
              "requestsPerMinute": 60,
              "sseConnections": 1,
              "maxSymbols": 2
            },
            "sseTokenTtlSeconds": 180,
            "ts": "2025-12-28T20:30:42Z"
          }
        """)
      )
    ),
    @ApiResponse(responseCode = "401", description = "Invalid or missing x-api-key")
  })
  @GetMapping("/v1/me")
  public Map<String, Object> me(HttpServletRequest req) {

    String apiKey = (String) req.getAttribute(ApiKeyFilter.REQ_ATTR_API_KEY);
    ApiKeyStore.Plan plan = (ApiKeyStore.Plan) req.getAttribute(ApiKeyFilter.REQ_ATTR_PLAN);

    String requestId = safeRequestId(req);

    return Map.of(
      "ok", true,
      "requestId", requestId,
      "apiKey", mask(apiKey),
      "plan", plan != null ? plan.name() : "UNKNOWN",
      "limits", Map.of(
        "requestsPerMinute", plan != null ? plan.requestsPerMinute : 0,
        "sseConnections", plan != null ? plan.sseConnections : 0,
        "maxSymbols", plan != null ? plan.maxSymbols : 0
      ),
      "sseTokenTtlSeconds", sseTokenTtlSeconds,
      "ts", OffsetDateTime.now().toString()
    );
  }

  private String safeRequestId(HttpServletRequest req) {
    Object ridObj = req.getAttribute("requestId");
    String rid = (ridObj == null) ? "" : String.valueOf(ridObj);
    if (rid.isBlank() || "null".equalsIgnoreCase(rid)) {
      rid = UUID.randomUUID().toString();
      req.setAttribute("requestId", rid);
    }
    return rid;
  }

  private String mask(String k) {
    if (k == null || k.isBlank()) return "";
    if (k.length() <= 8) return "***";
    return k.substring(0, 4) + "..." + k.substring(k.length() - 4);
  }
}
