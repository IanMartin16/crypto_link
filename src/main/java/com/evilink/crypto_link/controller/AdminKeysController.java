package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.persistence.ApiKeyRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/v1/keys")
public class AdminKeysController {

    private final ApiKeyRepository repo;
    private final SecureRandom rnd = new SecureRandom();

    @Value("${cryptolink.admin.secret:}")
    private String adminSecret;

    @Value("${cryptolink.master.admin.key:}")
    private String masterAdminKey;

    public AdminKeysController(ApiKeyRepository repo) {
        this.repo = repo;
    }

    private void requireAdmin(String secret, String master) {
        if (adminSecret == null || adminSecret.isBlank()
                || masterAdminKey == null || masterAdminKey.isBlank()
                || secret == null || !adminSecret.equals(secret)
                || master == null || !masterAdminKey.equals(master)) {

            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
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
            @RequestHeader(value = "x-master-admin", required = false) String master,
            @RequestParam(defaultValue = "FREE") String plan,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String expiresAtIso
    ) {
        requireAdmin(secret, master);

        String apiKey = genKey();
        OffsetDateTime expiresAt = (expiresAtIso == null || expiresAtIso.isBlank())
                ? null
                : OffsetDateTime.parse(expiresAtIso);

        int rows = repo.insertKey(apiKey, plan.toUpperCase(), status.toUpperCase(), expiresAt);

        Map<String, Object> out = new java.util.HashMap<>();
            out.put("ok", rows == 1);
            out.put("apiKey", apiKey);
            out.put("plan", plan.toUpperCase());
            out.put("status", status.toUpperCase());
            if (expiresAt != null) out.put("expiresAt", expiresAt.toString());
            return out;
    }

    // REVOKE
    @PostMapping("/{apiKey}/revoke")
    public Map<String, Object> revoke(
            @RequestHeader(value = "x-admin-secret", required = false) String secret,
            @RequestHeader(value = "x-master-admin", required = false) String master,
            @PathVariable @NotBlank String apiKey
    ) {
        requireAdmin(secret, master);
        int rows = repo.updateStatus(apiKey, "REVOKED");
        return Map.of("ok", rows == 1);
    }

    // CHANGE PLAN
    @PostMapping("/{apiKey}/plan")
    public Map<String, Object> changePlan(
            @RequestHeader(value = "x-admin-secret", required = false) String secret,
            @RequestHeader(value = "x-master-admin", required = false) String master,
            @PathVariable @NotBlank String apiKey,
            @RequestParam String plan
    ) {
        requireAdmin(secret, master);
        int rows = repo.updatePlan(apiKey, plan.toUpperCase());
        return Map.of("ok", rows == 1, "plan", plan.toUpperCase());
    }

    // SET EXPIRATION
    @PostMapping("/{apiKey}/expires")
    public Map<String, Object> setExpires(
            @RequestHeader(value = "x-admin-secret", required = false) String secret,
            @RequestHeader(value = "x-master-admin", required = false) String master,
            @PathVariable @NotBlank String apiKey,
            @RequestParam(required = false) String expiresAtIso
    ) {
        requireAdmin(secret, master);

        OffsetDateTime exp = (expiresAtIso == null || expiresAtIso.isBlank())
                ? null
                : OffsetDateTime.parse(expiresAtIso);

        int rows = repo.updateExpiresAt(apiKey, exp);
        return Map.of("ok", rows == 1, "expiresAt", exp == null ? null : exp.toString());
    }
}
