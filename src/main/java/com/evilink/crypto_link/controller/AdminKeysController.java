package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.persistence.ApiKeyRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/admin/v1/keys")
public class AdminKeysController {

    private final ApiKeyRepository repo;
    private final SecureRandom rnd = new SecureRandom();

    @Value("${cryptolink.admin.secret:}")
    private String adminSecret;

    public AdminKeysController(ApiKeyRepository repo) {
        this.repo = repo;
    }

    private void requireAdmin(String secret) {
        if (adminSecret == null || adminSecret.isBlank() || secret == null || !adminSecret.equals(secret)) {
          throw new org.springframework.web.server.ResponseStatusException(
              org.springframework.http.HttpStatus.UNAUTHORIZED,
              "Unauthorized"
          );

        }
    }

    private String genKey() {
        byte[] b = new byte[24];
        rnd.nextBytes(b);
        return "cl_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    // CREATE
    @PostMapping
    public Map<String, Object> create(
            @RequestHeader(value = "x-admin-secret", required = false) String secret,
            @RequestParam(defaultValue = "FREE") String plan,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String expiresAtIso
    ) {
        requireAdmin(secret);

        String apiKey = genKey();
        OffsetDateTime expiresAt = (expiresAtIso == null || expiresAtIso.isBlank())
                ? null
                : OffsetDateTime.parse(expiresAtIso);

        int rows = repo.insertKey(apiKey, plan.toUpperCase(), status.toUpperCase(), expiresAt);

        var resp = new LinkedHashMap<String, Object>();
        resp.put("ok", rows == 1);
        resp.put("apiKey", apiKey);
        resp.put("plan", plan.toUpperCase());
        resp.put("status", status.toUpperCase());
        resp.put("expiresAt", expiresAt == null ? null : expiresAt.toString()); // aquí sí puede ir null
        return resp;
    }

    // REVOKE
    @PostMapping("/{apiKey}/revoke")
    public Map<String, Object> revoke(
            @RequestHeader("x-admin-secret") String secret,
            @PathVariable @NotBlank String apiKey
    ) {
        requireAdmin(secret);
        int rows = repo.updateStatus(apiKey, "REVOKED");
        return Map.of("ok", rows == 1);
    }

    // CHANGE PLAN
    @PostMapping("/{apiKey}/plan")
    public Map<String, Object> changePlan(
            @RequestHeader("x-admin-secret") String secret,
            @PathVariable @NotBlank String apiKey,
            @RequestParam String plan
    ) {
        requireAdmin(secret);
        int rows = repo.updatePlan(apiKey, plan.toUpperCase());
        return Map.of("ok", rows == 1, "plan", plan.toUpperCase());
    }

    // SET EXPIRATION
    @PostMapping("/{apiKey}/expires")
    public Map<String, Object> setExpires(
            @RequestHeader("x-admin-secret") String secret,
            @PathVariable @NotBlank String apiKey,
            @RequestParam(required = false) String expiresAtIso
    ) {
        requireAdmin(secret);
        OffsetDateTime exp = (expiresAtIso == null || expiresAtIso.isBlank())
                ? null
                : OffsetDateTime.parse(expiresAtIso);

        int rows = repo.updateExpiresAt(apiKey, exp);
        return Map.of("ok", rows == 1, "expiresAt", exp == null ? null : exp.toString());
    }
}
