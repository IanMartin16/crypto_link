package com.evilink.crypto_link.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {

    private static class Window {
        long minute; // epochMinute
        int count;
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean allow(String apiKey, int limitPerMinute) {
        long nowMinute = Instant.now().getEpochSecond() / 60;

        Window w = windows.computeIfAbsent(apiKey, k -> new Window());

        synchronized (w) {
            if (w.minute != nowMinute) {
                w.minute = nowMinute;
                w.count = 0;
            }
            w.count++;
            return w.count <= limitPerMinute;
        }
    }
}
