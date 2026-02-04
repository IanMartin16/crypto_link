package com.evilink.crypto_link.persistence;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class SymbolRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public SymbolRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<String> listActiveSymbols() {
    String sql = """
      select symbol
      from cryptolink_symbols
      where active = true
      order by symbol
    """;
    return jdbc.getJdbcTemplate().queryForList(sql, String.class);
  }

  public Map<String, String> resolveIds(List<String> symbolsUpper) {
    if (symbolsUpper == null || symbolsUpper.isEmpty()) return Map.of();

    String sql = """
      select symbol, coingecko_id
      from cryptolink_symbols
      where active = true
        and symbol in (:symbols)
    """;

    Map<String, Object> params = Map.of("symbols", symbolsUpper);

    var rows = jdbc.query(sql, params, (rs, n) -> new AbstractMap.SimpleEntry<>(
        rs.getString("symbol"),
        rs.getString("coingecko_id")
    ));

    Map<String, String> out = new HashMap<>();
    for (var e : rows) out.put(e.getKey(), e.getValue());
    return out;
  }
}
