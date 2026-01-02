package com.evilink.crypto_link.service;

import com.evilink.crypto_link.persistence.ApiKeyRepository;
import com.evilink.crypto_link.persistence.FulfillmentRepository;
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
  private final FulfillmentRepository fulfillRepo;

  private final Random rnd = new Random();

  @Value("${cryptolink.stripe.secret-key:}")
  private String stripeSecretKey;

  @Value("${cryptolink.stripe.price.business:}")
  private String priceBusiness;

  @Value("${cryptolink.stripe.price.pro:}")
  private String pricePro;

  public StripeFulfillmentService(JdbcTemplate jdbc, ApiKeyRepository apiKeys, SmtpEmailService email, FulfillmentRepository fulfillRepo) {
    this.jdbc = jdbc;
    this.apiKeys = apiKeys;
    this.email = email;
    this.fulfillRepo = fulfillRepo;
  }

  @Transactional
  public boolean process(Event event) throws Exception {

    // ✅ Ignora eventos no soportados (webhook "OK")
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
    Session full = null;
    if (isBlank(plan) || isBlank(emailTo)) {
      full = retrieveSessionExpanded(s.getId());
      log.info("Stripe fulfillment: session retrieved sessionId={} subId={}", full.getId(), full.getSubscription());

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
    }

    // 2c) Si sigue faltando plan: inferirlo por priceId de la Subscription
    if (isBlank(plan)) {
      if (full == null) {
        full = retrieveSessionExpanded(s.getId());
      }
      String subId = full.getSubscription();

      // 1) intenta metadata de subscription
      String planFromSubMeta = null;
      if (!isBlank(subId)) {
        Subscription subBasic = Subscription.retrieve(subId);
        if (subBasic.getMetadata() != null) {
          planFromSubMeta = subBasic.getMetadata().get("plan");
        }
      }
      if (!isBlank(planFromSubMeta)) {
        plan = planFromSubMeta;
      } else {
        // 2) inferir por priceId (definitivo)
        plan = inferPlanFromSubscription(subId);
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

    fulfillRepo.insert(emailTo, plan, apiKey, "stripe", event.getId(), s.getId());


    // 5) Email best-effort (no tumba DB)
    try {
      email.sendApiKey(emailTo, plan, apiKey);
      fulfillRepo.markEmailSent(emailTo, apiKey);

      log.info("Stripe fulfillment: email sent to={} plan={} apiKey={}", emailTo, plan, mask(apiKey));
    } catch (Exception e) {
      fulfillRepo.markEmailFailed(emailTo, apiKey, e.getMessage());
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

  private String inferPlanFromSubscription(String subId) throws Exception {
    if (isBlank(subId)) return null;

    ensureStripeKey();
    Stripe.apiKey = stripeSecretKey;

    // Expand para traer price en items
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
