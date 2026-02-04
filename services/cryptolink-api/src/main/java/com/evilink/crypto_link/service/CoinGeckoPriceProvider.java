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
    String vs = fiat.toLowerCase();

    List<String> norm = symbols.stream()
      .filter(Objects::nonNull)
      .map(s -> s.trim().toUpperCase())
      .filter(s -> !s.isBlank())
      .distinct()
      .toList();

    // Detecta símbolos sin mapping
    List<String> missing = norm.stream()
      .filter(sym -> {
        String id = symToId.get(sym);
        return (id == null || id.isBlank());
      })
      .toList();

    // Si quieres que NO truene, comenta este throw y solo deja log
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("Missing coingecko_id for: " + missing);
    }

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

    if (resp == null) return Map.of();

    // reverse map: id -> symbol
    Map<String,String> idToSym = new HashMap<>();
    for (String sym : norm) {
      String id = symToId.get(sym);
      if (id != null) idToSym.put(id, sym);
    }

    Map<String, BigDecimal> out = new LinkedHashMap<>();

    for (var entry : resp.entrySet()) {
      String id = entry.getKey();
      Object rowObj = entry.getValue();
      if (!(rowObj instanceof Map<?,?> row)) continue;

      Object priceObj = row.get(vs);
      if (!(priceObj instanceof Number n)) continue;

      String sym = idToSym.get(id);
      if (sym != null) {
        out.put(sym, BigDecimal.valueOf(n.doubleValue()));
      }
    }

    return out;
  }
}
