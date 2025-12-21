package com.evilink.crypto_link.service;

import com.evilink.crypto_link.sse.PriceBroadcaster;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

@Component
public class MockPricePoller {

    private final PriceBroadcaster broadcaster;
    private final PriceService priceService;

    public MockPricePoller(PriceBroadcaster broadcaster, PriceService priceService) {
        this.broadcaster = broadcaster;
        this.priceService = priceService;
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        Map<String, Set<String>> req = broadcaster.snapshotRequested();
        if (req.isEmpty()) return;

        for (var e : req.entrySet()) {
            String fiat = e.getKey();
            var symbols = new ArrayList<>(e.getValue());
            if (symbols.isEmpty()) continue;

            var r = priceService.getPrices(symbols, fiat);

            broadcaster.broadcastPrices(
                    r.fiat,
                    Map.of("ts", r.ts, "fiat", r.fiat, "source", r.source),
                    r.prices
            );
        }
    }
}
