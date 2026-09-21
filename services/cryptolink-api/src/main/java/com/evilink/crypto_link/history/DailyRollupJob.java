package com.evilink.crypto_link.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class DailyRollupJob {

  private static final Logger log = LoggerFactory.getLogger(DailyRollupJob.class);
  private static final String FIAT = "USD";

  private final HistoricalPricesRepository repo;

  public DailyRollupJob(HistoricalPricesRepository repo) {
    this.repo = repo;
  }

  /** Cada día a las 00:15 UTC: agrega el DÍA ANTERIOR (ya completo) a price_daily.
   *  Se corre después de medianoche para que el día a resumir esté cerrado. */
  @Scheduled(cron = "0 15 0 * * *", zone = "UTC")
  public void rollupYesterday() {
    LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    runFor(yesterday);
  }

  /** Reutilizable: agrega un día concreto. Idempotente (ON CONFLICT). */
  public void runFor(LocalDate day) {
    try {
      List<HistoricalPricesRepository.DailyRollup> rows = repo.aggregateDay(FIAT, day);
      if (rows.isEmpty()) {
        log.warn("[daily-rollup] no data to roll up for {}", day);
        return;
      }
      repo.saveDaily(FIAT, day, rows);
      log.info("[daily-rollup] rolled up {} symbols for {}", rows.size(), day);
    } catch (Exception e) {
      log.error("[daily-rollup] failed for {}: {}", day, e.getMessage(), e);
    }
  }
}