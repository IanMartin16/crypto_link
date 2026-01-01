package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.service.StripeFulfillmentService;
import com.stripe.exception.SignatureVerificationException;
import jakarta.servlet.http.HttpServletRequest;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

  @PostMapping(value="/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String,Object>> handle(
      @RequestBody String payload,
      @RequestHeader(name="Stripe-Signature", required=false) String sigHeader
  ) {

    if (sigHeader == null || sigHeader.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "Missing Stripe-Signature"));
    }
    log.warn("🔥 STRIPE WEBHOOK HIT uri={} len={} sigPresent={}",
        req.getRequestURI(),
        payload != null ? payload.length() : -1,
        req.getHeader("Stripe-Signature") != null
      );

    final Event event;
    try {
      event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
    } catch (SignatureVerificationException e) {
        log.warn("❌ STRIPE SIGNATURE INVALID: {}", e.getMessage());
      return ResponseEntity.status(400).body(Map.of("ok", false, "error", "Invalid signature"));
    } catch (Exception e) {
        log.error("💥 STRIPE WEBHOOK ERROR", e);log.error("💥 STRIPE WEBHOOK ERROR", e);
      return ResponseEntity.status(400).body(Map.of("ok", false, "error", "Bad payload"));
    }

    log.info("stripe webhook received type={} id={}", event.getType(), event.getId());

    if (!"checkout.session.completed".equals(event.getType())) {
      return ResponseEntity.ok(Map.of("ok", true, "ignored", true, "eventId", event.getId(), "type", event.getType()));
    }

    try {
      boolean processed = fulfill.process(event);

      if (!processed) {
        // IMPORTANTE: 500 => Stripe reintenta y tú ves el error en logs
        log.warn("stripe fulfillment returned false eventId={} type={}", event.getId(), event.getType());
        return ResponseEntity.status(500).body(Map.of(
            "ok", false, "error", "Fulfillment returned false", "eventId", event.getId(), "type", event.getType()
        ));
      }

      return ResponseEntity.ok(Map.of("ok", true, "processed", true, "eventId", event.getId(), "type", event.getType()));
    } catch (Exception e) {
      log.error("stripe fulfillment failed eventId={} type={}", event.getId(), event.getType(), e);
      return ResponseEntity.status(500).body(Map.of(
          "ok", false, "error", "Fulfillment failed", "eventId", event.getId(), "type", event.getType()
      ));
    }
  }
}
