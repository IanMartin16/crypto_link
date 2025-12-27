package com.evilink.crypto_link.service;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

@Service
public class SymbolService {
  private final JdbcTemplate jdbc;

  public SymbolService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Set<String> listActiveSet() {
    return new HashSet<>(jdbc.queryForList(
      "select symbol from cryptolink_symbols where active=true",
      String.class
    ));
  }

  public Map<String, String> listActiveSymbolToCoingeckoId() {
    List<Map<String,Object>> rows = jdbc.queryForList(
      "select symbol, coingecko_id from cryptolink_symbols where active=true"
    );
    Map<String,String> out = new HashMap<>();
    for (var r : rows) {
      out.put(((String)r.get("symbol")).toUpperCase(), ((String)r.get("coingecko_id")));
    }
    return out;
  }
}

