package com.evilink.crypto_link.service;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.*;

@Component
public class CoinGeckoPriceProvider {

  private final RestClient coingecko;
  private final SymbolService symbolService;

  public CoinGeckoPriceProvider(RestClient coingeckoRestClient, SymbolService symbolService) {
    this.coingecko = coingeckoRestClient;
    this.symbolService = symbolService;
  }

  @SuppressWarnings("unchecked")
  public Map<String, BigDecimal> getPrices(List<String> symbols, String fiat) {

    Map<String,String> symToId = symbolService.listActiveSymbolToCoingeckoId();

    String vs = fiat.toLowerCase(); // eur/usd/mxn
    List<String> norm = symbols.stream().map(s -> s.trim().toUpperCase()).filter(s -> !s.isBlank()).distinct().toList();

    // ids para coingecko
    String ids = norm.stream()
      .map(symToId::get)
      .filter(id -> id != null && !id.isBlank())
      .distinct()
      .reduce((a,b) -> a + "," + b)
      .orElse("");

    if (ids.isBlank()) return Map.of();

    Map<String,Object> resp = coingecko.get()
      .uri(uriBuilder -> uriBuilder
        .path("/simple/price")
        .queryParam("ids", ids)
        .queryParam("vs_currencies", vs)
        .build())
      .retrieve()
      .body(Map.class);

    Map<String, BigDecimal> out = new HashMap<>();
    if (resp == null) return out;

    for (String sym : norm) {
      String id = symToId.get(sym);
      if (id == null) continue;

      Object rowObj = resp.get(id);
      if (!(rowObj instanceof Map<?,?> row)) continue;

      Object priceObj = row.get(vs);
      if (priceObj instanceof Number n) out.put(sym, BigDecimal.valueOf(n.doubleValue()));
    }

    return out;
  }
}
