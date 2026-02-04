package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.persistence.FulfillmentRepository;
import com.evilink.crypto_link.service.SmtpEmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/billing")
public class BillingSupportController {

  private final FulfillmentRepository fulfillRepo;
  private final SmtpEmailService email;

  public BillingSupportController(FulfillmentRepository fulfillRepo, SmtpEmailService email) {
    this.fulfillRepo = fulfillRepo;
    this.email = email;
  }

  public record ResendReq(String email) {}

  @PostMapping("/resend-key")
  public ResponseEntity<Map<String, Object>> resendKey(@RequestBody ResendReq req) {
    if (req == null || req.email() == null || req.email().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Missing email"));
    }

    String e = req.email().trim().toLowerCase();

    var rowOpt = fulfillRepo.findLatestByEmail(e);
    if (rowOpt.isEmpty()) {
      return ResponseEntity.status(404).body(Map.of("ok", false, "error", "No apiKey found for email"));
    }

    var row = rowOpt.get();
    try {
      email.sendApiKey(row.email(), row.plan(), row.apiKey());
      fulfillRepo.markEmailSent(row.email(), row.apiKey());
      return ResponseEntity.ok(Map.of("ok", true, "resent", true));
    } catch (Exception ex) {
      fulfillRepo.markEmailFailed(row.email(), row.apiKey(), safe(ex.getMessage()));
      return ResponseEntity.status(502).body(Map.of("ok", false, "error", "Email provider failed"));
    }
  }

  private static String safe(String s) {
    if (s == null) return null;
    return s.length() > 500 ? s.substring(0, 500) : s;
  }
}
