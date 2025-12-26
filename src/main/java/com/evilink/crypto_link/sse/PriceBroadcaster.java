package com.evilink.crypto_link.sse;

import com.evilink.crypto_link.security.ApiKeyStore;
import org.springframework.stereotype.Component;
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
        int limit = plan.sseConnections;

        byKey.putIfAbsent(apiKey, new CopyOnWriteArrayList<>());
        CopyOnWriteArrayList<Subscription> list = byKey.get(apiKey);

        if (list.size() >= limit) {
            throw new TooManyConnectionsException(limit);
        }

        SseEmitter emitter = new SseEmitter(0L);

        Set<String> symSet = new LinkedHashSet<>();
        for (String s : symbols) {
            if (s != null && !s.isBlank()) symSet.add(s.trim().toUpperCase());
        }

        Subscription sub = new Subscription(emitter, symSet, fiat.toUpperCase());
        list.add(sub);

        Runnable cleanup = () -> {
            list.remove(sub);
            if (list.isEmpty()) byKey.remove(apiKey);
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

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

    /** Para el poller: qué es lo que piden los clientes ahorita (para optimizar llamadas) */
    public Map<String, Set<String>> snapshotRequested() {
        Map<String, Set<String>> out = new HashMap<>();
        for (var entry : byKey.entrySet()) {
            for (Subscription sub : entry.getValue()) {
                out.computeIfAbsent(sub.fiat, k -> new LinkedHashSet<>()).addAll(sub.symbols);
            }
        }
        return out;
    }

    /** Broadcast ya con (fiat + precios) y aquí filtramos por cliente */
    public void broadcastPrices(String fiat, Map<String, Object> payload, Map<String, ?> pricesBySymbol) {
        String F = fiat.toUpperCase();

        for (var entry : byKey.entrySet()) {
            for (Subscription sub : entry.getValue()) {
                if (!sub.fiat.equalsIgnoreCase(F)) continue;

                // filtra solo símbolos que pidió ese cliente
                Map<String, Object> filtered = new LinkedHashMap<>();
                for (String s : sub.symbols) {
                    Object v = ((Map<?, ?>) pricesBySymbol).get(s);
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
                    entry.getValue().remove(sub);
                }
            }
        }
    }

    public void broadcastPing() {
        for (var entry : byKey.entrySet()) {
            for (Subscription sub : entry.getValue()) {
                try {
                    sub.emitter.send(SseEmitter.event().name("ping").data("ok"));
                } catch (IOException e) {
                    entry.getValue().remove(sub);
                }
            }
        }
    }

    public static class TooManyConnectionsException extends Exception {
        public final int max;
        public TooManyConnectionsException(int max) { this.max = max; }
    }

    public int activeConnections() {
    return byKey.values().stream().mapToInt(java.util.List::size).sum();
    }

}
