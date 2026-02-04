package com.evilink.crypto_link.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public class FiatRepository {

  private final JdbcTemplate jdbc;

  public FiatRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<String> listActive() {
    return jdbc.query(
      "select fiat from cryptolink_fiats where active = true order by fiat",
      (rs, n) -> rs.getString("fiat")
    );
  }

  public Set<String> listActiveSet() {
    return Set.copyOf(listActive());
  }
}
