package com.evilink.crypto_link.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class FiatService {

  private final JdbcTemplate jdbc;

  public FiatService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Set<String> listActiveSet() {
    return new HashSet<>(jdbc.queryForList(
      "select code from cryptolink_fiats where active=true",
      String.class
    ));
  }
}
