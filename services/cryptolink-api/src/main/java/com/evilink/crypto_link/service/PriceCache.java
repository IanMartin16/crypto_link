package com.evilink.crypto_link.service;

import com.evilink.crypto_link.service.CoinGeckoPriceProvider.PricePoint;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CAMBIO: el caché ahora guarda PricePoint (precio + 24h + marketCap) en vez de
 * solo BigDecimal (precio). Así una sola llamada rica cada TTL trae y cachea todo.
 * Los consumidores de solo-precio extraen .price; las cards leen 24h/marketCap.
 */
@Component
public class PriceCache {

    public static class Entry {
        public final Map<String, PricePoint> points;   // antes: Map<String,BigDecimal> prices
        public final long fetchedAtEpochMs;
        public final long expiresAtEpochMs;

        public Entry(Map<String, PricePoint> points, long fetchedAtEpochMs, long expiresAtEpochMs) {
            this.points = points;
            this.fetchedAtEpochMs = fetchedAtEpochMs;
            this.expiresAtEpochMs = expiresAtEpochMs;
        }

        public boolean isFresh(long nowMs) {
            return nowMs <= expiresAtEpochMs;
        }
    }

    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    public Entry get(String key) {
        return cache.get(key);
    }

    public void put(String key, Map<String, PricePoint> points, long ttlMs) {
        long now = Instant.now().toEpochMilli();
        cache.put(key, new Entry(points, now, now + ttlMs));
    }

    public static String key(String fiat, String symbolsCsv) {
        return fiat.toUpperCase() + "|" + symbolsCsv.toUpperCase().replace(" ", "");
    }
}
