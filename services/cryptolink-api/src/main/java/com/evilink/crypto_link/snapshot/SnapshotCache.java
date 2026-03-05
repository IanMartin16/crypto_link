package com.evilink.crypto_link.snapshot;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SnapshotCache {
  private final AtomicReference<Map<String, Object>> ref = new AtomicReference<>(null);

  public Map<String, Object> get() { return ref.get(); }
  public void set(Map<String, Object> snapshot) { ref.set(snapshot); }
}