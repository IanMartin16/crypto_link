package com.evilink.crypto_link.service;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.*;

/**
 * CAMBIO ADITIVO (24h + marketCap):
 * - getPrices() sigue devolviendo Map<String,BigDecimal> de PRECIOS, idéntico a
 *   antes -> los consumidores actuales NO se rompen.
 * - Se añade getPricesRich() que trae precio + 24h + marketCap en la MISMA llamada
 *   (/simple/price con flags include_24hr_change, include_market_cap). Cero costo
 *   extra de llamadas: son solo flags.
 * - Data rica FUTURA no va aquí: va por social_link (menos carga). Aquí solo estos
 *   dos campos que el front ya mapea, para completar las cards gigantes.
 */
@Component
public class CoinGeckoPriceProvider {

  private final RestClient coingecko;
  private final SymbolService symbolService;

  public CoinGeckoPriceProvider(RestClient coingeckoRestClient, SymbolService symbolService) {
    this.coingecko = coingeckoRestClient;
    this.symbolService = symbolService;
  }

  /** Dato por símbolo: precio + cambio 24h + market cap (los dos últimos opcionales). */
  public static class PricePoint {
    public final BigDecimal price;
    public final BigDecimal change24h;   // puede ser null si CoinGecko no lo da
    public final BigDecimal marketCap;   // puede ser null

    public PricePoint(BigDecimal price, BigDecimal change24h, BigDecimal marketCap) {
      this.price = price;
      this.change24h = change24h;
      this.marketCap = marketCap;
    }
  }

  /** COMPAT: sigue devolviendo solo precios. No romper consumidores existentes. */
  public Map<String, BigDecimal> getPrices(List<String> symbols, String fiat) {
    Map<String, PricePoint> rich = getPricesRich(symbols, fiat);
    Map<String, BigDecimal> out = new LinkedHashMap<>();
    rich.forEach((sym, p) -> out.put(sym, p.price));
    return out;
  }

  /** NUEVO: precio + 24h + marketCap en una sola llamada. */
  @SuppressWarnings("unchecked")
  public Map<String, PricePoint> getPricesRich(List<String> symbols, String fiat) {

    Map<String,String> symToId = symbolService.listActiveSymbolToCoingeckoId();
    String vs = fiat.toLowerCase();

    List<String> norm = symbols.stream()
      .filter(Objects::nonNull)
      .map(s -> s.trim().toUpperCase())
      .filter(s -> !s.isBlank())
      .distinct()
      .toList();

    List<String> missing = norm.stream()
      .filter(sym -> {
        String id = symToId.get(sym);
        return (id == null || id.isBlank());
      })
      .toList();

    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("Missing coingecko_id for: " + missing);
    }

    String ids = norm.stream()
      .map(symToId::get)
      .filter(id -> id != null && !id.isBlank())
      .distinct()
      .reduce((a,b) -> a + "," + b)
      .orElse("");

    if (ids.isBlank()) return Map.of();

    final String idsFinal = ids;
    Map<String,Object> resp = coingecko.get()
      .uri(uriBuilder -> uriBuilder
        .path("/simple/price")
        .queryParam("ids", idsFinal)
        .queryParam("vs_currencies", vs)
        .queryParam("include_24hr_change", "true")   // ADITIVO
        .queryParam("include_market_cap", "true")    // ADITIVO
        .build())
      .retrieve()
      .body(Map.class);

    if (resp == null) return Map.of();

    Map<String,String> idToSym = new HashMap<>();
    for (String sym : norm) {
      String id = symToId.get(sym);
      if (id != null) idToSym.put(id, sym);
    }

    Map<String, PricePoint> out = new LinkedHashMap<>();

    for (var entry : resp.entrySet()) {
      String id = entry.getKey();
      Object rowObj = entry.getValue();
      if (!(rowObj instanceof Map<?,?> row)) continue;

      Object priceObj = row.get(vs);
      if (!(priceObj instanceof Number priceNum)) continue;

      // claves que CoinGecko arma: "<vs>_24h_change" y "<vs>_market_cap"
      Object changeObj = row.get(vs + "_24h_change");
      Object mcapObj   = row.get(vs + "_market_cap");

      BigDecimal price = BigDecimal.valueOf(priceNum.doubleValue());
      BigDecimal change24h = (changeObj instanceof Number cn) ? BigDecimal.valueOf(cn.doubleValue()) : null;
      BigDecimal marketCap = (mcapObj instanceof Number mn) ? BigDecimal.valueOf(mn.doubleValue()) : null;

      String sym = idToSym.get(id);
      if (sym != null) {
        out.put(sym, new PricePoint(price, change24h, marketCap));
      }
    }

    return out;
  }
}
