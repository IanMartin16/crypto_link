package com.evilink.crypto_link.controller;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/billing")
public class BillingController {

  @Value("${cryptolink.stripe.secret-key:}")
  private String stripeSecret;

  @Value("${cryptolink.stripe.price.business:}")
  private String priceBusiness;

  @Value("${cryptolink.stripe.price.pro:}")
  private String pricePro;

  @Value("${app.landing-url:}")
  private String landingUrl;

  public record CheckoutReq(String email) {}

  @PostMapping("/checkout")
  public Map<String, Object> checkout(
      @RequestParam String plan,
      @RequestBody CheckoutReq body,
      @RequestHeader(name = "Idempotency-Key", required = false) String idemKey
  ) throws Exception {

    // ---- Validaciones base
    if (stripeSecret == null || stripeSecret.isBlank()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stripe not configured");
    }
    if (!stripeSecret.startsWith("sk_")) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stripe secret-key must be sk_...");
    }
    if (landingUrl == null || landingUrl.isBlank()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing app.landing-url");
    }
    if (body == null || body.email() == null || body.email().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing email");
    }

    // ---- Normaliza input
    String p = (plan == null) ? "" : plan.trim().toUpperCase();
    String email = body.email().trim().toLowerCase();

    // ---- Plan -> priceId
    String priceId = switch (p) {
      case "BUSINESS" -> priceBusiness;
      case "PRO" -> pricePro;
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported plan: " + p);
    };

    if (priceId == null || priceId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PriceId not configured for plan " + p);
    }

    // ---- Stripe key
    Stripe.apiKey = stripeSecret;

    // ---- Idempotencia:
    // Si el cliente manda header, úsalo. Si no, uno por request (ideal en pruebas).
    String idempotencyKey = (idemKey == null || idemKey.isBlank())
        ? ("cryptolink_" + UUID.randomUUID())
        : idemKey.trim();

    // ---- URLs (blindadas, sin concatenación manual)
    String successUrl = buildLandingUrl("success", p);
    String cancelUrl  = buildLandingUrl("cancel",  p);

    // ---- Crea sesión de Checkout
    SessionCreateParams params = SessionCreateParams.builder()
        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
        .setCustomerEmail(email)
        .setSuccessUrl(successUrl)
        .setCancelUrl(cancelUrl)

        // ✅ metadata EN LA SESIÓN (clave para webhook)
        .putMetadata("app", "cryptolink")
        .putMetadata("plan", p)
        .putMetadata("email", email)

        // tracking útil
        .setClientReferenceId("cryptolink_" + p + "_" + email)

        .addLineItem(
            SessionCreateParams.LineItem.builder()
                .setPrice(priceId)
                .setQuantity(1L)
                .build()
        )

        // ✅ metadata también en la suscripción (fallback)
        .setSubscriptionData(
            SessionCreateParams.SubscriptionData.builder()
                .putMetadata("app", "cryptolink")
                .putMetadata("plan", p)
                .putMetadata("email", email)
                .build()
        )
        .build();

    com.stripe.net.RequestOptions opts = com.stripe.net.RequestOptions.builder()
        .setIdempotencyKey(idempotencyKey)
        .build();

    Session session = Session.create(params, opts);

    return Map.of(
        "ok", true,
        "plan", p,
        "email", email,
        "url", session.getUrl(),
        "sessionId", session.getId()
    );
  }

  private String buildLandingUrl(String status, String plan) {
    if (landingUrl == null || landingUrl.isBlank()) {
      throw new IllegalStateException("app.landing-url not configured");
    }

    // Tip: si landingUrl trae espacios o trailing slash, lo limpiamos leve
    return UriComponentsBuilder
        .fromUriString(landingUrl.trim())
        .queryParam("status", status)
        .queryParam("plan", plan)
        .build(true)
        .toUriString();
  }
}
