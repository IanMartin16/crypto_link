package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.service.StripeFulfillmentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

  private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

  @Value("${stripe.webhook-secret}")
  private String webhookSecret;

  private final StripeFulfillmentService fulfill;

  public StripeWebhookController(StripeFulfillmentService fulfill) {
    this.fulfill = fulfill;
  }

  @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> handle(HttpServletRequest request) {

    String sigHeader = request.getHeader("Stripe-Signature");
    String uri = request.getRequestURI();

    String payload;
    try {
      payload = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.error("stripe webhook: failed reading body uri={}", uri, e);
      return ResponseEntity.status(400).body(Map.of("ok", false, "error", "Could not read body"));
    }

    log.warn("? STRIPE WEBHOOK HIT uri={} len={} sigPresent={}",
        uri, payload == null ? -1 : payload.length(), (sigHeader != null && !sigHeader.isBlank()));

    if (sigHeader == null || sigHeader.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Missing Stripe-Signature"));
    }

    final Event event;
    try {
      event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
    } catch (SignatureVerificationException e) {
      log.warn("? STRIPE SIGNATURE INVALID: {}", e.getMessage());
      return ResponseEntity.status(400).body(Map.of("ok", false, "error", "Invalid signature"));
    } catch (Exception e) {
      // OJO: aquí entra cuando el JSON está roto / vacío / no es JSON
      log.error("? STRIPE WEBHOOK BAD PAYLOAD", e);
      return ResponseEntity.status(400).body(Map.of("ok", false, "error", "Bad payload"));
    }

    log.info("stripe webhook received type={} id={}", event.getType(), event.getId());

    // Procesa solo lo que te interesa (tu service ya filtra y devuelve true en otros)
    try {
      boolean ok = fulfill.process(event);
      if (!ok) {
        // 500 => Stripe reintenta
        log.warn("stripe fulfillment returned false eventId={} type={}", event.getId(), event.getType());
        return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Fulfillment returned false"));
      }
      return ResponseEntity.ok(Map.of("ok", true));
    } catch (Exception e) {
      log.error("stripe fulfillment failed eventId={} type={}", event.getId(), event.getType(), e);
      return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Fulfillment failed"));
    }
  }
}
