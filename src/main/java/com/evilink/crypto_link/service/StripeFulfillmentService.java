package com.evilink.crypto_link.service;

import com.evilink.crypto_link.persistence.ApiKeyRepository;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.ApiResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;

@Service
public class StripeFulfillmentService {

  private final JdbcTemplate jdbc;
  private final ApiKeyRepository apiKeys;
  private final SmtpEmailService email;
  private final SecureRandom rnd = new SecureRandom();

  public StripeFulfillmentService(JdbcTemplate jdbc, ApiKeyRepository apiKeys, SmtpEmailService email) {
    this.jdbc = jdbc;
    this.apiKeys = apiKeys;
    this.email = email;
  }

  public boolean process(Event event) throws Exception {
    // Idempotencia por event.id
    if (!reserveEvent(event.getId())) return false;

    if (!"checkout.session.completed".equals(event.getType())) {
      return false; // por ahora solo este
    }

    // Parse del objeto
    Object obj = event.getDataObjectDeserializer().getObject().orElse(null);
    if (obj == null) return false;

    Session s = (Session) obj;

    String plan = (s.getMetadata() != null) ? s.getMetadata().get("plan") : null;
    String emailTo = null;

    if (s.getCustomerDetails() != null && s.getCustomerDetails().getEmail() != null) {
      emailTo = s.getCustomerDetails().getEmail();
    } else if (s.getCustomerEmail() != null) {
      emailTo = s.getCustomerEmail();
    } else if (s.getMetadata() != null) {
      emailTo = s.getMetadata().get("email");
    }

    if (plan == null || emailTo == null || emailTo.isBlank()) return false;

    plan = plan.trim().toUpperCase();

    // genera apiKey
    String apiKey = genKey();

    // Inserta en DB (status ACTIVE)
    apiKeys.insertKey(apiKey, plan, "ACTIVE", (OffsetDateTime) null);

    // email al cliente
    email.sendApiKey(emailTo.trim(), plan, apiKey);

    return true;
  }

  private boolean reserveEvent(String eventId) {
    try {
      int rows = jdbc.update("""
        insert into cryptolink_stripe_events(event_id) values (?)
        on conflict (event_id) do nothing
      """, eventId);
      return rows == 1;
    } catch (Exception e) {
      return false;
    }
  }

  private String genKey() {
    byte[] b = new byte[24];
    rnd.nextBytes(b);
    return "cl_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }
}
