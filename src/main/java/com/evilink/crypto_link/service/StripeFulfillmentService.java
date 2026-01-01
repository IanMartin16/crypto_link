package com.evilink.crypto_link.service;

import com.evilink.crypto_link.persistence.ApiKeyRepository;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.ApiResource;
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

  private final Random rnd = new Random();

  @Value("${cryptolink.stripe.secret-key:}")
  private String stripeSecretKey;

  public StripeFulfillmentService(JdbcTemplate jdbc, ApiKeyRepository apiKeys, SmtpEmailService email) {
    this.jdbc = jdbc;
    this.apiKeys = apiKeys;
    this.email = email;
  }

  @Transactional
  public boolean process(Event event) throws Exception {

    // ✅ Ignora todo lo que no sea checkout.session.completed
    if (!"checkout.session.completed".equals(event.getType())) {
      return true;
    }

    // 1) Obtener Session (deserializada o fallback)
    Session s = extractSession(event);
    if (s == null) {
      log.warn("Stripe fulfillment: could not extract session eventId={}", event.getId());
      return false;
    }

    // 2) plan + email (primero del objeto recibido)
    String plan = meta(s, "plan");

    String emailTo = null;
    if (s.getCustomerDetails() != null && s.getCustomerDetails().getEmail() != null) {
      emailTo = s.getCustomerDetails().getEmail();
    } else if (s.getCustomerEmail() != null) {
      emailTo = s.getCustomerEmail();
    } else {
      emailTo = meta(s, "email");
    }

    // 2b) Fallback fuerte: retrieve + expand customer/subscription
    if (isBlank(plan) || isBlank(emailTo)) {
      Session full = retrieveSessionExpanded(s.getId());
      log.warn("Stripe fulfillment debug sessionId={} sessionMeta={} subId={}",
          full.getId(),
          full.getMetadata(),
          full.getSubscription()
        );


      if (isBlank(plan)) plan = meta(full, "plan");

      if (isBlank(emailTo)) {
        if (full.getCustomerDetails() != null && full.getCustomerDetails().getEmail() != null) {
          emailTo = full.getCustomerDetails().getEmail();
        } else if (full.getCustomerEmail() != null) {
          emailTo = full.getCustomerEmail();
        } else {
          emailTo = meta(full, "email");
        }
      }

      // Si el plan estaba en Subscription.metadata
      if (isBlank(plan) && full.getSubscription() != null) {
        Subscription sub = Subscription.retrieve(full.getSubscription());
        if (sub.getMetadata() != null) {
          plan = sub.getMetadata().get("plan");
        }
      }
    }

    if (isBlank(plan) || isBlank(emailTo)) {
      // OJO: no reservamos eventId si faltan datos -> permite retry
      log.warn("Stripe fulfillment: missing plan/email eventId={} sessionId={} plan={} email={}",
          event.getId(), s.getId(), plan, emailTo);
      return false;
    }

    plan = plan.trim().toUpperCase();
    emailTo = emailTo.trim().toLowerCase();

    // 3) Idempotencia por eventId
    if (!reserveEvent(event.getId())) {
      log.info("Stripe fulfillment: duplicate event ignored eventId={}", event.getId());
      return true;
    }

    // 4) Genera apiKey + inserta
    String apiKey = genKey();
    apiKeys.insertKey(apiKey, plan, "ACTIVE", (OffsetDateTime) null);

    // 5) Email best-effort (no tumba DB)
    try {
      email.sendApiKey(emailTo, plan, apiKey);
      log.info("Stripe fulfillment: email sent to={} plan={} apiKey={}", emailTo, plan, mask(apiKey));
    } catch (Exception e) {
      log.warn("Stripe fulfillment: email failed to={} plan={} apiKey={}", emailTo, plan, mask(apiKey), e);
    }

    log.info("Stripe fulfillment: completed eventId={} sessionId={} plan={} apiKey={}",
        event.getId(), s.getId(), plan, mask(apiKey));

    return true;
  }

  private Session extractSession(Event event) throws Exception {
    var deser = event.getDataObjectDeserializer();
    var opt = deser.getObject();

    if (opt.isPresent() && opt.get() instanceof Session s) {
      return s;
    }

    // Fallback: usar rawJson para obtener id y hacer retrieve
    String rawJson = deser.getRawJson();
    if (rawJson == null || rawJson.isBlank()) return null;

    @SuppressWarnings("unchecked")
    Map<String, Object> raw = ApiResource.GSON.fromJson(rawJson, Map.class);
    String sessionId = raw == null ? null : (String) raw.get("id");
    if (isBlank(sessionId)) return null;

    return retrieveSessionExpanded(sessionId);
  }

  private Session retrieveSessionExpanded(String sessionId) throws Exception {
    ensureStripeKey();
    Stripe.apiKey = stripeSecretKey;

    Map<String, Object> params = new java.util.HashMap<>();
    params.put("expand", List.of("customer", "subscription"));

    return Session.retrieve(sessionId, params, null);
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
