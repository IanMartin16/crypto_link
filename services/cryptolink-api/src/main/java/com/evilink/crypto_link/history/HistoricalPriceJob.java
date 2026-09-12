package com.evilink.crypto_link.history;

import com.evilink.crypto_link.service.CoinGeckoPriceProvider;
import com.evilink.crypto_link.service.SymbolService;
import com.evilink.crypto_link.history.PriceHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class HistoricalPriceJob {

  private static final Logger log = LoggerFactory.getLogger(HistoricalPriceJob.class);
  private static final int MAX_IDS_PER_CALL = 500;   // margen bajo el tier de 515
  private static final int RETENTION_DAYS   = 90;    // 3 meses
  private static final int PRICE_HISTORY_RETENTION_DAYS = 30;
  private static final String FIAT = "USD";

  private final CoinGeckoPriceProvider provider;
  private final SymbolService symbolService;
  private final HistoricalPricesRepository repo;
  private final PriceHistoryRepository priceHistoryRepo;

  public HistoricalPriceJob(CoinGeckoPriceProvider provider,
                            SymbolService symbolService,
                            HistoricalPricesRepository repo,
                            PriceHistoryRepository priceHistoryRepo) {
    this.provider = provider;
    this.symbolService = symbolService;
    this.repo = repo;
    this.priceHistoryRepo = priceHistoryRepo;
  }

  /** Cada hora. fixedRate en ms. initialDelay evita correr justo al arrancar. */
  @Scheduled(fixedRate = 3_600_000, initialDelay = 60_000)
  public void run() {
    try {
      // lista canónica DINÁMICA: todos los símbolos activos (crece solo al agregar)
      List<String> symbols = new ArrayList<>(
          symbolService.listActiveSymbolToCoingeckoId().keySet());

      if (symbols.isEmpty()) {
        log.warn("[historical-job] no active symbols, skipping");
        return;
      }

      // SALVAGUARDA del tier: si algún día superamos MAX_IDS_PER_CALL, avisar y
      // recortar (paginación completa se agrega cuando de verdad se acerque a 500).
      if (symbols.size() > MAX_IDS_PER_CALL) {
        log.warn("[historical-job] {} symbols exceeds MAX_IDS_PER_CALL={} — truncating; add pagination soon",
            symbols.size(), MAX_IDS_PER_CALL);
        symbols = symbols.subList(0, MAX_IDS_PER_CALL);
      }

      Map<String, CoinGeckoPriceProvider.HistoryPoint> data =
          provider.getPricesForHistory(symbols, FIAT);

      if (data.isEmpty()) {
        log.warn("[historical-job] fetch returned empty, nothing to persist");
        return;
      }

      OffsetDateTime capturedAt = OffsetDateTime.now();
      List<HistoricalPricesRepository.Row> rows = new ArrayList<>(data.size());
      data.forEach((sym, p) -> rows.add(new HistoricalPricesRepository.Row(
          FIAT, sym.toUpperCase(), p.price(), p.marketCap(), p.volume24h(),
          p.change24h(), p.sourceUpdatedAt(), capturedAt
      )));

      repo.saveAll(rows);
      log.info("[historical-job] persisted {} symbols @ {}", rows.size(), capturedAt);

    } catch (Exception e) {
      // best-effort: un fallo del job NO debe tumbar la app (el precio en vivo sigue)
      log.error("[historical-job] failed: {}", e.getMessage(), e);
    }
  }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void cleanup() {
        try {
            int h = repo.deleteOlderThanDays(RETENTION_DAYS);                    // historical_prices 90d
            int p = priceHistoryRepo.deleteOlderThanDays(PRICE_HISTORY_RETENTION_DAYS); // price_history 30d
            log.info("[retention] historical_prices deleted {} (>{}d), price_history deleted {} (>{}d)",
                h, RETENTION_DAYS, p, PRICE_HISTORY_RETENTION_DAYS);
        } catch (Exception e) {
           log.error("[retention] cleanup failed: {}", e.getMessage(), e);
        }
    }
}