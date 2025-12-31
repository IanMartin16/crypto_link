package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.service.StripeFulfillmentService;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.stripe.exception.SignatureVerificationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

  @Value("${stripe.webhook-secret}")
  private String webhookSecret;

  private final StripeFulfillmentService fulfill;

  public StripeWebhookController(StripeFulfillmentService fulfill) {
    this.fulfill = fulfill;
  }

  @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Map<String,Object> handle(
      @RequestBody String payload,
      @RequestHeader(name = "Stripe-Signature", required = false) String sigHeader,
      HttpServletRequest req
  ) throws Exception {

    if (sigHeader == null || sigHeader.isBlank()) {
      return Map.of("ok", false, "error", "Missing Stripe-Signature");
    }

    final Event event;
    try {
      event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
    } catch (SignatureVerificationException e) {
      return Map.of("ok", false, "error", "Invalid signature");
    }

    // Procesa solo lo que nos interesa
    boolean processed = fulfill.process(event);

    return Map.of("ok", true, "processed", processed, "type", event.getType());
  }
}
