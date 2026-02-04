package com.evilink.crypto_link.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class FulfillmentRepository {

  private final JdbcTemplate jdbc;

  public FulfillmentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(String email, String plan, String apiKey, String source, String eventId, String sessionId) {
    jdbc.update("""
      insert into cryptolink_fulfillments(email, plan, api_key, source, event_id, session_id, email_status)
      values (?, ?, ?, ?, ?, ?, 'PENDING')
    """, email, plan, apiKey, source, eventId, sessionId);
  }

  public Optional<FulfillmentRow> findLatestByEmail(String email) {
    return jdbc.query("""
        select email, plan, api_key
        from cryptolink_fulfillments
        where email = ?
        order by created_at desc
        limit 1
      """,
      rs -> rs.next()
          ? Optional.of(new FulfillmentRow(rs.getString("email"), rs.getString("plan"), rs.getString("api_key")))
          : Optional.empty(),
      email
    );
  }

  public Optional<FulfillmentRow> findByApiKey(String apiKey) {
    return jdbc.query("""
        select email, plan, api_key
        from cryptolink_fulfillments
        where api_key = ?
        order by created_at desc
        limit 1
      """,
      rs -> rs.next()
          ? Optional.of(new FulfillmentRow(rs.getString("email"), rs.getString("plan"), rs.getString("api_key")))
          : Optional.empty(),
      apiKey
    );
  }

  public void markEmailSent(String email, String apiKey) {
    jdbc.update("""
      update cryptolink_fulfillments
      set email_status = 'SENT', email_error = null
      where email = ? and api_key = ?
    """, email, apiKey);
  }

  public void markEmailFailed(String email, String apiKey, String err) {
    jdbc.update("""
      update cryptolink_fulfillments
      set email_status = 'FAILED', email_error = ?
      where email = ? and api_key = ?
    """, err, email, apiKey);
  }

  public record FulfillmentRow(String email, String plan, String apiKey) {}
}
