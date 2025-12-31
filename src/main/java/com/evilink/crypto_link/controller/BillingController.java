package com.evilink.crypto_link.controller;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/v1/billing")
public class BillingController {

  // ✅ usa tus nombres reales
  @Value("${cryptolink.stripe.secret-key:}")
  private String stripeSecret;

  @Value("${cryptolink.stripe.price.business:}")
  private String priceBusiness;

  @Value("${cryptolink.stripe.price.pro:}")
  private String pricePro;

  @Value("${app.landing-url:https://evi_link.dev/cryptolink}")
  private String landingUrl;

  public record CheckoutReq(@Email @NotBlank String email) {}

  @PostMapping("/checkout")
  public Map<String, Object> checkout(
      @RequestParam String plan,
      @RequestBody CheckoutReq body,
      @RequestHeader(name = "Idempotency-Key", required = false) String idemKey
  ) throws Exception {

    if (stripeSecret == null || stripeSecret.isBlank()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stripe not configured");
    }
    if (body == null || body.email() == null || body.email().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing email");
    }

    String p = plan == null ? "" : plan.trim().toUpperCase();
    String priceId = switch (p) {
      case "BUSINESS" -> priceBusiness;
      case "PRO" -> pricePro;
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported plan: " + p);
    };

    if (priceId == null || priceId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PriceId not configured for plan " + p);
    }

    // Stripe key
    Stripe.apiKey = stripeSecret;

    // Idempotencia: si no mandan header, generamos uno determinista por email+plan
    String idempotencyKey = (idemKey == null || idemKey.isBlank())
        ? ("cryptolink_" + p + "_" + body.email().trim().toLowerCase())
        : idemKey.trim();

    // Success/Cancel URLs (landing)
    String successUrl = landingUrl + "?status=success&plan=" + p;
    String cancelUrl  = landingUrl + "?status=cancel&plan=" + p;

    SessionCreateParams params = SessionCreateParams.builder()
        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
        .setCustomerEmail(body.email().trim())
        .setSuccessUrl(successUrl)
        .setCancelUrl(cancelUrl)
        .addLineItem(
            SessionCreateParams.LineItem.builder()
                .setPrice(priceId)
                .setQuantity(1L)
                .build()
        )
        // Metadatos útiles para fulfillment (webhook)
        .putMetadata("app", "cryptolink")
        .putMetadata("plan", p)
        .putMetadata("email", body.email().trim().toLowerCase())
        .build();

    // Idempotency (Stripe)
    com.stripe.net.RequestOptions opts = com.stripe.net.RequestOptions.builder()
        .setIdempotencyKey(idempotencyKey)
        .build();

    Session session = Session.create(params, opts);

    return Map.of(
        "ok", true,
        "plan", p,
        "email", body.email().trim().toLowerCase(),
        "url", session.getUrl(),
        "sessionId", session.getId()
    );
  }
}
