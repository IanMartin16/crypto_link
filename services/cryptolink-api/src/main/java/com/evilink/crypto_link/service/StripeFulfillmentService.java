package com.evilink.crypto_link.service;

import com.evilink.crypto_link.persistence.ApiKeyRepository;
import com.evilink.crypto_link.persistence.FulfillmentRepository;
import com.stripe.Stripe;
import com.stripe.model.Price;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
  private final TransactionTemplate transactionTemplate;

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
      FulfillmentRepository fulfillRepo,
      PlatformTransactionManager transactionManager
  ) {
    this.jdbc = jdbc;
    this.apiKeys = apiKeys;
    this.email = email;
    this.fulfillRepo = fulfillRepo;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

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

  /*
   * Todas las llamadas externas a Stripe ocurren fuera
   * de la transacción JDBC.
   */
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

  SubscriptionResolution resolution =
      resolveCryptoLinkSubscription(subscriptionId);

  /*
   * El evento es auténtico, pero pertenece a otro producto
   * o a un precio que CryptoLink no reconoce.
   */
  if (resolution == null) {
    transactionTemplate.executeWithoutResult(status -> {
      if (reserveEvent(eventId)) {
        markEventCompleted(
            eventId,
            "IGNORED_WRONG_PRODUCT"
        );
      }
    });

    log.info(
        "Stripe fulfillment ignored: foreign product or price eventId={} sessionId={} subscriptionId={}",
        eventId,
        sessionId,
        subscriptionId
    );

    return true;
  }

  if (isBlank(emailTo)) {
    throw new IllegalStateException(
        "Stripe fulfillment: missing customer email"
    );
  }

  validatePlan(resolution.plan());

  /*
   * Solo esta parte utiliza una transacción.
   * No contiene llamadas a Stripe ni al proveedor de correo.
   */
  PersistResult persisted =
      transactionTemplate.execute(status -> {

        var existing =
            fulfillRepo.findBySubscriptionId(
                subscriptionId
            );

        if (existing.isPresent()) {
          if (reserveEvent(eventId)) {
            markEventCompleted(
                eventId,
                "DUPLICATE_SUBSCRIPTION"
            );
          }

          return new PersistResult(
              existing.get().apiKey(),
              false
          );
        }

        /*
         * Puede bloquear momentáneamente si el mismo evento
         * está siendo procesado en paralelo. Después del conflicto
         * volvemos a buscar el fulfillment.
         */
        if (!reserveEvent(eventId)) {
          var existingAfterDuplicateEvent =
              fulfillRepo.findBySubscriptionId(
                  subscriptionId
              );

          if (existingAfterDuplicateEvent.isPresent()) {
            return new PersistResult(
                existingAfterDuplicateEvent.get().apiKey(),
                false
            );
          }

          throw new IllegalStateException(
              "Stripe event already reserved without fulfillment: "
                  + eventId
          );
        }

        String generatedApiKey = genKey();

        int insertedKey = apiKeys.insertKey(
            generatedApiKey,
            resolution.plan(),
            "ACTIVE",
            null
        );

        if (insertedKey != 1) {
          throw new IllegalStateException(
              "Stripe fulfillment: API key could not be inserted"
          );
        }

        boolean fulfillmentInserted =
            fulfillRepo.insertIfAbsent(
                emailTo,
                resolution.plan(),
                generatedApiKey,
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
           * La excepción revierte también la key recién creada
           * y evita dejar una key huérfana.
           */
          throw new IllegalStateException(
              "Stripe fulfillment already exists for subscription: "
                  + subscriptionId
          );
        }

        markEventCompleted(
            eventId,
            "COMPLETED"
        );

        return new PersistResult(
            generatedApiKey,
            true
        );
      });

  if (persisted == null) {
    throw new IllegalStateException(
        "Stripe fulfillment transaction returned no result"
    );
  }

  if (!persisted.created()) {
    log.info(
        "Stripe fulfillment already processed eventId={} subscriptionId={} apiKey={}",
        eventId,
        subscriptionId,
        mask(persisted.apiKey())
    );

    return true;
  }

  /*
   * Aquí la transacción ya terminó y la key ya es visible
   * para otros eventos como subscription.updated.
   */
  String apiKey = persisted.apiKey();

  try {
    email.sendApiKey(
        emailTo,
        resolution.plan(),
        apiKey
    );

    fulfillRepo.markEmailSent(
        emailTo,
        apiKey
    );

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

  public boolean processSubscriptionUpdated(
    String eventId,
    String subscriptionId
) throws Exception {

  if (isBlank(eventId) || isBlank(subscriptionId)) {
    log.warn(
        "Stripe subscription update rejected: missing eventId/subscriptionId"
    );
    return false;
  }

  /*
   * Stripe se consulta fuera de la transacción JDBC.
   */
  Subscription subscription =
      retrieveSubscriptionExpanded(subscriptionId);

  SubscriptionResolution resolution =
      resolveCryptoLinkSubscription(subscription);

  if (resolution == null) {
    transactionTemplate.executeWithoutResult(tx -> {
      if (reserveEvent(eventId)) {
        markEventCompleted(
            eventId,
            "IGNORED_WRONG_PRODUCT"
        );
      }
    });

    return true;
  }

  var fulfillment =
      fulfillRepo.findBySubscriptionId(subscriptionId);

  if (fulfillment.isEmpty()) {
    throw new IllegalStateException(
        "No fulfillment found for subscription "
            + subscriptionId
    );
  }

  String subscriptionStatus =
      subscription.getStatus();

  boolean cancelAtPeriodEnd =
      Boolean.TRUE.equals(
          subscription.getCancelAtPeriodEnd()
      );

  OffsetDateTime stripeCancelAt =
      fromEpoch(subscription.getCancelAt());

  OffsetDateTime currentPeriodEnd =
      resolveCurrentPeriodEnd(subscription);

  /*
   * En tu versión de Stripe puede llegar cancel_at con
   * cancel_at_period_end=false.
   */
  boolean cancellationScheduled =
      cancelAtPeriodEnd || stripeCancelAt != null;

  boolean shouldRemainActive =
      isActiveSubscriptionStatus(
          subscriptionStatus
      );

  Boolean processed =
      transactionTemplate.execute(tx -> {

        /*
         * Única reserva del evento.
         */
        if (!reserveEvent(eventId)) {
          return false;
        }

        int updated =
            fulfillRepo.updateSubscriptionState(
                subscriptionId,
                subscriptionStatus,
                cancelAtPeriodEnd,
                cancellationScheduled,
                currentPeriodEnd,
                stripeCancelAt,
                shouldRemainActive
                    ? null
                    : OffsetDateTime.now(
                        ZoneOffset.UTC
                    )
            );

        if (updated != 1) {
          throw new IllegalStateException(
              "Subscription state update affected "
                  + updated
                  + " rows for "
                  + subscriptionId
          );
        }

        if (!shouldRemainActive) {
          int deactivated =
              apiKeys.deactivate(
                  fulfillment.get().apiKey()
              );

          log.info(
              "Stripe API key deactivation subscriptionId={} affectedRows={}",
              subscriptionId,
              deactivated
          );
        }

        markEventCompleted(
            eventId,
            "COMPLETED"
        );

        return true;
      });

  if (!Boolean.TRUE.equals(processed)) {
    log.info(
        "Stripe subscription update duplicate ignored eventId={} subscriptionId={}",
        eventId,
        subscriptionId
    );

    return true;
  }

  log.info(
      "Stripe subscription updated eventId={} subscriptionId={} status={} cancelAtPeriodEnd={} stripeCancelAt={} currentPeriodEnd={} cancellationScheduled={} active={}",
      eventId,
      subscriptionId,
      subscriptionStatus,
      cancelAtPeriodEnd,
      stripeCancelAt,
      currentPeriodEnd,
      cancellationScheduled,
      shouldRemainActive
  );

  return true;
}

  public boolean processSubscriptionDeleted(
    String eventId,
    String subscriptionId
  ) throws Exception {

  if (isBlank(eventId) || isBlank(subscriptionId)) {
    log.warn(
        "Stripe subscription deletion rejected: missing eventId/subscriptionId"
    );
    return false;
  }

  Subscription subscription =
      retrieveSubscriptionExpanded(subscriptionId);

  SubscriptionResolution resolution =
      resolveCryptoLinkSubscription(subscription);

  if (resolution == null) {
    recordIgnoredEvent(eventId, "IGNORED_WRONG_PRODUCT");
    return true;
  }

  var fulfillment =
      fulfillRepo.findBySubscriptionId(subscriptionId);

  if (fulfillment.isEmpty()) {
    throw new IllegalStateException(
        "No fulfillment found for deleted subscription "
            + subscriptionId
    );
  }

  if (!reserveEvent(eventId)) {
    log.info(
        "Stripe subscription deletion duplicate ignored eventId={}",
        eventId
    );
    return true;
  }

  OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

  transactionTemplate.executeWithoutResult(tx -> {

  fulfillRepo.updateSubscriptionState(
      subscriptionId,
      "canceled",
      false,
      false,
      fromEpoch(subscription.getCurrentPeriodEnd()),
      fromEpoch(subscription.getCancelAt()),
      now
  );

  apiKeys.deactivate(fulfillment.get().apiKey());

  markEventCompleted(eventId, "COMPLETED");
  });

  log.info(
      "Stripe subscription deleted eventId={} subscriptionId={} apiKey={}",
      eventId,
      subscriptionId,
      mask(fulfillment.get().apiKey())
    );

    return true;
  }

    private Session retrieveSessionExpanded(
        String sessionId
    )   throws Exception {
    ensureStripeConfiguration();

    Stripe.apiKey = stripeSecretKey;

    Map<String, Object> params = new HashMap<>();
    params.put(
        "expand",
        List.of("customer", "subscription")
    );

    return Session.retrieve(sessionId, params, null);
    }

    private Subscription retrieveSubscriptionExpanded(
      String subscriptionId
    ) throws Exception {

    ensureStripeConfiguration();
    Stripe.apiKey = stripeSecretKey;

    Map<String, Object> params = new HashMap<>();
    params.put(
      "expand",
      List.of("items.data.price.product")
    );

    return Subscription.retrieve(
      subscriptionId,
      params,
      null
    );
  }

    private SubscriptionResolution resolveCryptoLinkSubscription(
      String subscriptionId
    ) throws Exception {

    Subscription subscription =
        retrieveSubscriptionExpanded(subscriptionId);

    return resolveCryptoLinkSubscription(subscription);   
    } 

    private SubscriptionResolution resolveCryptoLinkSubscription(
    Subscription subscription
  ) {

  if (subscription == null) {
    throw new IllegalArgumentException(
        "Stripe subscription cannot be null"
    );
  }

  if (subscription.getItems() == null
      || subscription.getItems().getData() == null
      || subscription.getItems().getData().isEmpty()) {
    throw new IllegalStateException(
        "Stripe subscription has no items"
    );
  }

  if (subscription.getItems().getData().size() != 1) {
    throw new IllegalStateException(
        "Expected exactly one Stripe subscription item"
    );
  }

  var item = subscription.getItems().getData().get(0);
  var price = item.getPrice();

  if (price == null || isBlank(price.getId())) {
    throw new IllegalStateException(
        "Stripe subscription item has no price"
    );
  }

  String priceId = price.getId();
  String productId = extractProductId(price);

  if (!cryptoLinkProductId.equals(productId)) {
    log.info(
        "Stripe subscription ignored: foreign product subscriptionId={} productId={} priceId={}",
        subscription.getId(),
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
        "Stripe subscription ignored: unknown CryptoLink price subscriptionId={} productId={} priceId={}",
        subscription.getId(),
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

  private boolean isActiveSubscriptionStatus(String status) {
    return Set.of(
      "active",
      "trialing",
      "past_due"
    ).contains(status);
  }

  private OffsetDateTime fromEpoch(Long epochSeconds) {
    if (epochSeconds == null) return null;

    return OffsetDateTime.ofInstant(
      Instant.ofEpochSecond(epochSeconds),
      ZoneOffset.UTC
    );
  }

  private void recordIgnoredEvent(
    String eventId,
    String status
  ) {
    if (reserveEvent(eventId)) {
      markEventCompleted(eventId, status);
    }
  }
  private record PersistResult(
    String apiKey,
    boolean created
  ) {}

  private OffsetDateTime resolveCurrentPeriodEnd(
      Subscription subscription
  ) {
    if (subscription == null) {
      return null;
    }

    /*
     * Compatibilidad con versiones anteriores de Stripe API,
     * donde current_period_end estaba en Subscription.
     */
    Long subscriptionPeriodEnd =
      subscription.getCurrentPeriodEnd();

  if (subscriptionPeriodEnd != null) {
    return fromEpoch(subscriptionPeriodEnd);
  }

  /*
   * En API Basil/Clover, current_period_end vive dentro de
   * items.data[]. Algunas versiones de stripe-java todavía
   * no exponen SubscriptionItem#getCurrentPeriodEnd().
   */
  try {
    JsonObject raw = subscription.getRawJsonObject();

    if (raw == null) {
      return null;
    }

    JsonObject items = raw.getAsJsonObject("items");

    if (items == null) {
      return null;
    }

    JsonArray data = items.getAsJsonArray("data");

    if (data == null || data.isEmpty()) {
      return null;
    }

    JsonElement firstElement = data.get(0);

    if (firstElement == null
        || !firstElement.isJsonObject()) {
      return null;
    }

    JsonObject firstItem =
        firstElement.getAsJsonObject();

    JsonElement periodEnd =
        firstItem.get("current_period_end");

    if (periodEnd == null
        || periodEnd.isJsonNull()) {
      return null;
    }

    return fromEpoch(periodEnd.getAsLong());

  } catch (Exception e) {
    log.warn(
        "Stripe subscription current_period_end could not be resolved subscriptionId={}",
        subscription.getId(),
        e
    );

      return null;
    }
  }
}