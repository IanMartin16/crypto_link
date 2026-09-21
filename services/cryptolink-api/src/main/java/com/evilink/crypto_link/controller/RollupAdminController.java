package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.history.DailyRollupJob;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/v1/admin")   // ruta admin, protegida por la API key
public class RollupAdminController {

  private final DailyRollupJob rollupJob;

  public RollupAdminController(DailyRollupJob rollupJob) {
    this.rollupJob = rollupJob;
  }

  // POST /v1/admin/backfill?from=2026-08-26&to=2026-09-20
  @PostMapping("/backfill")
  public String backfill(@RequestParam String from, @RequestParam(required = false) String to) {
    LocalDate start = LocalDate.parse(from);
    LocalDate end = (to != null) ? LocalDate.parse(to) : LocalDate.now(ZoneOffset.UTC).minusDays(1);
    int days = 0;
    for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
      rollupJob.runFor(d);
      days++;
    }
    return "Backfilled " + days + " days (" + start + " to " + end + ")";
  }
}