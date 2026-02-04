package com.evilink.crypto_link.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class RateLimiter {

  private static class Bucket {
    final long window;     // epochMinute
    final LongAdder count = new LongAdder();
    Bucket(long window) { this.window = window; }
  }

  public record Decision(boolean allowed, int used, int limit, long resetEpochSec) {
    public int remaining() { return Math.max(0, limit - used); }
  }

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  public Decision check(String apiKey, int limitPerMinute) {
    long nowSec = System.currentTimeMillis() / 1000;
    long epochMinute = nowSec / 60;
    long reset = (epochMinute + 1) * 60; // siguiente minuto

    String key = apiKey + ":" + epochMinute;
    Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(epochMinute));

    b.count.increment();
    int used = (int) b.count.sum();

    boolean allowed = used <= limitPerMinute;
    return new Decision(allowed, used, limitPerMinute, reset);
  }

  @Scheduled(fixedRateString = "${cryptolink.ratelimit.cleanup-ms:60000}")
  public void cleanup() {
    long epochMinute = (System.currentTimeMillis() / 1000) / 60;
    long keepFrom = epochMinute - 3;
    buckets.keySet().removeIf(k -> {
      int idx = k.lastIndexOf(':');
      if (idx < 0) return true;
      long w = Long.parseLong(k.substring(idx + 1));
      return w < keepFrom;
    });
  }
}
