package com.evilink.crypto_link.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PriceCache {

    public static class Entry {
        public final Map<String, BigDecimal> prices;
        public final long fetchedAtEpochMs;
        public final long expiresAtEpochMs;

        public Entry(Map<String, BigDecimal> prices, long fetchedAtEpochMs, long expiresAtEpochMs) {
            this.prices = prices;
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

    public void put(String key, Map<String, BigDecimal> prices, long ttlMs) {
        long now = Instant.now().toEpochMilli();
        cache.put(key, new Entry(prices, now, now + ttlMs));
    }

    public static String key(String fiat, String symbolsCsv) {
        return fiat.toUpperCase() + "|" + symbolsCsv.toUpperCase().replace(" ", "");
    }
}
