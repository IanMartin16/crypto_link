package com.evilink.crypto_link.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class FulfillmentRepository {

  private final JdbcTemplate jdbc;

  public FulfillmentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean insertIfAbsent(
      String email,
      String plan,
      String apiKey,
      String source,
      String eventId,
      String sessionId,
      String customerId,
      String subscriptionId,
      String priceId,
      String productId,
      String subscriptionStatus
  ) {
    int rows = jdbc.update("""
      insert into cryptolink_fulfillments(
        email,
        plan,
        api_key,
        source,
        event_id,
        session_id,
        customer_id,
        subscription_id,
        price_id,
        product_id,
        subscription_status,
        email_status,
        updated_at
      )
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', now())
      on conflict (subscription_id)
        where subscription_id is not null
      do nothing
      """,
      email,
      plan,
      apiKey,
      source,
      eventId,
      sessionId,
      customerId,
      subscriptionId,
      priceId,
      productId,
      subscriptionStatus
    );

    return rows == 1;
  }

  public Optional<FulfillmentRow> findBySubscriptionId(
      String subscriptionId
  ) {
    return jdbc.query("""
        select
          email,
          plan,
          api_key,
          customer_id,
          subscription_id,
          price_id,
          product_id,
          subscription_status
        from cryptolink_fulfillments
        where subscription_id = ?
        limit 1
      """,
      rs -> rs.next()
          ? Optional.of(mapRow(rs))
          : Optional.empty(),
      subscriptionId
    );
  }

  public Optional<FulfillmentRow> findLatestByEmail(String email) {
    return jdbc.query("""
        select
          email,
          plan,
          api_key,
          customer_id,
          subscription_id,
          price_id,
          product_id,
          subscription_status
        from cryptolink_fulfillments
        where email = ?
        order by created_at desc
        limit 1
      """,
      rs -> rs.next()
          ? Optional.of(mapRow(rs))
          : Optional.empty(),
      email
    );
  }

  public Optional<FulfillmentRow> findByApiKey(String apiKey) {
    return jdbc.query("""
        select
          email,
          plan,
          api_key,
          customer_id,
          subscription_id,
          price_id,
          product_id,
          subscription_status
        from cryptolink_fulfillments
        where api_key = ?
        order by created_at desc
        limit 1
      """,
      rs -> rs.next()
          ? Optional.of(mapRow(rs))
          : Optional.empty(),
      apiKey
    );
  }

  public void markEmailSent(String email, String apiKey) {
    jdbc.update("""
      update cryptolink_fulfillments
      set
        email_status = 'SENT',
        email_error = null,
        updated_at = now()
      where email = ?
        and api_key = ?
      """,
      email,
      apiKey
    );
  }

  public void markEmailFailed(
      String email,
      String apiKey,
      String error
  ) {
    jdbc.update("""
      update cryptolink_fulfillments
      set
        email_status = 'FAILED',
        email_error = ?,
        updated_at = now()
      where email = ?
        and api_key = ?
      """,
      truncate(error, 1000),
      email,
      apiKey
    );
  }

  private FulfillmentRow mapRow(
      java.sql.ResultSet rs
  ) throws java.sql.SQLException {
    return new FulfillmentRow(
        rs.getString("email"),
        rs.getString("plan"),
        rs.getString("api_key"),
        rs.getString("customer_id"),
        rs.getString("subscription_id"),
        rs.getString("price_id"),
        rs.getString("product_id"),
        rs.getString("subscription_status")
    );
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) return null;
    return value.length() <= maxLength
        ? value
        : value.substring(0, maxLength);
  }

  public record FulfillmentRow(
      String email,
      String plan,
      String apiKey,
      String customerId,
      String subscriptionId,
      String priceId,
      String productId,
      String subscriptionStatus
    ) {}

    public void insert(
      String email,
      String plan,
      String apiKey,
      String source,
      String eventId,
      String sessionId
    ) {
      jdbc.update("""
          insert into cryptolink_fulfillments(
            email,
            plan,
            api_key,
            source,
            event_id,
            session_id,
            email_status,
            updated_at
          )
          values (?, ?, ?, ?, ?, ?, 'PENDING', now())
          """,
          email,
          plan,
          apiKey,
          source,
          eventId,
          sessionId
      );
    }

    public int updateSubscriptionState(
      String subscriptionId,
      String subscriptionStatus,
      boolean cancelAtPeriodEnd,
      boolean cancellationScheduled,
      OffsetDateTime currentPeriodEnd,
      OffsetDateTime stripeCancelAt,
      OffsetDateTime revokedAt
  ) {
    return jdbc.update("""
        update cryptolink_fulfillments
        set
          subscription_status = ?,
          cancel_at_period_end = ?,
          cancellation_scheduled = ?,
          current_period_end = ?,
          stripe_cancel_at = ?,
          revoked_at = ?,
          updated_at = now()
        where subscription_id = ?
        """,
        subscriptionStatus,
        cancelAtPeriodEnd,
        cancellationScheduled,
        currentPeriodEnd,
        stripeCancelAt,
        revokedAt,
        subscriptionId
    );
  }
}