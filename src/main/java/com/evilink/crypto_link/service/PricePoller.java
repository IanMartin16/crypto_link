package com.evilink.crypto_link.service;

import com.evilink.crypto_link.sse.PriceBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;

@Component
public class PricePoller {

  private static final Logger log = LoggerFactory.getLogger(PricePoller.class);

  private final PriceBroadcaster broadcaster;
  private final PriceService priceService;

  // CoinGecko: mejor no mandar listas enormes en una sola llamada
  private final int batchSize = 25;

  public PricePoller(PriceBroadcaster broadcaster, PriceService priceService) {
    this.broadcaster = broadcaster;
    this.priceService = priceService;
  }

  // fixedDelay evita empalmes si una llamada tarda
  @Scheduled(fixedDelayString = "${cryptolink.poller.delay-ms:1500}")
  public void tick() {

    // opcional: si no hay nadie conectado, ni te muevas
    if (broadcaster.activeConnections() == 0) return;

    Map<String, Set<String>> req = broadcaster.snapshotRequested();
    if (req.isEmpty()) return;

    for (var e : req.entrySet()) {
      String fiat = e.getKey();
      List<String> symbols = new ArrayList<>(e.getValue());
      if (symbols.isEmpty()) continue;

      // Acumula precios de todos los batches
      Map<String, Object> allPrices = new LinkedHashMap<>();
      String source = "unknown";

      try {
        for (int i = 0; i < symbols.size(); i += batchSize) {
          List<String> chunk = symbols.subList(i, Math.min(i + batchSize, symbols.size()));

          var r = priceService.getPrices(chunk, fiat);

          // r.prices es Map<String, BigDecimal> (o similar), lo metemos a un Map genérico
          for (var p : r.prices.entrySet()) {
            allPrices.put(p.getKey(), p.getValue());
          }

          // si viene mezclado cache/coingecko, no pasa nada, solo reporta el último
          source = r.source;
        }

        // payload único por fiat
        Map<String, Object> payload = Map.of(
          "ts", OffsetDateTime.now().toString(),
          "fiat", fiat.toUpperCase(),
          "source", source
        );

        broadcaster.broadcastPrices(fiat, payload, allPrices);

      } catch (Exception ex) {
        // IMPORTANTÍSIMO: que NO mate todo el tick
        log.warn("Poller failed for fiat={} symbols={} msg={}", fiat, symbols, ex.getMessage());
      }
    }
  }
}
