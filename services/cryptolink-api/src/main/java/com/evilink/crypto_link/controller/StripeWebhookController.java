package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.service.StripeFulfillmentService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

  private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

  // tolerancia típica Stripe: 5 min
  private static final long SIGNATURE_TOLERANCE_SECONDS = 300;

  @Value("${stripe.webhook-secret:}")
  private String webhookSecret;

  private final StripeFulfillmentService fulfill;

  public StripeWebhookController(StripeFulfillmentService fulfill) {
    this.fulfill = fulfill;
  }

  @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> handle(HttpServletRequest request) {

    String sigHeader = request.getHeader("Stripe-Signature");
    String uri = request.getRequestURI();

    final String payload;
    try {
      payload = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.error("stripe webhook: failed reading body uri={}", uri, e);
      return ResponseEntity.status(400).body(Map.of("ok", false, "error", "Could not read body"));
    }

    log.warn("? STRIPE WEBHOOK HIT uri={} len={} sigPresent={}",
        uri, payload == null ? -1 : payload.length(), (sigHeader != null && !sigHeader.isBlank()));

    if (payload == null || payload.isBlank()) {
      return ResponseEntity.status(400).body(Map.of("ok", false, "error", "Empty payload"));
    }

    if (sigHeader == null || sigHeader.isBlank()) {
      log.warn("stripe webhook missing Stripe-Signature header");
      return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Missing Stripe-Signature"));
    }

    if (webhookSecret == null || webhookSecret.isBlank()) {
      log.error("stripe webhook secret not configured");
      return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Webhook secret not configured"));
    }

    // 1) Verify signature WITHOUT parsing Stripe Event
    try {
      verifyStripeSignature(payload, sigHeader, webhookSecret, SIGNATURE_TOLERANCE_SECONDS);
    } catch (Exception e) {
      log.warn("? STRIPE SIGNATURE INVALID: {}", e.getMessage());
      return ResponseEntity.status(400).body(Map.of("ok", false, "error", "Invalid signature"));
    }

    // 2) Minimal JSON parse (type/id/sessionId)
    final JsonObject root;
    try {
      root = JsonParser.parseString(payload).getAsJsonObject();
    } catch (Exception e) {
      log.error("? STRIPE WEBHOOK BAD PAYLOAD (json parse)", e);
      return ResponseEntity.status(400).body(Map.of("ok", false, "error", "Bad payload"));
    }

    String eventType = getAsString(root, "type");
    String eventId   = getAsString(root, "id");

    if (eventType == null || eventId == null) {
      log.error("? STRIPE WEBHOOK BAD PAYLOAD (missing type/id)");
      return ResponseEntity.status(400).body(Map.of("ok", false, "error", "Bad payload"));
    }

    log.info("stripe webhook received type={} id={}", eventType, eventId);

    // 3) We only care about checkout.session.completed
    if (!"checkout.session.completed".equals(eventType)) {
      return ResponseEntity.ok(Map.of("ok", true, "ignored", true, "type", eventType, "eventId", eventId));
    }

    String sessionId = extractCheckoutSessionId(root);
    if (sessionId == null || sessionId.isBlank()) {
      log.warn("stripe webhook: checkout.session.completed missing sessionId eventId={}", eventId);
      // 500 para que Stripe reintente (a veces llega incompleto/bug de forwarding)
      return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Missing sessionId"));
    }

    // 4) Fulfillment
    try {
      boolean ok = fulfill.processCheckoutCompleted(eventId, sessionId);

      if (!ok) {
        log.warn("stripe fulfillment returned false eventId={} sessionId={}", eventId, sessionId);
        return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Fulfillment returned false"));
      }

      return ResponseEntity.ok(Map.of("ok", true, "processed", true, "eventId", eventId, "sessionId", sessionId));
    } catch (Exception e) {
      log.error("stripe fulfillment failed eventId={} sessionId={}", eventId, sessionId, e);
      return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Fulfillment failed"));
    }
  }

  private static String extractCheckoutSessionId(JsonObject root) {
    try {
      JsonObject data = root.getAsJsonObject("data");
      if (data == null) return null;
      JsonObject obj = data.getAsJsonObject("object");
      if (obj == null) return null;
      return obj.has("id") && !obj.get("id").isJsonNull() ? obj.get("id").getAsString() : null;
    } catch (Exception e) {
      return null;
    }
  }

  private static String getAsString(JsonObject obj, String key) {
    if (obj == null || key == null) return null;
    if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
    try {
      return obj.get(key).getAsString();
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Manual Stripe signature verification (HMAC-SHA256), avoids Stripe Event parsing.
   * Header format: "t=timestamp,v1=signature[,v1=signature2...]"
   */
  private static void verifyStripeSignature(String payload, String sigHeader, String secret, long toleranceSeconds) throws Exception {
    long timestamp = -1;
    List<String> signatures = new ArrayList<>();

    String[] parts = sigHeader.split(",");
    for (String p : parts) {
      String s = p.trim();
      if (s.startsWith("t=")) {
        timestamp = Long.parseLong(s.substring(2));
      } else if (s.startsWith("v1=")) {
        signatures.add(s.substring(3));
      }
    }

    if (timestamp <= 0 || signatures.isEmpty()) {
      throw new IllegalArgumentException("Invalid Stripe-Signature header format");
    }

    long now = Instant.now().getEpochSecond();
    long diff = Math.abs(now - timestamp);
    if (diff > toleranceSeconds) {
      throw new IllegalArgumentException("Timestamp outside tolerance: diff=" + diff + "s");
    }

    String signedPayload = timestamp + "." + payload;
    String expected = hmacSha256Hex(secret, signedPayload);

    boolean match = false;
    for (String sig : signatures) {
      if (constantTimeEqualsHex(expected, sig)) {
        match = true;
        break;
      }
    }

    if (!match) {
      throw new IllegalArgumentException("No signatures found matching the expected signature for payload");
    }
  }

  private static String hmacSha256Hex(String secret, String data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return toHex(raw);
  }

  private static String toHex(byte[] bytes) {
    char[] hexArray = "0123456789abcdef".toCharArray();
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = hexArray[v >>> 4];
      hexChars[j * 2 + 1] = hexArray[v & 0x0F];
    }
    return new String(hexChars);
  }

  private static boolean constantTimeEqualsHex(String a, String b) {
    if (a == null || b == null) return false;
    byte[] ba = a.getBytes(StandardCharsets.UTF_8);
    byte[] bb = b.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(ba, bb);
  }
}
