package com.evilink.crypto_link.billing;

import com.evilink.crypto_link.persistence.ApiKeyRepository;
import com.evilink.crypto_link.security.ApiKeyStore;
import com.stripe.Stripe;
import com.stripe.model.LineItem;
import com.stripe.model.LineItemCollection;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionListLineItemsParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;

@Service
public class StripeFulfillmentService {

  private static final Logger log = LoggerFactory.getLogger(StripeFulfillmentService.class);
  private static final SecureRandom RNG = new SecureRandom();

  private final JdbcTemplate jdbc;
  private final ApiKeyRepository apiKeyRepo;
  private final ObjectProvider<JavaMailSender> mailSender;

  private final String stripeSecretKey;
  private final String pricePro;
  private final String priceBusiness;
  private final String mailFrom;

  public StripeFulfillmentService(
      JdbcTemplate jdbc,
      ApiKeyRepository apiKeyRepo,
      ObjectProvider<JavaMailSender> mailSender,
      @Value("${cryptolink.stripe.secret-key}") String stripeSecretKey,
      @Value("${cryptolink.stripe.price.pro}") String pricePro,
      @Value("${cryptolink.stripe.price.business}") String priceBusiness,
      @Value("${cryptolink.mail.from:}") String mailFrom
  ) {
    this.jdbc = jdbc;
    this.apiKeyRepo = apiKeyRepo;
    this.mailSender = mailSender;
    this.stripeSecretKey = stripeSecretKey;
    this.pricePro = pricePro;
    this.priceBusiness = priceBusiness;
    this.mailFrom = mailFrom;

    // Stripe global key
    Stripe.apiKey = stripeSecretKey;
  }

  public FulfillmentResult fulfillFromCheckoutSessionId(String sessionId) throws Exception {
    // 0) Idempotencia: si ya existe, regresa lo mismo
    var existing = jdbc.query("""
        select api_key, plan, email
        from cryptolink_billing_sessions
        where session_id = ?
      """,
      rs -> rs.next()
        ? new FulfillmentResult(rs.getString("api_key"), rs.getString("plan"), rs.getString("email"), true)
        : null,
      sessionId
    );
    if (existing != null) return existing;

    // 1) Recupera sesión (expand recomendado) 
    SessionRetrieveParams retrieveParams = SessionRetrieveParams.builder()
      .addExpand("customer_details")
      .addExpand("subscription")
      .build();

    Session session = Session.retrieve(sessionId, retrieveParams, null);

    // Para subs: payment_status debe ser "paid" (si no, mejor no entregar key)
    String payStatus = session.getPaymentStatus();
    if (payStatus == null || !"paid".equalsIgnoreCase(payStatus)) {
      throw new IllegalStateException("Checkout session not paid yet: " + payStatus);
    }

    String email = null;
    if (session.getCustomerDetails() != null) email = session.getCustomerDetails().getEmail();
    if ((email == null || email.isBlank()) && session.getCustomerEmail() != null) email = session.getCustomerEmail();
    if (email == null) email = "";

    // 2) Obtén priceId desde line items (lo más robusto) 
    SessionListLineItemsParams liParams = SessionListLineItemsParams.builder()
      .setLimit(10L)
      .addExpand("data.price")
      .build();

    LineItemCollection items = session.listLineItems(liParams);

    String priceId = null;
    if (items != null && items.getData() != null && !items.getData().isEmpty()) {
      LineItem li = items.getData().get(0);
      if (li.getPrice() != null) priceId = li.getPrice().getId();
    }
    if (priceId == null || priceId.isBlank()) {
      throw new IllegalStateException("Missing priceId in line items");
    }

    ApiKeyStore.Plan plan = mapPriceToPlan(priceId);
    String subscriptionId = session.getSubscription();

    // 3) Genera apiKey
    String apiKey = genApiKey();

    // 4) Inserta en cryptolink_api_keys (tu tabla real de auth)
    apiKeyRepo.insertKey(apiKey, plan.name(), "ACTIVE", null);

    // 5) Guarda billing_sessions (idempotente)
    int inserted = jdbc.update("""
      insert into cryptolink_billing_sessions (session_id, subscription_id, email, plan, api_key)
      values (?, ?, ?, ?, ?)
      on conflict (session_id) do nothing
    """, sessionId, subscriptionId, email, plan.name(), apiKey);

    // Si alguien ganó la carrera, lee el valor real
    if (inserted == 0) {
      var again = jdbc.query("""
          select api_key, plan, email
          from cryptolink_billing_sessions
          where session_id = ?
        """,
        rs -> rs.next()
          ? new FulfillmentResult(rs.getString("api_key"), rs.getString("plan"), rs.getString("email"), true)
          : null,
        sessionId
      );
      if (again != null) return again;
    }

    // 6) Email (solo si hay mail config)
    if (inserted == 1) {
      sendEmailIfPossible(email, plan.name(), apiKey);
    }

    return new FulfillmentResult(apiKey, plan.name(), email, false);
  }

  private ApiKeyStore.Plan mapPriceToPlan(String priceId) {
    if (priceId.equals(pricePro)) return ApiKeyStore.Plan.PRO;
    if (priceId.equals(priceBusiness)) return ApiKeyStore.Plan.BUSINESS;
    throw new IllegalArgumentException("Unknown priceId: " + priceId);
  }

  private String genApiKey() {
    byte[] b = new byte[24];
    RNG.nextBytes(b);
    return "cl_" + HexFormat.of().formatHex(b);
  }

  private void sendEmailIfPossible(String email, String plan, String apiKey) {
    if (email == null || email.isBlank()) return;

    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null) {
      log.info("Mail not configured. Would email apiKey to {}", email);
      return;
    }

    SimpleMailMessage msg = new SimpleMailMessage();
    if (mailFrom != null && !mailFrom.isBlank()) msg.setFrom(mailFrom);
    msg.setTo(email);
    msg.setSubject("Tu API Key de CryptoLink (" + plan + ")");
    msg.setText("""
      ¡Listo!

      Tu suscripción está activa.

      Plan: %s
      API Key: %s

      Docs:
      - /v1/meta
      - /v1/me

      Fecha: %s
      """.formatted(plan, apiKey, OffsetDateTime.now()));

    sender.send(msg);
  }

  public record FulfillmentResult(String apiKey, String plan, String email, boolean alreadyExisted) {}
}
