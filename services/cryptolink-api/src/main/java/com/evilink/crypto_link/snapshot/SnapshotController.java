package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.snapshot.SnapshotCache;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class SnapshotController {

  private final SnapshotCache snapshotCache;

  public SnapshotController(SnapshotCache snapshotCache) {
    this.snapshotCache = snapshotCache;
  }

  @GetMapping("/snapshot")
  public ResponseEntity<?> snapshot() {
    Map<String, Object> snap = snapshotCache.get();
    if (snap == null) {
      return ResponseEntity.status(503).body(Map.of("ok", false, "error", "snapshot_not_ready"));
    }

    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofSeconds(10)).cachePublic())
        .body(Map.of("ok", true, "snapshot", snap));
  }
}