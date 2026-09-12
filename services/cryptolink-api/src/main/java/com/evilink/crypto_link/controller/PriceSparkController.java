package com.evilink.crypto_link.controller;

import com.evilink.crypto_link.history.PriceHistoryRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/v1")
public class PriceSparkController {

    private final PriceHistoryRepository priceHistoryRepo;

    // cuántos puntos del sparkline (antes el buffer daba máx 24)
    private static final int SPARK_POINTS = 24;

    public PriceSparkController(PriceHistoryRepository priceHistoryRepo) {
        this.priceHistoryRepo = priceHistoryRepo;
    }

    @GetMapping("/prices/spark")
    public ResponseEntity<?> getPriceSpark(
        @RequestParam String symbols,
        @RequestParam(defaultValue = "USD") String fiat
    ) {
        List<String> list = Arrays.stream(symbols.split(","))
            .map(String::trim).filter(s -> !s.isBlank())
            .map(String::toUpperCase).toList();

        String vs = fiat.toUpperCase();
        Map<String, Object> out = new LinkedHashMap<>();

        for (String sym : list) {
            // findSeries devuelve DESC (más reciente primero); el sparkline los
            // quiere en orden cronológico (viejo->nuevo), así que invertimos.
            var rows = priceHistoryRepo.findSeries(vs, sym, SPARK_POINTS);
            List<Map<String, Object>> points = new ArrayList<>(rows.size());
            for (int i = rows.size() - 1; i >= 0; i--) {   // invertir a ASC
                var r = rows.get(i);
                Map<String, Object> pt = new LinkedHashMap<>();
                pt.put("t", r.capturedAt() == null ? null : r.capturedAt().toString());
                pt.put("v", r.price());
                points.add(pt);
            }
            out.put(sym, points);
        }

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(10)).cachePublic())
            .body(Map.of(
                "ok", true,
                "fiat", vs,
                "ts", OffsetDateTime.now().toString(),
                "source", "price-history-db",   // antes "internal-history"
                "series", out
            ));
    }
}