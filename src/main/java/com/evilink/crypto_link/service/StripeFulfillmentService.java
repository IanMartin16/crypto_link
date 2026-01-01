package com.evilink.crypto_link.service;

import com.evilink.crypto_link.persistence.ApiKeyRepository;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.ApiResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;

@Service
public class StripeFulfillmentService {

  private static final Logger log = LoggerFactory.getLogger(StripeFulfillmentService.class);

  @Value("${cryptolink.stripe.secret-key:}")
  private String stripeSecretKey;

  private final JdbcTemplate jdbc;
  private final ApiKeyRepository apiKeys;
  private final SmtpEmailService email;
  private final SecureRandom rnd = new SecureRandom();

  public StripeFulfillmentService(JdbcTemplate jdbc, ApiKeyRepository apiKeys, SmtpEmailService email) {
    this.jdbc = jdbc;
    this.apiKeys = apiKeys;
    this.email = email;
  }

  /**
   * Procesa checkout.session.completed.
   * Importante: si algo de DB falla, debe lanzar excepción para que el controller responda 500
   * y Stripe reintente.
   */
  @Transactional
  public boolean process(Event event) throws Exception {

    // 0) Solo procesamos este tipo (el controller normalmente ya filtra)
    if (!"checkout.session.completed".equals(event.getType())) {
      return false;
    }

    // 1) Obtener Session (deserializada o fallback)
    Session s = extractSession(event);
    if (s == null) {
      log.warn("Stripe fulfillment: could not extract session eventId={}", event.getId());
      return false;
    }

    // 2) plan + email
    String plan = (s.getMetadata() != null) ? s.getMetadata().get("plan") : null;

    String emailTo = null;
    if (s.getCustomerDetails() != null && s.getCustomerDetails().getEmail() != null) {
      emailTo = s.getCustomerDetails().getEmail();
    } else if (s.getCustomerEmail() != null) {
      emailTo = s.getCustomerEmail();
    } else if (s.getMetadata() != null) {
      emailTo = s.getMetadata().get("email");
    }

    if (plan == null || plan.isBlank() || emailTo == null || emailTo.isBlank()) {
      // NO reservamos el evento si faltan datos, para permitir retry futuro
      log.warn("Stripe fulfillment: missing plan/email eventId={} sessionId={}", event.getId(), s.getId());
      return false;
    }

    plan = plan.trim().toUpperCase();
    emailTo = emailTo.trim().toLowerCase();

    // 3) Idempotencia (si ya se procesó este eventId, no repetir)
    if (!reserveEvent(event.getId())) {
      log.info("Stripe fulfillment: duplicate event ignored eventId={}", event.getId());
      return false;
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
    // ✅ Forma nueva (sin deprecated)
    var deser = event.getDataObjectDeserializer();
    var opt = deser.getObject();

    if (opt.isPresent() && opt.get() instanceof Session s) {
      return s;
    }

    // ✅ Fallback: usar rawJson para obtener id y hacer retrieve
    String rawJson = deser.getRawJson();
    if (rawJson == null || rawJson.isBlank()) return null;

    @SuppressWarnings("unchecked")
    Map<String, Object> raw = ApiResource.GSON.fromJson(rawJson, Map.class);
    String sessionId = raw == null ? null : (String) raw.get("id");
    if (sessionId == null || sessionId.isBlank()) return null;

    // Necesita secret key real
    if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
      log.warn("Stripe fulfillment: missing cryptolink.stripe.secret-key (sk_...)");
      return null;
    }
    if (!stripeSecretKey.startsWith("sk_")) {
      log.warn("Stripe fulfillment: secret-key does not look like sk_... (current startsWith={})",
          stripeSecretKey.length() >= 3 ? stripeSecretKey.substring(0, 3) : "???");
      return null;
    }

    Stripe.apiKey = stripeSecretKey;
    return Session.retrieve(sessionId);
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
}
