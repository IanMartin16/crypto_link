package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.security.ApiKeyFilter;
import com.evilink.crypto_link.security.ApiKeyStore;
import com.evilink.crypto_link.security.SseTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    private final SseTokenService tokenService;

    public AuthController(SseTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/v1/auth/sse-token")
    public Map<String, Object> sseToken(HttpServletRequest req) {
        String apiKey = (String) req.getAttribute(ApiKeyFilter.REQ_ATTR_API_KEY);
        ApiKeyStore.Plan plan = (ApiKeyStore.Plan) req.getAttribute(ApiKeyFilter.REQ_ATTR_PLAN);

        String token = tokenService.mint(apiKey, plan);

        return Map.of(
                "ok", true,
                "token", token,
                "expiresInSeconds", tokenService.getTtlSeconds()
        );
    }
}
