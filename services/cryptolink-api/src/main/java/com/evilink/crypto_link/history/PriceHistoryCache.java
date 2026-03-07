package com.evilink.crypto_link.history;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PriceHistoryCache {

    public static class Point {
        public String t;
        public BigDecimal v;

        public Point(String t, BigDecimal v) {
            this.t = t;
            this.v = v;
        }
    }

    private final Map<String, Deque<Point>> series = new ConcurrentHashMap<>();
    private static final int MAX_POINTS = 24;

    public void add(String fiat, String symbol, BigDecimal value) {
        if (fiat == null || symbol == null || value == null) return;

        String key = fiat.toUpperCase() + ":" + symbol.toUpperCase();
        Deque<Point> q = series.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (q) {
            q.addLast(new Point(Instant.now().toString(), value));
            while (q.size() > MAX_POINTS) {
                q.removeFirst();
            }
        }
    }

    public List<Point> get(String fiat, String symbol) {
        String key = fiat.toUpperCase() + ":" + symbol.toUpperCase();
        Deque<Point> q = series.get(key);
        if (q == null) return List.of();

        synchronized (q) {
            return new ArrayList<>(q);
        }
    }
}