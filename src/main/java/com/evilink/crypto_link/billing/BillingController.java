package com.evilink.crypto_link.billing;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/v1/billing")
public class BillingController {

  @Value("${stripe.secret-key}")
  private String stripeSecret;

  @Value("${stripe.price.business}")
  private String priceBusiness;

  @Value("${stripe.price.pro}")
  private String pricePro;

  @Value("${app.landing-url}")
  private String landingUrl;

  @PostMapping("/checkout")
  public Map<String,Object> checkout(
      @RequestParam String plan,
      @RequestBody CheckoutReq body
  ) throws Exception {

    String email = body.email == null ? "" : body.email.trim().toLowerCase();
    if (email.isBlank() || !email.contains("@")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email");
    }

    String p = plan == null ? "" : plan.trim().toUpperCase();
    String priceId = switch (p) {
      case "BUSINESS" -> priceBusiness;
      case "PRO" -> pricePro;
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown plan");
    };

    Stripe.apiKey = stripeSecret;

    SessionCreateParams params =
        SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomerEmail(email)
            .setSuccessUrl(landingUrl + "/success?session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(landingUrl + "?canceled=1")
            .putMetadata("plan", p)
            .putMetadata("email", email)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPrice(priceId)
                    .build()
            )
            .build();

    Session session = Session.create(params);

    return Map.of("ok", true, "url", session.getUrl());
  }

  public static class CheckoutReq {
    public String email;
  }
}