package com.evilink.crypto_link.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

  private final String webhookSecret;
  private final StripeFulfillmentService fulfillment;

  public StripeWebhookController(
      @Value("${cryptolink.stripe.webhook-secret}") String webhookSecret,
      StripeFulfillmentService fulfillment
  ) {
    this.webhookSecret = webhookSecret;
    this.fulfillment = fulfillment;
  }

  @PostMapping(value = "/stripe/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> handle(
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

    String type = event.getType();

    // Para Payment Links + subs: esto es lo normal.
    if ("checkout.session.completed".equals(type) || "checkout.session.async_payment_succeeded".equals(type)) {
      Object obj = event.getDataObjectDeserializer().getObject().orElse(null);
      if (obj instanceof Session s) {
        fulfillment.fulfillFromCheckoutSessionId(s.getId());
      }
    }

    return Map.of("ok", true);
  }
}
