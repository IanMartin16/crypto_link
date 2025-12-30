package com.evilink.crypto_link.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/v1/billing")
public class BillingController {

  private final StripeFulfillmentService fulfillment;

  private final String linkPro;
  private final String linkBusiness;

  public BillingController(
      StripeFulfillmentService fulfillment,
      @Value("${cryptolink.stripe.link.pro:}") String linkPro,
      @Value("${cryptolink.stripe.link.business:}") String linkBusiness
  ) {
    this.fulfillment = fulfillment;
    this.linkPro = linkPro;
    this.linkBusiness = linkBusiness;
  }

  // Público: lo puedes linkear desde README / evi_link.dev
  @GetMapping("/links")
  public Map<String, Object> links() {
    return Map.of(
      "ok", true,
      "plans", Map.of(
        "PRO", linkPro,
        "BUSINESS", linkBusiness
      ),
      "ts", OffsetDateTime.now().toString()
    );
  }

  // Público: success redirect de Stripe
  @GetMapping("/claim")
  public Map<String, Object> claim(@RequestParam("session_id") String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing session_id");
    }

    try {
      var r = fulfillment.fulfillFromCheckoutSessionId(sessionId);

      return Map.of(
        "ok", true,
        "plan", r.plan(),
        "apiKey", r.apiKey(),
        "email", r.email(),
        "ts", OffsetDateTime.now().toString()
      );
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot claim: " + e.getMessage());
    }
  }
}
