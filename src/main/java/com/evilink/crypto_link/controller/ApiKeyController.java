package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.persistence.ApiKeyRepository;
import com.evilink.crypto_link.persistence.FulfillmentRepository;
import com.evilink.crypto_link.service.SmtpEmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/v1/apikeys")
public class ApiKeyController {

  private final ApiKeyRepository apiKeys;
  private final FulfillmentRepository fulfillRepo;
  private final SmtpEmailService email;

  private final SecureRandom rnd = new SecureRandom();

  public ApiKeyController(ApiKeyRepository apiKeys, FulfillmentRepository fulfillRepo, SmtpEmailService email) {
    this.apiKeys = apiKeys;
    this.fulfillRepo = fulfillRepo;
    this.email = email;
  }

  @PostMapping("/rotate")
  @Transactional
  public ResponseEntity<Map<String, Object>> rotate(@RequestHeader(name = "X-Api-Key", required = false) String currentKey) {
    if (currentKey == null || currentKey.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Missing X-Api-Key"));
    }

    String oldKey = currentKey.trim();
    var rowOpt = apiKeys.findActiveByKey(oldKey);

    if (rowOpt.isEmpty()) {
      return ResponseEntity.status(401).body(Map.of("ok", false, "error", "Invalid API key"));
    }

    String plan = rowOpt.get().plan();
    String newKey = genKey();

    // 1) Crear nueva
    apiKeys.insertKey(newKey, plan, "ACTIVE", null);

    // 2) Desactivar anterior
    apiKeys.deactivate(oldKey);

    // 3) Auditoría + (opcional) email si sabemos el correo
    var f = fulfillRepo.findByApiKey(oldKey);
    if (f.isPresent()) {
      String emailTo = f.get().email();
      fulfillRepo.insert(emailTo, plan, newKey, "rotate", null, null);

      try {
        email.sendApiKey(emailTo, plan, newKey);
        fulfillRepo.markEmailSent(emailTo, newKey);
      } catch (Exception ex) {
        fulfillRepo.markEmailFailed(emailTo, newKey, safe(ex.getMessage()));
      }
    }

    return ResponseEntity.ok(Map.of(
        "ok", true,
        "rotated", true,
        "plan", plan,
        "apiKey", newKey // <-- solo se muestra una vez (tu cliente lo debe guardar)
    ));
  }

  private String genKey() {
    byte[] b = new byte[24];
    rnd.nextBytes(b);
    return "cl_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  private static String safe(String s) {
    if (s == null) return null;
    return s.length() > 500 ? s.substring(0, 500) : s;
  }
}
