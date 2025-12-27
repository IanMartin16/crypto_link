package com.evilink.crypto_link.validation;

import com.evilink.crypto_link.service.SymbolService;
import com.evilink.crypto_link.service.FiatService;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class MarketValidator {

  private final SymbolService symbols;
  private final FiatService fiats;

  // defaults “por si falla el refresh” o mientras levanta la app
  private volatile Set<String> cachedSymbols = Set.of("BTC", "ETH");
  private volatile Set<String> cachedFiats   = Set.of("USD", "MXN", "EUR");
  private volatile long cachedAtMs = 0;

  private final long ttlMs = 30_000; // 30s cache

  public MarketValidator(SymbolService symbols, FiatService fiats) {
    this.symbols = symbols;
    this.fiats = fiats;
  }

  private void refreshIfNeeded() {
    long now = System.currentTimeMillis();
    if (now - cachedAtMs < ttlMs) return;

    synchronized (this) {
      // double-check dentro del lock
      now = System.currentTimeMillis();
      if (now - cachedAtMs < ttlMs) return;

      Set<String> s = symbols.listActive().stream()
          .filter(Objects::nonNull)
          .map(String::trim)
          .filter(x -> !x.isBlank())
          .map(String::toUpperCase)
          .collect(Collectors.toSet());

      Set<String> f = fiats.listActiveSet().stream()
          .filter(Objects::nonNull)
          .map(String::trim)
          .filter(x -> !x.isBlank())
          .map(String::toUpperCase)
          .collect(Collectors.toSet());

      if (!s.isEmpty()) cachedSymbols = s;
      if (!f.isEmpty()) cachedFiats = f;

      cachedAtMs = now;
    }
  }

  public List<String> allowedSymbols() {
    refreshIfNeeded();
    return cachedSymbols.stream().sorted().toList();
  }

  public List<String> allowedFiats() {
    refreshIfNeeded();
    return cachedFiats.stream().sorted().toList();
  }

  /** Para endpoints tipo stream: "BTC,ETH,SOL" -> List<String> validada */
  public List<String> normalizeSymbolsCsv(String csv) {
    if (csv == null || csv.isBlank()) return List.of("BTC", "ETH");

    List<String> input = Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList();

    return normalizeSymbols(input);
  }

  /** Para endpoint /v1/prices: "BTC" -> "BTC" */
  public String normalizeSymbol(String symbol) {
    List<String> out = normalizeSymbols(List.of(symbol));
    return out.get(0);
  }

  /** Valida lista de símbolos contra lo que hay en DB/cache */
  public List<String> normalizeSymbols(List<String> input) {
    refreshIfNeeded();

    if (input == null || input.isEmpty()) return List.of("BTC", "ETH");

    // normaliza + quita duplicados conservando orden
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String raw : input) {
      if (raw == null) continue;
      String s = raw.trim().toUpperCase();
      if (!s.isBlank()) normalized.add(s);
    }

    if (normalized.isEmpty()) return List.of("BTC", "ETH");

    // valida
    List<String> invalid = normalized.stream()
        .filter(s -> !cachedSymbols.contains(s))
        .toList();

    if (!invalid.isEmpty()) {
      throw new IllegalArgumentException(
          "Unsupported symbols: " + invalid + ". Allowed: " + allowedSymbols()
      );
    }

    return new ArrayList<>(normalized);
  }

  public String normalizeFiat(String fiat) {
    refreshIfNeeded();

    String f = (fiat == null || fiat.isBlank()) ? "USD" : fiat.trim().toUpperCase();
    if (!cachedFiats.contains(f)) {
      throw new IllegalArgumentException(
          "Unsupported fiat: " + f + ". Allowed: " + allowedFiats()
      );
    }
    return f;
  }
}

