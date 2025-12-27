package com.evilink.crypto_link.sse;

import com.evilink.crypto_link.security.ApiKeyStore;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class PriceBroadcaster {

  private final ApiKeyStore store;

  public PriceBroadcaster(ApiKeyStore store) {
    this.store = store;
  }

  public static class Subscription {
    public final SseEmitter emitter;
    public final Set<String> symbols;
    public final String fiat;

    public Subscription(SseEmitter emitter, Set<String> symbols, String fiat) {
      this.emitter = emitter;
      this.symbols = symbols;
      this.fiat = fiat;
    }
  }

  // apiKey -> subs
  private final ConcurrentHashMap<String, CopyOnWriteArrayList<Subscription>> byKey = new ConcurrentHashMap<>();

  public SseEmitter subscribe(String apiKey, List<String> symbols, String fiat) throws TooManyConnectionsException {

    ApiKeyStore.Plan plan = store.getPlan(apiKey);
    if (plan == null) {
      // aunque tu ApiKeyFilter ya debería impedir esto, mejor blindado
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }

    int limit = plan.sseConnections;

    byKey.putIfAbsent(apiKey, new CopyOnWriteArrayList<>());
    CopyOnWriteArrayList<Subscription> list = byKey.get(apiKey);

    // Normaliza símbolos
    Set<String> symSet = new LinkedHashSet<>();
    for (String s : symbols) {
      if (s != null && !s.isBlank()) symSet.add(s.trim().toUpperCase());
    }
    if (symSet.isEmpty()) symSet = new LinkedHashSet<>(List.of("BTC","ETH"));

    String fiatUpper = (fiat == null || fiat.isBlank()) ? "USD" : fiat.trim().toUpperCase();

    SseEmitter emitter = new SseEmitter(0L);
    Subscription sub = new Subscription(emitter, symSet, fiatUpper);

    // ✅ Check+Add atómico (evita race conditions si abren 2 tabs a la vez)
    synchronized (list) {
      if (list.size() >= limit) {
        throw new TooManyConnectionsException(limit);
      }
      list.add(sub);
    }

    Runnable cleanup = () -> removeSub(apiKey, sub);

    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(e -> cleanup.run());

    // hello event
    try {
      emitter.send(SseEmitter.event()
        .name("hello")
        .data(Map.of(
          "ok", true,
          "plan", plan.name(),
          "fiat", sub.fiat,
          "symbols", sub.symbols,
          "ts", OffsetDateTime.now().toString()
        )));
    } catch (IOException ignored) {
      cleanup.run();
    }

    return emitter;
  }

  private void removeSub(String apiKey, Subscription sub) {
    CopyOnWriteArrayList<Subscription> list = byKey.get(apiKey);
    if (list == null) return;

    list.remove(sub);

    if (list.isEmpty()) {
      byKey.remove(apiKey);
    }
  }

  /** Para el poller: qué piden los clientes ahorita (para optimizar llamadas) */
  public Map<String, Set<String>> snapshotRequested() {
    Map<String, Set<String>> out = new HashMap<>();

    for (var entry : byKey.entrySet()) {
      for (Subscription sub : entry.getValue()) {
        if (sub == null) continue;
        out.computeIfAbsent(sub.fiat, k -> new LinkedHashSet<>()).addAll(sub.symbols);
      }
    }
    return out;
  }

  /** Broadcast ya con (fiat + precios) y aquí filtramos por cliente */
  public void broadcastPrices(String fiat, Map<String, Object> payload, Map<String, ?> pricesBySymbol) {
    String F = fiat.toUpperCase();

    for (var entry : byKey.entrySet()) {
      String apiKey = entry.getKey();
      CopyOnWriteArrayList<Subscription> subs = entry.getValue();

      for (Subscription sub : subs) {
        if (!sub.fiat.equalsIgnoreCase(F)) continue;

        // filtra solo símbolos que pidió ese cliente
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (String s : sub.symbols) {
          Object v = pricesBySymbol.get(s);
          if (v != null) filtered.put(s, v);
        }

        try {
          sub.emitter.send(SseEmitter.event().name("price").data(Map.of(
            "ts", payload.get("ts"),
            "fiat", payload.get("fiat"),
            "source", payload.get("source"),
            "prices", filtered
          )));
        } catch (IOException e) {
          // ✅ cleanup completo
          removeSub(apiKey, sub);
        }
      }
    }
  }

  /** Keepalive automático (Railway/edge proxies lo agradecen) */
  @Scheduled(fixedRateString = "${cryptolink.sse.keepalive-ms:25000}")
  public void keepAlive() {
    broadcastPing();
  }

  public void broadcastPing() {
    for (var entry : byKey.entrySet()) {
      String apiKey = entry.getKey();
      CopyOnWriteArrayList<Subscription> subs = entry.getValue();

      for (Subscription sub : subs) {
        try {
          sub.emitter.send(SseEmitter.event().name("ping").data(Map.of(
            "ok", true,
            "ts", OffsetDateTime.now().toString()
          )));
        } catch (IOException e) {
          removeSub(apiKey, sub);
        }
      }
    }
  }

  public static class TooManyConnectionsException extends Exception {
    public final int max;
    public TooManyConnectionsException(int max) { this.max = max; }
  }

  public int activeConnections() {
    return byKey.values().stream().mapToInt(List::size).sum();
  }
}
