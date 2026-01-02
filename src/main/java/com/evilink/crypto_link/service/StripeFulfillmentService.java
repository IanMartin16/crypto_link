package com.evilink.crypto_link.service;

import com.evilink.crypto_link.persistence.ApiKeyRepository;
import com.evilink.crypto_link.persistence.FulfillmentRepository;
import com.stripe.Stripe;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class StripeFulfillmentService {

  private static final Logger log = LoggerFactory.getLogger(StripeFulfillmentService.class);

  private final JdbcTemplate jdbc;
  private final ApiKeyRepository apiKeys;
  private final SmtpEmailService email;
  private final FulfillmentRepository fulfillRepo;

  private final Random rnd = new Random();

  @Value("${cryptolink.stripe.secret-key:}")
  private String stripeSecretKey;

  @Value("${cryptolink.stripe.price.business:}")
  private String priceBusiness;

  @Value("${cryptolink.stripe.price.pro:}")
  private String pricePro;

  public StripeFulfillmentService(
      JdbcTemplate jdbc,
      ApiKeyRepository apiKeys,
      SmtpEmailService email,
      FulfillmentRepository fulfillRepo
  ) {
    this.jdbc = jdbc;
    this.apiKeys = apiKeys;
    this.email = email;
    this.fulfillRepo = fulfillRepo;
  }

  @Transactional
  public boolean processCheckoutCompleted(String eventId, String sessionId) throws Exception {

    if (isBlank(eventId) || isBlank(sessionId)) {
      log.warn("Stripe fulfillment: missing eventId/sessionId");
      return false;
    }

    // 1) idempotencia por eventId
    if (!reserveEvent(eventId)) {
      log.info("Stripe fulfillment: duplicate event ignored eventId={}", eventId);
      return true;
    }

    // 2) retrieve session expanded
    Session session = retrieveSessionExpanded(sessionId);

    // 3) plan + email
    String plan = meta(session, "plan");
    String emailTo = extractEmail(session);

    // debug útil si vuelve a pasar
    log.warn("Stripe fulfillment debug sessionId={} sessionMeta={} subId={}",
        sessionId, session.getMetadata(), session.getSubscription());

    // 3b) si falta plan, intenta subscription meta / infer por priceId
    if (isBlank(plan)) {
      String subId = session.getSubscription();
      if (!isBlank(subId)) {
        Subscription subBasic = Subscription.retrieve(subId);
        String subPlan = subBasic.getMetadata() != null ? subBasic.getMetadata().get("plan") : null;
        if (!isBlank(subPlan)) plan = subPlan;
        if (isBlank(plan)) plan = inferPlanFromSubscription(subId);
      }
    }

    if (isBlank(plan) || isBlank(emailTo)) {
      // si faltan datos ya reservamos eventId, pero preferible: marcar y permitir soporte manual
      // (si quieres reintento automático, habría que NO reservar hasta tener plan/email)
      log.warn("Stripe fulfillment: missing plan/email eventId={} sessionId={} plan={} email={}",
          eventId, sessionId, plan, emailTo);
      return false;
    }

    plan = plan.trim().toUpperCase();
    emailTo = emailTo.trim().toLowerCase();

    // 4) generar apikey + guardar
    String apiKey = genKey();
    apiKeys.insertKey(apiKey, plan, "ACTIVE", (OffsetDateTime) null);

    // 5) registrar fulfillment
    fulfillRepo.insert(emailTo, plan, apiKey, "stripe", eventId, sessionId);

    // 6) email best-effort
    try {
      email.sendApiKey(emailTo, plan, apiKey);        // aquí ya estás con Resend por debajo 👍
      fulfillRepo.markEmailSent(emailTo, apiKey);
      log.info("Stripe fulfillment: email sent to={} plan={} apiKey={}", emailTo, plan, mask(apiKey));
    } catch (Exception e) {
      fulfillRepo.markEmailFailed(emailTo, apiKey, e.getMessage());
      log.warn("Stripe fulfillment: email failed to={} plan={} apiKey={}", emailTo, plan, mask(apiKey), e);
      // NO tumbamos el proceso: DB ya quedó bien
    }

    log.info("Stripe fulfillment: completed eventId={} sessionId={} plan={} apiKey={}",
        eventId, sessionId, plan, mask(apiKey));

    return true;
  }

  private Session retrieveSessionExpanded(String sessionId) throws Exception {
    ensureStripeKey();
    Stripe.apiKey = stripeSecretKey;

    Map<String, Object> params = new java.util.HashMap<>();
    params.put("expand", List.of("customer", "subscription"));

    return Session.retrieve(sessionId, params, null);
  }

  private String inferPlanFromSubscription(String subId) throws Exception {
    if (isBlank(subId)) return null;

    ensureStripeKey();
    Stripe.apiKey = stripeSecretKey;

    Map<String, Object> params = new java.util.HashMap<>();
    params.put("expand", List.of("items.data.price"));
    Subscription sub = Subscription.retrieve(subId, params, null);

    String priceId = null;
    if (sub.getItems() != null && sub.getItems().getData() != null && !sub.getItems().getData().isEmpty()) {
      var item0 = sub.getItems().getData().get(0);
      if (item0.getPrice() != null) priceId = item0.getPrice().getId();
    }

    if (isBlank(priceId)) {
      log.warn("Stripe fulfillment: could not infer plan (no priceId) subId={}", subId);
      return null;
    }

    if (!isBlank(pricePro) && pricePro.equals(priceId)) return "PRO";
    if (!isBlank(priceBusiness) && priceBusiness.equals(priceId)) return "BUSINESS";

    log.warn("Stripe fulfillment: unknown priceId={} (expected pro={} business={})",
        priceId, pricePro, priceBusiness);

    return null;
  }

  private String extractEmail(Session s) {
    if (s == null) return null;

    if (s.getCustomerDetails() != null && s.getCustomerDetails().getEmail() != null) {
      return s.getCustomerDetails().getEmail();
    }
    if (s.getCustomerEmail() != null) {
      return s.getCustomerEmail();
    }
    return meta(s, "email");
  }

  private void ensureStripeKey() {
    if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
      throw new IllegalStateException("Stripe fulfillment: missing cryptolink.stripe.secret-key (sk_...)");
    }
    if (!stripeSecretKey.startsWith("sk_")) {
      throw new IllegalStateException("Stripe fulfillment: secret-key must start with sk_...");
    }
  }

  private boolean reserveEvent(String eventId) {
    int rows = jdbc.update("""
      insert into cryptolink_stripe_events(event_id) values (?)
      on conflict (event_id) do nothing
    """, eventId);
    return rows == 1;
  }

  private String genKey() {
    byte[] b = new byte[24];
    rnd.nextBytes(b);
    return "cl_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  private String mask(String k) {
    if (k == null) return "";
    if (k.length() <= 10) return "***";
    return k.substring(0, 4) + "..." + k.substring(k.length() - 4);
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static String meta(Session s, String key) {
    if (s == null || s.getMetadata() == null) return null;
    return s.getMetadata().get(key);
  }
}
