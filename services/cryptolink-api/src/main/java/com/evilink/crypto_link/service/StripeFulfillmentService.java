package com.evilink.crypto_link.service;

import com.evilink.crypto_link.persistence.ApiKeyRepository;
import com.evilink.crypto_link.persistence.FulfillmentRepository;
import com.stripe.Stripe;
import com.stripe.model.Price;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class StripeFulfillmentService {

  private static final Logger log =
      LoggerFactory.getLogger(StripeFulfillmentService.class);

  private static final Set<String> ALLOWED_PLANS =
      Set.of("BUSINESS", "PRO");

  private final JdbcTemplate jdbc;
  private final ApiKeyRepository apiKeys;
  private final SmtpEmailService email;
  private final FulfillmentRepository fulfillRepo;

  private final SecureRandom secureRandom = new SecureRandom();

  @Value("${cryptolink.stripe.secret-key:}")
  private String stripeSecretKey;

  @Value("${cryptolink.stripe.product-id:}")
  private String cryptoLinkProductId;

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
  public boolean processCheckoutCompleted(
      String eventId,
      String sessionId
  ) throws Exception {

    if (isBlank(eventId) || isBlank(sessionId)) {
      log.warn(
          "Stripe fulfillment rejected: missing eventId/sessionId"
      );
      return false;
    }

    Session session = retrieveSessionExpanded(sessionId);

    String subscriptionId = session.getSubscription();
    String customerId = session.getCustomer();
    String emailTo = normalizeEmail(extractEmail(session));

    if (isBlank(subscriptionId)) {
      log.warn(
          "Stripe fulfillment rejected: missing subscriptionId eventId={} sessionId={}",
          eventId,
          sessionId
      );
      return false;
    }

    /*
     * Idempotencia comercial.
     *
     * Una suscripción solo puede tener un fulfillment.
     */
    var existing =
        fulfillRepo.findBySubscriptionId(subscriptionId);

    if (existing.isPresent()) {
      log.info(
          "Stripe fulfillment duplicate subscription ignored eventId={} sessionId={} subscriptionId={} apiKey={}",
          eventId,
          sessionId,
          subscriptionId,
          mask(existing.get().apiKey())
      );

      markEventCompleted(eventId, "DUPLICATE_SUBSCRIPTION");
      return true;
    }

    SubscriptionResolution resolution =
        resolveCryptoLinkSubscription(subscriptionId);

    /*
     * Un evento válido de Stripe puede pertenecer a Curpify,
     * Data_Link o cualquier otro producto de la cuenta.
     *
     * Se ignora sin reintento porque no es un error transitorio.
     */
    if (resolution == null) {
      log.info(
          "Stripe fulfillment ignored: foreign product or price eventId={} sessionId={} subscriptionId={}",
          eventId,
          sessionId,
          subscriptionId
      );

      markEventCompleted(eventId, "IGNORED_WRONG_PRODUCT");
      return true;
    }

    if (isBlank(emailTo)) {
      log.warn(
          "Stripe fulfillment failed: missing customer email eventId={} sessionId={} subscriptionId={}",
          eventId,
          sessionId,
          subscriptionId
      );

      /*
       * Se lanza excepción para provocar rollback y permitir
       * que Stripe vuelva a intentar.
       */
      throw new IllegalStateException(
          "Stripe fulfillment: missing customer email"
      );
    }

    validatePlan(resolution.plan());

    /*
     * Reservamos el evento después de validar producto, precio,
     * suscripción y datos indispensables.
     */
    if (!reserveEvent(eventId)) {
      log.info(
          "Stripe fulfillment duplicate event ignored eventId={}",
          eventId
      );
      return true;
    }

    String apiKey = genKey();

    /*
     * Primero insertamos la key y después el fulfillment dentro
     * de la misma transacción. Cualquier error provoca rollback.
     */
    int insertedKey = apiKeys.insertKey(
        apiKey,
        resolution.plan(),
        "ACTIVE",
        (OffsetDateTime) null
    );

    if (insertedKey != 1) {
      throw new IllegalStateException(
        "Stripe fulfillment: API key could not be inserted"
      );
    }

    boolean fulfillmentInserted = fulfillRepo.insertIfAbsent(
        emailTo,
        resolution.plan(),
        apiKey,
        "stripe",
        eventId,
        sessionId,
        customerId,
        subscriptionId,
        resolution.priceId(),
        resolution.productId(),
        resolution.subscriptionStatus()
    );

    if (!fulfillmentInserted) {
      /*
       * La suscripción fue insertada concurrentemente.
       * Al lanzar excepción también hacemos rollback de la key
       * recién creada, evitando una key huérfana.
       */
      throw new IllegalStateException(
          "Stripe fulfillment already exists for subscription: " + subscriptionId
      );
    }

    try {
      email.sendApiKey(
          emailTo,
          resolution.plan(),
          apiKey
      );

      fulfillRepo.markEmailSent(emailTo, apiKey);

      log.info(
          "Stripe fulfillment email sent to={} plan={} subscriptionId={} apiKey={}",
          emailTo,
          resolution.plan(),
          subscriptionId,
          mask(apiKey)
      );
    } catch (Exception e) {
      fulfillRepo.markEmailFailed(
          emailTo,
          apiKey,
          e.getMessage()
      );

      log.warn(
          "Stripe fulfillment email failed to={} plan={} subscriptionId={} apiKey={}",
          emailTo,
          resolution.plan(),
          subscriptionId,
          mask(apiKey),
          e
      );
    }

    markEventCompleted(eventId, "COMPLETED");

    log.info(
        "Stripe fulfillment completed eventId={} sessionId={} customerId={} subscriptionId={} productId={} priceId={} plan={} apiKey={}",
        eventId,
        sessionId,
        customerId,
        subscriptionId,
        resolution.productId(),
        resolution.priceId(),
        resolution.plan(),
        mask(apiKey)
    );

    return true;
  }

  private Session retrieveSessionExpanded(
      String sessionId
  ) throws Exception {
    ensureStripeConfiguration();

    Stripe.apiKey = stripeSecretKey;

    Map<String, Object> params = new HashMap<>();
    params.put(
        "expand",
        List.of("customer", "subscription")
    );

    return Session.retrieve(sessionId, params, null);
  }

  private SubscriptionResolution resolveCryptoLinkSubscription(
      String subscriptionId
  ) throws Exception {
    ensureStripeConfiguration();

    Stripe.apiKey = stripeSecretKey;

    Map<String, Object> params = new HashMap<>();
    params.put(
        "expand",
        List.of("items.data.price.product")
    );

    Subscription subscription =
        Subscription.retrieve(subscriptionId, params, null);

    if (subscription.getItems() == null
        || subscription.getItems().getData() == null
        || subscription.getItems().getData().isEmpty()) {
      throw new IllegalStateException(
          "Stripe fulfillment: subscription has no items"
      );
    }

    if (subscription.getItems().getData().size() != 1) {
      throw new IllegalStateException(
          "Stripe fulfillment: expected exactly one subscription item"
      );
    }

    var item = subscription.getItems().getData().get(0);
    Price price = item.getPrice();

    if (price == null || isBlank(price.getId())) {
      throw new IllegalStateException(
          "Stripe fulfillment: subscription item has no price"
      );
    }

    String priceId = price.getId();
    String productId = extractProductId(price);

    if (isBlank(productId)) {
      throw new IllegalStateException(
          "Stripe fulfillment: subscription item has no productId"
      );
    }

    if (!cryptoLinkProductId.equals(productId)) {
      log.info(
          "Stripe subscription does not belong to CryptoLink subscriptionId={} productId={} priceId={}",
          subscriptionId,
          productId,
          priceId
      );

      return null;
    }

    final String plan;

    if (priceBusiness.equals(priceId)) {
      plan = "BUSINESS";
    } else if (pricePro.equals(priceId)) {
      plan = "PRO";
    } else {
      log.warn(
          "Stripe subscription rejected: CryptoLink product with unknown price subscriptionId={} productId={} priceId={}",
          subscriptionId,
          productId,
          priceId
      );

      return null;
    }

    return new SubscriptionResolution(
        plan,
        priceId,
        productId,
        subscription.getStatus()
    );
  }

  private String extractProductId(Price price) {
    if (price == null) return null;

    if (price.getProductObject() != null) {
      return price.getProductObject().getId();
    }

    return price.getProduct();
  }

  private String extractEmail(Session session) {
    if (session == null) return null;

    if (session.getCustomerDetails() != null
        && !isBlank(session.getCustomerDetails().getEmail())) {
      return session.getCustomerDetails().getEmail();
    }

    if (!isBlank(session.getCustomerEmail())) {
      return session.getCustomerEmail();
    }

    return meta(session, "email");
  }

  private String normalizeEmail(String email) {
    return isBlank(email)
        ? null
        : email.trim().toLowerCase(Locale.ROOT);
  }

  private void validatePlan(String plan) {
    if (!ALLOWED_PLANS.contains(plan)) {
      throw new IllegalArgumentException(
          "Unsupported CryptoLink plan: " + plan
      );
    }
  }

  private void ensureStripeConfiguration() {
    if (isBlank(stripeSecretKey)) {
      throw new IllegalStateException(
          "Stripe fulfillment: missing cryptolink.stripe.secret-key"
      );
    }

    if (!stripeSecretKey.startsWith("sk_")) {
      throw new IllegalStateException(
          "Stripe fulfillment: secret key must start with sk_"
      );
    }

    if (isBlank(cryptoLinkProductId)
        || !cryptoLinkProductId.startsWith("prod_")) {
      throw new IllegalStateException(
          "Stripe fulfillment: invalid cryptolink.stripe.product-id"
      );
    }

    if (isBlank(priceBusiness)
        || !priceBusiness.startsWith("price_")) {
      throw new IllegalStateException(
          "Stripe fulfillment: invalid business price"
      );
    }

    if (isBlank(pricePro)
        || !pricePro.startsWith("price_")) {
      throw new IllegalStateException(
          "Stripe fulfillment: invalid pro price"
      );
    }

    if (priceBusiness.equals(pricePro)) {
      throw new IllegalStateException(
          "Stripe fulfillment: business and pro prices cannot be equal"
      );
    }
  }

  private boolean reserveEvent(String eventId) {
    int rows = jdbc.update("""
      insert into cryptolink_stripe_events(
        event_id,
        status
      )
      values (?, 'PROCESSING')
      on conflict (event_id) do nothing
      """,
      eventId
    );

    return rows == 1;
  }

  private void markEventCompleted(
      String eventId,
      String status
  ) {
    jdbc.update("""
      update cryptolink_stripe_events
      set
        status = ?,
        processed_at = now()
      where event_id = ?
      """,
      status,
      eventId
    );
  }

  private String genKey() {
    byte[] bytes = new byte[24];
    secureRandom.nextBytes(bytes);

    return "cl_" +
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes);
  }

  private String mask(String apiKey) {
    if (apiKey == null) return "";
    if (apiKey.length() <= 10) return "***";

    return apiKey.substring(0, 4)
        + "..."
        + apiKey.substring(apiKey.length() - 4);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String meta(
      Session session,
      String key
  ) {
    if (session == null || session.getMetadata() == null) {
      return null;
    }

    return session.getMetadata().get(key);
  }

  private record SubscriptionResolution(
      String plan,
      String priceId,
      String productId,
      String subscriptionStatus
  ) {}

  private static final class DuplicateSubscriptionException
      extends RuntimeException {

    private DuplicateSubscriptionException(String message) {
      super(message);
    }
  }
}