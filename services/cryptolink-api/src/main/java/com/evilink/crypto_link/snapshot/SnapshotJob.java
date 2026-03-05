package com.evilink.crypto_link.snapshot;

import com.evilink.crypto_link.service.PriceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SnapshotJob {

  private final SnapshotCache snapshotCache;
  private final PriceService priceService;

  public SnapshotJob(SnapshotCache snapshotCache, PriceService priceService) {
    this.snapshotCache = snapshotCache;
    this.priceService = priceService;
  }

  // ✅ 10s (coherente con tu intención “snapshot cada 10s”)
  @Scheduled(fixedRate = 10_000)
  public void refresh() {
    // Nota: tu PriceService ya tiene TTL interno (3s) y fallback stale-cache
    PriceService.Result r = priceService.getPrices(List.of("BTC", "ETH"), "USD");

    // MVP: mood neutral (luego lo derivamos de trends)
    Map<String, Object> snapshot = Map.of(
        "asOf", r.ts,
        "provider", "coingecko",
        "fiat", r.fiat,
        "source", r.source,
        "marketMood", "neutral",
        "prices", r.prices // Map<String, BigDecimal>
    );

    snapshotCache.set(snapshot);
  }
}