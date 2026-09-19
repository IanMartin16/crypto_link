package com.evilink.crypto_link.snapshot;

import com.evilink.crypto_link.breadth.BreadthService;
import com.evilink.crypto_link.service.PriceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class SnapshotJob {

  private final SnapshotCache snapshotCache;
  private final PriceService priceService;
  private final BreadthService breadthService;   // ← NUEVO

  public SnapshotJob(SnapshotCache snapshotCache,
                     PriceService priceService,
                     BreadthService breadthService) {   // ← NUEVO
    this.snapshotCache = snapshotCache;
    this.priceService = priceService;
    this.breadthService = breadthService;
  }

  @Scheduled(fixedRate = 50_000)
  public void refresh() {
    PriceService.Result r = priceService.getPrices(List.of("BTC", "ETH"), "USD");

    // mood derivado de BREADTH (amplitud = mood real, no placeholder)
    String mood = deriveMood();

    Map<String, Object> snapshot = Map.of(
        "asOf", r.ts,
        "provider", "coingecko",
        "fiat", r.fiat,
        "source", r.source,
        "marketMood", mood,          // ← ya NO hardcodeado
        "prices", r.prices
    );

    snapshotCache.set(snapshot);
  }

  /** mood del mercado derivado del breadth (% sobre MA). Honesto: la amplitud
   *  ES el sentimiento agregado. Best-effort: si breadth falla, neutral. */
  private String deriveMood() {
    try {
        var b = breadthService.getBreadth("USD");
        if (b.movers() < 5) return "neutral";
        double pct = b.pctAbove().doubleValue();
        if (pct >= 60) return "bullish";
        if (pct <= 40) return "bearish";
        return "neutral";
    } catch (Exception e) {
        return "neutral";
    }
  }
}