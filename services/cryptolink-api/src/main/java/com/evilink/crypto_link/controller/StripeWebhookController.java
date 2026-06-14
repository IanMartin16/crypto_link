package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.service.StripeFulfillmentService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Hidden
@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

  private static final Logger log =
      LoggerFactory.getLogger(StripeWebhookController.class);

  private static final long SIGNATURE_TOLERANCE_SECONDS = 300;

  @Value("${stripe.webhook-secret:}")
  private String webhookSecret;

  private final StripeFulfillmentService fulfill;

  public StripeWebhookController(
      StripeFulfillmentService fulfill
  ) {
    this.fulfill = fulfill;
  }

  @PostMapping(
      value = "/webhook",
      consumes = MediaType.APPLICATION_JSON_VALUE
  )
  public ResponseEntity<Map<String, Object>> handle(
      HttpServletRequest request
  ) {

    String signatureHeader =
        request.getHeader("Stripe-Signature");

    String uri = request.getRequestURI();

    final String payload;

    try {
      payload = StreamUtils.copyToString(
          request.getInputStream(),
          StandardCharsets.UTF_8
      );
    } catch (Exception e) {
      log.error(
          "Stripe webhook: failed reading body uri={}",
          uri,
          e
      );

      return ResponseEntity.status(400).body(
          Map.of(
              "ok", false,
              "error", "Could not read body"
          )
      );
    }

    log.info(
        "Stripe webhook hit uri={} payloadLength={} signaturePresent={}",
        uri,
        payload == null ? -1 : payload.length(),
        !isBlank(signatureHeader)
    );

    if (isBlank(payload)) {
      return ResponseEntity.badRequest().body(
          Map.of(
              "ok", false,
              "error", "Empty payload"
          )
      );
    }

    if (isBlank(signatureHeader)) {
      log.warn(
          "Stripe webhook missing Stripe-Signature header"
      );

      return ResponseEntity.badRequest().body(
          Map.of(
              "ok", false,
              "error", "Missing Stripe-Signature"
          )
      );
    }

    if (isBlank(webhookSecret)) {
      log.error(
          "Stripe webhook secret not configured"
      );

      return ResponseEntity.status(500).body(
          Map.of(
              "ok", false,
              "error", "Webhook secret not configured"
          )
      );
    }

    /*
     * 1. Verificar la firma usando el payload crudo.
     */
    try {
      verifyStripeSignature(
          payload,
          signatureHeader,
          webhookSecret,
          SIGNATURE_TOLERANCE_SECONDS
      );
    } catch (Exception e) {
      log.warn(
          "Stripe webhook signature invalid: {}",
          e.getMessage()
      );

      return ResponseEntity.badRequest().body(
          Map.of(
              "ok", false,
              "error", "Invalid signature"
          )
      );
    }

    /*
     * 2. Parseo mínimo del evento.
     */
    final JsonObject root;

    try {
      root = JsonParser.parseString(payload)
          .getAsJsonObject();
    } catch (Exception e) {
      log.error(
          "Stripe webhook bad JSON payload",
          e
      );

      return ResponseEntity.badRequest().body(
          Map.of(
              "ok", false,
              "error", "Bad payload"
          )
      );
    }

    String eventType = getAsString(root, "type");
    String eventId = getAsString(root, "id");

    if (isBlank(eventType) || isBlank(eventId)) {
      log.error(
          "Stripe webhook bad payload: missing type or id"
      );

      return ResponseEntity.badRequest().body(
          Map.of(
              "ok", false,
              "error", "Bad payload"
          )
      );
    }

    log.info(
        "Stripe webhook received type={} eventId={}",
        eventType,
        eventId
    );

    /*
     * Tanto Checkout Session como Subscription tienen su ID en
     * data.object.id.
     */
    String objectId = extractObjectId(root);

    try {
      return switch (eventType) {

        case "checkout.session.completed" ->
            processCheckoutCompleted(
                eventId,
                objectId
            );

        case "customer.subscription.updated" ->
            processSubscriptionUpdated(
                eventId,
                objectId
            );

        case "customer.subscription.deleted" ->
            processSubscriptionDeleted(
                eventId,
                objectId
            );

        default -> {
          log.debug(
              "Stripe webhook event ignored type={} eventId={}",
              eventType,
              eventId
          );

          yield ResponseEntity.ok(
              Map.of(
                  "ok", true,
                  "ignored", true,
                  "type", eventType,
                  "eventId", eventId
              )
          );
        }
      };
    } catch (Exception e) {
      log.error(
          "Stripe event processing failed type={} eventId={} objectId={}",
          eventType,
          eventId,
          objectId,
          e
      );

      /*
       * 500 permite que Stripe reintente el evento.
       */
      return ResponseEntity.status(500).body(
          Map.of(
              "ok", false,
              "error", "Stripe event processing failed",
              "type", eventType,
              "eventId", eventId
          )
      );
    }
  }

  private ResponseEntity<Map<String, Object>>
  processCheckoutCompleted(
      String eventId,
      String sessionId
  ) throws Exception {

    if (isBlank(sessionId)) {
      log.warn(
          "Stripe checkout event missing sessionId eventId={}",
          eventId
      );

      return ResponseEntity.status(500).body(
          Map.of(
              "ok", false,
              "error", "Missing checkout session id",
              "eventId", eventId
          )
      );
    }

    boolean processed =
        fulfill.processCheckoutCompleted(
            eventId,
            sessionId
        );

    if (!processed) {
      log.warn(
          "Stripe checkout fulfillment returned false eventId={} sessionId={}",
          eventId,
          sessionId
      );

      return ResponseEntity.status(500).body(
          Map.of(
              "ok", false,
              "error", "Checkout fulfillment returned false",
              "eventId", eventId
          )
      );
    }

    return ResponseEntity.ok(
        Map.of(
            "ok", true,
            "processed", true,
            "type", "checkout.session.completed",
            "eventId", eventId,
            "sessionId", sessionId
        )
    );
  }

  private ResponseEntity<Map<String, Object>>
  processSubscriptionUpdated(
      String eventId,
      String subscriptionId
  ) throws Exception {

    if (isBlank(subscriptionId)) {
      log.warn(
          "Stripe subscription update missing subscriptionId eventId={}",
          eventId
      );

      return ResponseEntity.status(500).body(
          Map.of(
              "ok", false,
              "error", "Missing subscription id",
              "eventId", eventId
          )
      );
    }

    boolean processed =
        fulfill.processSubscriptionUpdated(
            eventId,
            subscriptionId
        );

    if (!processed) {
      log.warn(
          "Stripe subscription update returned false eventId={} subscriptionId={}",
          eventId,
          subscriptionId
      );

      return ResponseEntity.status(500).body(
          Map.of(
              "ok", false,
              "error", "Subscription update returned false",
              "eventId", eventId
          )
      );
    }

    return ResponseEntity.ok(
        Map.of(
            "ok", true,
            "processed", true,
            "type", "customer.subscription.updated",
            "eventId", eventId,
            "subscriptionId", subscriptionId
        )
    );
  }

  private ResponseEntity<Map<String, Object>>
  processSubscriptionDeleted(
      String eventId,
      String subscriptionId
  ) throws Exception {

    if (isBlank(subscriptionId)) {
      log.warn(
          "Stripe subscription deletion missing subscriptionId eventId={}",
          eventId
      );

      return ResponseEntity.status(500).body(
          Map.of(
              "ok", false,
              "error", "Missing subscription id",
              "eventId", eventId
          )
      );
    }

    boolean processed =
        fulfill.processSubscriptionDeleted(
            eventId,
            subscriptionId
        );

    if (!processed) {
      log.warn(
          "Stripe subscription deletion returned false eventId={} subscriptionId={}",
          eventId,
          subscriptionId
      );

      return ResponseEntity.status(500).body(
          Map.of(
              "ok", false,
              "error", "Subscription deletion returned false",
              "eventId", eventId
          )
      );
    }

    return ResponseEntity.ok(
        Map.of(
            "ok", true,
            "processed", true,
            "type", "customer.subscription.deleted",
            "eventId", eventId,
            "subscriptionId", subscriptionId
        )
    );
  }

  private static String extractObjectId(
      JsonObject root
  ) {
    try {
      JsonObject data =
          root.getAsJsonObject("data");

      if (data == null) {
        return null;
      }

      JsonObject object =
          data.getAsJsonObject("object");

      if (object == null) {
        return null;
      }

      return getAsString(object, "id");
    } catch (Exception e) {
      return null;
    }
  }

  private static String getAsString(
      JsonObject object,
      String key
  ) {
    if (object == null || key == null) {
      return null;
    }

    if (!object.has(key)
        || object.get(key).isJsonNull()) {
      return null;
    }

    try {
      return object.get(key).getAsString();
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Verificación manual HMAC-SHA256.
   *
   * Stripe-Signature:
   * t=timestamp,v1=signature[,v1=signature2...]
   */
  private static void verifyStripeSignature(
      String payload,
      String signatureHeader,
      String secret,
      long toleranceSeconds
  ) throws Exception {

    long timestamp = -1;
    List<String> signatures = new ArrayList<>();

    String[] parts = signatureHeader.split(",");

    for (String part : parts) {
      String value = part.trim();

      if (value.startsWith("t=")) {
        timestamp =
            Long.parseLong(value.substring(2));
      } else if (value.startsWith("v1=")) {
        signatures.add(value.substring(3));
      }
    }

    if (timestamp <= 0 || signatures.isEmpty()) {
      throw new IllegalArgumentException(
          "Invalid Stripe-Signature header format"
      );
    }

    long now = Instant.now().getEpochSecond();
    long difference = Math.abs(now - timestamp);

    if (difference > toleranceSeconds) {
      throw new IllegalArgumentException(
          "Timestamp outside tolerance: diff="
              + difference
              + "s"
      );
    }

    String signedPayload =
        timestamp + "." + payload;

    String expected =
        hmacSha256Hex(secret, signedPayload);

    boolean match = false;

    for (String signature : signatures) {
      if (constantTimeEqualsHex(
          expected,
          signature
      )) {
        match = true;
        break;
      }
    }

    if (!match) {
      throw new IllegalArgumentException(
          "No signatures found matching the expected signature"
      );
    }
  }

  private static String hmacSha256Hex(
      String secret,
      String data
  ) throws Exception {

    Mac mac = Mac.getInstance("HmacSHA256");

    mac.init(
        new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        )
    );

    byte[] raw =
        mac.doFinal(
            data.getBytes(StandardCharsets.UTF_8)
        );

    return toHex(raw);
  }

  private static String toHex(byte[] bytes) {
    char[] hexArray =
        "0123456789abcdef".toCharArray();

    char[] hexChars =
        new char[bytes.length * 2];

    for (int index = 0;
         index < bytes.length;
         index++) {

      int value = bytes[index] & 0xFF;

      hexChars[index * 2] =
          hexArray[value >>> 4];

      hexChars[index * 2 + 1] =
          hexArray[value & 0x0F];
    }

    return new String(hexChars);
  }

  private static boolean constantTimeEqualsHex(
      String first,
      String second
  ) {
    if (first == null || second == null) {
      return false;
    }

    byte[] firstBytes =
        first.getBytes(StandardCharsets.UTF_8);

    byte[] secondBytes =
        second.getBytes(StandardCharsets.UTF_8);

    return MessageDigest.isEqual(
        firstBytes,
        secondBytes
    );
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}